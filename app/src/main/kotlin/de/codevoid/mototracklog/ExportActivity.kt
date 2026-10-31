package de.codevoid.mototracklog

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import de.codevoid.mototracklog.data.ExportFilterStore
import de.codevoid.mototracklog.data.FilterSettings
import de.codevoid.mototracklog.data.NavBarStore
import de.codevoid.mototracklog.data.OrientationStore
import de.codevoid.mototracklog.data.RunInfo
import de.codevoid.mototracklog.data.RunReader
import de.codevoid.mototracklog.data.RunRepository
import de.codevoid.mototracklog.databinding.ActivityExportBinding
import de.codevoid.mototracklog.export.GpxExporter
import de.codevoid.mototracklog.export.PointFilter
import de.codevoid.mototracklog.ui.EscapeKeys
import de.codevoid.mototracklog.ui.Formats
import de.codevoid.mototracklog.ui.bind
import de.codevoid.mototracklog.ui.enableImmersiveMode
import de.codevoid.mototracklog.ui.fitContentToSystemBars
import de.codevoid.mototracklog.ui.noTransition
import de.codevoid.mototracklog.ui.reductionPercent
import de.codevoid.mototracklog.ui.shareFile
import de.codevoid.mototracklog.ui.showOptionsDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import java.util.Locale

/**
 * One run as GPX: three filter tiles that open preset pickers, the number of points the filters
 * keep, and Share GPX. Recording always keeps every raw fix; the filters act only here.
 */
class ExportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExportBinding
    private val viewModel: ExportViewModel by viewModels()
    private val orientationStore by lazy { OrientationStore(this) }
    private val navBarStore by lazy { NavBarStore(this) }
    private val formats by lazy { Formats(Locale.getDefault(), ZoneId.systemDefault()) }
    private val escapeKeys = EscapeKeys(onLongPress = ::finish, onShortPress = ::finish)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = orientationStore.orientation
        binding = ActivityExportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.enableImmersiveMode(navBarStore.showNavBar)
        window.fitContentToSystemBars()

        viewModel.open(intent.getLongExtra(EXTRA_RUN_ID, NO_RUN))
        binding.header.backButton.setOnClickListener { finish() }
        binding.accuracyFilter.root.setOnClickListener { pickAccuracy() }
        binding.distanceFilter.root.setOnClickListener { pickDistance() }
        binding.timeFilter.root.setOnClickListener { pickTime() }
        binding.shareButton.setOnClickListener { share() }
        renderFilters()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.run.collect { run ->
                        binding.header.headerTitle.text = run?.let { formats.runTitle(it.startTimeMillis) }
                    }
                }
                launch { viewModel.preview.collect { renderPreview(it) } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requestedOrientation = orientationStore.orientation
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.enableImmersiveMode(navBarStore.showNavBar)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        escapeKeys.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        escapeKeys.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)

    override fun finish() {
        super.finish()
        noTransition()
    }

    private fun renderFilters() {
        val filters = viewModel.filters
        binding.accuracyFilter.bind(
            getString(R.string.filter_accuracy),
            getString(R.string.filter_accuracy_value, filters.accuracyMeters),
        )
        binding.distanceFilter.bind(getString(R.string.filter_distance), distanceLabel(filters.distanceMeters.toInt()))
        val time = timeLabel(filters.timeSeconds)
        // Distance beats time in PointFilter: while a distance is set, the interval has no effect.
        binding.timeFilter.bind(
            getString(R.string.filter_time),
            if (filters.distanceMeters > 0f && filters.timeSeconds > 0) {
                time + getString(R.string.list_separator) + getString(R.string.filter_time_ignored)
            } else {
                time
            },
        )
    }

    private fun renderPreview(preview: ExportPreview) {
        when (preview) {
            // The last result stays, dimmed, rather than flashing a placeholder on every change.
            ExportPreview.Computing -> {
                binding.resultValue.alpha = STALE_ALPHA
                binding.resultDetail.alpha = STALE_ALPHA
                if (binding.resultValue.text.isNullOrEmpty()) binding.resultDetail.setText(R.string.result_computing)
            }
            is ExportPreview.Ready -> {
                binding.resultValue.alpha = 1f
                binding.resultDetail.alpha = 1f
                binding.resultValue.text = formats.count(preview.keptPoints)
                binding.resultDetail.text = getString(
                    R.string.result_detail,
                    formats.count(preview.totalPoints),
                    reductionPercent(preview.totalPoints, preview.keptPoints),
                )
            }
        }
    }

    private fun pickAccuracy() {
        val current = viewModel.filters
        showOptionsDialog(
            context = this,
            title = getString(R.string.filter_accuracy),
            options = ACCURACY_PRESETS.map { getString(R.string.filter_accuracy_value, it) },
            selectedIndex = ACCURACY_PRESETS.indexOf(current.accuracyMeters),
        ) { index -> applyFilters(current.copy(accuracyMeters = ACCURACY_PRESETS[index])) }
    }

    private fun pickDistance() {
        val current = viewModel.filters
        showOptionsDialog(
            context = this,
            title = getString(R.string.filter_distance),
            options = DISTANCE_PRESETS.map(::distanceLabel),
            selectedIndex = DISTANCE_PRESETS.indexOfFirst { it.toFloat() == current.distanceMeters },
        ) { index -> applyFilters(current.copy(distanceMeters = DISTANCE_PRESETS[index].toFloat())) }
    }

    private fun pickTime() {
        val current = viewModel.filters
        showOptionsDialog(
            context = this,
            title = getString(R.string.filter_time),
            options = TIME_PRESETS.map(::timeLabel),
            selectedIndex = TIME_PRESETS.indexOf(current.timeSeconds),
        ) { index -> applyFilters(current.copy(timeSeconds = TIME_PRESETS[index])) }
    }

    private fun applyFilters(filters: FilterSettings) {
        viewModel.setFilters(filters)
        renderFilters()
    }

    private fun distanceLabel(meters: Int): String =
        if (meters == 0) getString(R.string.setting_off) else getString(R.string.filter_distance_value, meters)

    private fun timeLabel(seconds: Int): String =
        if (seconds == 0) getString(R.string.setting_off) else getString(R.string.filter_time_value, seconds)

    // Named after the run's start, so shares from different runs do not collide.
    private fun share() {
        val run = viewModel.run.value ?: return
        val file = File(File(cacheDir, EXPORT_DIR), "motoTracklog-${formats.fileStamp(run.startTimeMillis)}.gpx")
        viewModel.export(file) { shareFile(it, MIME_GPX, getString(R.string.share_gpx_title)) }
    }

    companion object {
        private const val EXTRA_RUN_ID = "run_id"
        private const val NO_RUN = -1L
        private const val EXPORT_DIR = "exports"
        private const val MIME_GPX = "application/gpx+xml"
        private const val STALE_ALPHA = 0.35f

        private val ACCURACY_PRESETS = listOf(5, 10, 20, 50, 100)
        private val DISTANCE_PRESETS = listOf(0, 1, 2, 5, 10, 25, 50)
        private val TIME_PRESETS = listOf(0, 1, 2, 5, 10, 30, 60)

        fun intent(context: Context, runId: Long): Intent =
            Intent(context, ExportActivity::class.java).putExtra(EXTRA_RUN_ID, runId)
    }
}

/** How many of a run's points the current filters keep. */
sealed interface ExportPreview {
    data object Computing : ExportPreview
    data class Ready(val totalPoints: Long, val keptPoints: Long) : ExportPreview
}

/**
 * The run the page is open for and its preview. A filter change cancels the computation in
 * flight; each one re-reads the run from disk rather than holding its points in memory.
 */
class ExportViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RunRepository.open(app)
    private val filterStore = ExportFilterStore(app)
    private val _run = MutableStateFlow<RunInfo?>(null)
    val run: StateFlow<RunInfo?> = _run.asStateFlow()
    private val _preview = MutableStateFlow<ExportPreview>(ExportPreview.Computing)
    val preview: StateFlow<ExportPreview> = _preview.asStateFlow()
    private var opened = false
    private var previewJob: Job? = null

    val filters: FilterSettings get() = filterStore.filters

    /** Loads the run once; a recreated activity finds it already open. */
    fun open(runId: Long) {
        if (opened) return
        opened = true
        viewModelScope.launch {
            _run.value = withContext(Dispatchers.IO) { repository.listRuns().firstOrNull { it.id == runId } }
            recompute()
        }
    }

    fun setFilters(value: FilterSettings) {
        filterStore.filters = value
        recompute()
    }

    /** Writes the run as GPX to [dest] on IO, then hands the file to [onDone] on the main thread. */
    fun export(dest: File, onDone: (File) -> Unit) {
        val run = _run.value ?: return
        val filterSnapshot = filters
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dest.parentFile?.mkdirs()
                dest.outputStream().use { GpxExporter.export(it, listOf(run), filterSnapshot) }
            }
            onDone(dest)
        }
    }

    private fun recompute() {
        val run = _run.value ?: return
        val filterSnapshot = filters
        previewJob?.cancel()
        _preview.value = ExportPreview.Computing
        previewJob = viewModelScope.launch {
            _preview.value = withContext(Dispatchers.IO) {
                val records = RunReader.readAll(run.file)
                val kept = PointFilter.filter(
                    records,
                    filterSnapshot.accuracyMeters,
                    filterSnapshot.distanceMeters,
                    filterSnapshot.timeSeconds,
                )
                ExportPreview.Ready(totalPoints = records.size.toLong(), keptPoints = kept.size.toLong())
            }
        }
    }
}
