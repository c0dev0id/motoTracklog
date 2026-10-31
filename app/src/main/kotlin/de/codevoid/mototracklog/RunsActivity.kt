package de.codevoid.mototracklog

import android.app.Application
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import de.codevoid.mototracklog.data.ActiveRunStore
import de.codevoid.mototracklog.data.NavBarStore
import de.codevoid.mototracklog.data.OrientationStore
import de.codevoid.mototracklog.data.RunInfo
import de.codevoid.mototracklog.data.RunRepository
import de.codevoid.mototracklog.databinding.ActivityRunsBinding
import de.codevoid.mototracklog.ui.EscapeKeys
import de.codevoid.mototracklog.ui.Formats
import de.codevoid.mototracklog.ui.ListRowAdapter
import de.codevoid.mototracklog.ui.ListRowItem
import de.codevoid.mototracklog.ui.enableImmersiveMode
import de.codevoid.mototracklog.ui.fitContentToSystemBars
import de.codevoid.mototracklog.ui.noTransition
import de.codevoid.mototracklog.ui.runSummary
import de.codevoid.mototracklog.ui.showImmersive
import de.codevoid.mototracklog.ui.showOptionsDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.Locale

/**
 * Every recorded run, newest first. Tap opens a run's Export page; a touch long press offers
 * Delete and Merge with previous run. The run being recorded has no menu: its file is still
 * being written.
 */
class RunsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRunsBinding
    private val viewModel: RunsViewModel by viewModels()
    private val adapter = ListRowAdapter()
    private val orientationStore by lazy { OrientationStore(this) }
    private val navBarStore by lazy { NavBarStore(this) }
    private val formats by lazy { Formats(Locale.getDefault(), ZoneId.systemDefault()) }
    private val escapeKeys = EscapeKeys(onLongPress = ::finish, onShortPress = ::finish)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = orientationStore.orientation
        binding = ActivityRunsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.enableImmersiveMode(navBarStore.showNavBar)
        window.fitContentToSystemBars()

        binding.header.headerTitle.setText(R.string.runs)
        binding.header.backButton.setOnClickListener { finish() }
        binding.runList.isFocusable = false
        binding.runList.layoutManager = LinearLayoutManager(this)
        binding.runList.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.runs.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requestedOrientation = orientationStore.orientation
        viewModel.refresh()
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

    private fun render(runs: List<RunInfo>) {
        binding.emptyText.visibility = if (runs.isEmpty()) View.VISIBLE else View.GONE
        val activeId = ActiveRunStore(this).runId
        adapter.submit(
            runs.mapIndexed { index, run ->
                val summary = runSummary(run, formats)
                val active = run.id == activeId
                ListRowItem(
                    title = formats.runTitle(run.startTimeMillis),
                    subtitle = if (active) getString(R.string.run_recording) + getString(R.string.list_separator) + summary else summary,
                    onClick = {
                        startActivity(ExportActivity.intent(this, run.id))
                        noTransition()
                    },
                    // The list is newest first, so the previous run is the row below.
                    onLongClick = if (active) null else ({ showRunActions(run, runs.getOrNull(index + 1)) }),
                )
            }
        )
    }

    private fun showRunActions(run: RunInfo, previous: RunInfo?) {
        val title = formats.runTitle(run.startTimeMillis)
        val actions = mutableListOf(RunAction(getString(R.string.run_delete)) { confirmDelete(run, title) })
        if (previous != null) {
            actions += RunAction(getString(R.string.run_merge_previous)) { viewModel.mergeWithPrevious(run.id, previous.id) }
        }
        showOptionsDialog(
            context = this,
            title = title,
            options = actions.map { it.label },
            selectedIndex = -1,
        ) { index -> actions[index].onPick() }
    }

    // Delete is the one irreversible action; merging only moves points into one file.
    private fun confirmDelete(run: RunInfo, title: String) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.run_delete_confirm, title))
            .setPositiveButton(R.string.run_delete) { _, _ -> viewModel.delete(run.id) }
            .setNegativeButton(R.string.cancel, null)
            .create()
            .showImmersive()
    }
}

/** One row of a run's long-press menu. */
private class RunAction(val label: String, val onPick: () -> Unit)

class RunsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RunRepository.open(app)
    private val _runs = MutableStateFlow<List<RunInfo>>(emptyList())
    val runs: StateFlow<List<RunInfo>> = _runs.asStateFlow()

    fun refresh() = update {}

    fun delete(id: Long) = update { repository.delete(id) }

    /** The merged run keeps the earlier run's id and start time. */
    fun mergeWithPrevious(id: Long, previousId: Long) = update { repository.merge(listOf(previousId, id)) }

    /** Runs [change] on IO, then lists the runs again. */
    private fun update(change: () -> Unit) {
        viewModelScope.launch {
            _runs.value = withContext(Dispatchers.IO) {
                change()
                repository.listRuns()
            }
        }
    }
}
