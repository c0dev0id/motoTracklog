package de.codevoid.mototracklog

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.viewModels
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.GridLayoutManager
import de.codevoid.mototracklog.data.ActiveRunStore
import de.codevoid.mototracklog.data.DebugLogStore
import de.codevoid.mototracklog.data.NavBarStore
import de.codevoid.mototracklog.data.OrientationStore
import de.codevoid.mototracklog.data.RunInfo
import de.codevoid.mototracklog.data.RunRepository
import de.codevoid.mototracklog.data.SourceStore
import de.codevoid.mototracklog.data.ThemeStore
import de.codevoid.mototracklog.data.UnitsStore
import de.codevoid.mototracklog.databinding.ActivityRecordBinding
import de.codevoid.mototracklog.databinding.ViewStatTileBinding
import de.codevoid.mototracklog.service.LoggingService
import de.codevoid.mototracklog.service.LoggingState
import de.codevoid.mototracklog.service.LoggingStateHolder
import de.codevoid.mototracklog.service.NmeaDebugLog
import de.codevoid.mototracklog.ui.DASH
import de.codevoid.mototracklog.ui.EscapeKeys
import de.codevoid.mototracklog.ui.Formats
import de.codevoid.mototracklog.ui.ListRowItem
import de.codevoid.mototracklog.ui.RecordStatus
import de.codevoid.mototracklog.ui.TileAdapter
import de.codevoid.mototracklog.ui.TileItem
import de.codevoid.mototracklog.ui.bind
import de.codevoid.mototracklog.ui.blockKeyLongPress
import de.codevoid.mototracklog.ui.displaySpeed
import de.codevoid.mototracklog.ui.enableImmersiveMode
import de.codevoid.mototracklog.ui.fitContentToSystemBars
import de.codevoid.mototracklog.ui.formatDuration
import de.codevoid.mototracklog.ui.isPortrait
import de.codevoid.mototracklog.ui.noTransition
import de.codevoid.mototracklog.ui.recordStatus
import de.codevoid.mototracklog.ui.runSummary
import de.codevoid.mototracklog.ui.runUpdateFlow
import de.codevoid.mototracklog.ui.shareFile
import de.codevoid.mototracklog.ui.showOptionsDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.Locale

/**
 * The launcher entry: recording status, live values and the recording controls, with the
 * settings mode behind the header's Settings toggle. The remote reaches the primary tile
 * (Start, Pause, Resume) and nothing else on this screen until settings mode is opened by touch.
 */
class RecordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordBinding
    private val viewModel: RecordViewModel by viewModels()
    private val settingsAdapter = TileAdapter(emptyList())
    private val activeRun by lazy { ActiveRunStore(this) }
    private val sourceStore by lazy { SourceStore(this) }
    private val unitsStore by lazy { UnitsStore(this) }
    private val debugLogStore by lazy { DebugLogStore(this) }
    private val themeStore by lazy { ThemeStore(this) }
    private val orientationStore by lazy { OrientationStore(this) }
    private val navBarStore by lazy { NavBarStore(this) }
    private val formats by lazy { Formats(Locale.getDefault(), ZoneId.systemDefault()) }
    private val locationManager by lazy { getSystemService(LocationManager::class.java) }
    private val columns get() = if (isPortrait) 3 else 4
    // Invalidated by every tile that changes state, and on resume.
    private var settingsTilesCache: List<TileItem>? = null

    // What the idle status depends on outside the app; re-read on resume and when Location is
    // switched, which usually happens from the quick-settings shade without pausing this screen.
    private var hasInternalGps = false
    private var internalGpsEnabled = false
    private var preciseLocation = false

    // A short Escape leaves settings mode, then the screen. A hold does the same: a gloved press
    // is not timed precisely, and there is nothing else for a hold to do here.
    private val escapeKeys = EscapeKeys(onLongPress = ::goBack, onShortPress = ::goBack)

    private val locationPermissionLauncher =
        registerForActivityResult(RequestMultiplePermissions()) { result ->
            refreshPlatformState()
            renderRecord()
            if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) requestBackgroundLocation()
        }

    private val backgroundLocationLauncher =
        registerForActivityResult(RequestPermission()) { requestBatteryExemption() }

    private val bluetoothPermissionLauncher =
        registerForActivityResult(RequestPermission()) { granted -> if (granted) showSourcePicker() }

    private val providersChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshPlatformState()
            resumeInterruptedRun()
            renderRecord()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = orientationStore.orientation
        binding = ActivityRecordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.enableImmersiveMode(navBarStore.showNavBar)
        window.fitContentToSystemBars()

        hasInternalGps = LocationManagerCompat.hasProvider(locationManager, LocationManager.GPS_PROVIDER)
        refreshPlatformState()
        setUpStatTiles()

        binding.runsButton.setOnClickListener {
            startActivity(Intent(this, RunsActivity::class.java))
            noTransition()
        }
        binding.settingsButton.setOnClickListener { setSettingsMode(!viewModel.isSettingsMode) }
        binding.settingsGrid.layoutManager = GridLayoutManager(this, columns)
        binding.settingsGrid.adapter = settingsAdapter

        val primary = binding.actions.primaryButton
        primary.blockKeyLongPress()
        primary.setOnClickListener { onPrimaryAction() }
        binding.actions.stopButton.setOnClickListener { LoggingService.stop(this) }
        // Seeded once; the primary tile is the same view in every state, so focus stays on it.
        primary.post { primary.requestFocus() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { LoggingStateHolder.state.collect { renderRecord() } }
                // The GPS source tile is locked exactly while a run is active.
                launch {
                    LoggingStateHolder.state.map { it.runId }.distinctUntilChanged().collect { invalidateSettings() }
                }
                launch { viewModel.latestRun.collect { renderRecord() } }
            }
        }

        if (savedInstanceState == null && !hasPreciseLocation()) {
            locationPermissionLauncher.launch(locationPermissions())
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            providersChanged,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(providersChanged)
    }

    override fun onResume() {
        super.onResume()
        requestedOrientation = orientationStore.orientation
        LoggingStateHolder.uiVisible = true
        refreshPlatformState()
        resumeInterruptedRun()
        viewModel.refreshLatestRun()
        settingsTilesCache = null
        renderMode()
    }

    override fun onPause() {
        super.onPause()
        LoggingStateHolder.uiVisible = false
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

    private fun goBack() {
        if (viewModel.isSettingsMode) setSettingsMode(false) else finish()
    }

    private fun refreshPlatformState() {
        internalGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        preciseLocation = hasPreciseLocation()
    }

    /**
     * A run the platform would not resume in the background (no background location, a refused
     * restart) continues once the app is in front, where the location service may start, and
     * nothing blocks it.
     */
    private fun resumeInterruptedRun() {
        val state = LoggingStateHolder.state.value
        if (activeRun.runId != null && !state.isLogging && currentStatus(state) == RecordStatus.Idle) {
            LoggingService.resume(this)
        }
    }

    private fun currentStatus(state: LoggingState): RecordStatus =
        recordStatus(state, sourceStore.isExternal, preciseLocation, hasInternalGps, internalGpsEnabled)

    private fun onPrimaryAction() {
        val state = LoggingStateHolder.state.value
        when {
            state.isPaused -> LoggingService.unpause(this)
            state.isLogging -> LoggingService.pause(this)
            currentStatus(state) == RecordStatus.Idle -> LoggingService.start(this)
        }
    }

    private fun setSettingsMode(on: Boolean) {
        viewModel.isSettingsMode = on
        renderMode()
        if (!on) binding.actions.primaryButton.requestFocus()
    }

    private fun renderMode() {
        val settings = viewModel.isSettingsMode
        binding.settingsButton.isActivated = settings
        binding.recordContent.visibility = if (settings) View.GONE else View.VISIBLE
        binding.settingsGrid.visibility = if (settings) View.VISIBLE else View.GONE
        if (settings) {
            settingsAdapter.submit(settingsTilesCache ?: buildSettingsTiles().also { settingsTilesCache = it })
        } else {
            renderRecord()
        }
    }

    // --- Record ---------------------------------------------------------------------------

    private fun setUpStatTiles() {
        val stats = binding.stats
        stats.statSatellites.setUp(R.string.stat_satellites, unit = null)
        stats.statAccuracy.setUp(R.string.stat_accuracy, getString(R.string.unit_m))
        stats.statPoints.setUp(R.string.stat_points, unit = null)
        stats.statRate.setUp(R.string.stat_rate, getString(R.string.unit_hz))
        stats.statSpeed.setUp(R.string.stat_speed, getString(R.string.units_kmh))
        stats.statTime.setUp(R.string.stat_gps_time, unit = null)
    }

    private fun renderRecord() {
        if (viewModel.isSettingsMode) return
        val state = LoggingStateHolder.state.value
        val external = sourceStore.isExternal
        val status = currentStatus(state)
        binding.status.statusWord.setText(status.label)
        binding.status.statusWord.setTextColor(ContextCompat.getColor(this, status.color))
        binding.status.statusSubtitle.text = statusSubtitle(status, state, external)

        val primary = binding.actions.primaryButton
        // A run that was not stopped is continued by Start (LoggingService.start), so it says so.
        val resumable = state.isPaused || (!state.isLogging && activeRun.runId != null)
        primary.setText(
            when {
                resumable -> R.string.action_resume
                state.isLogging -> R.string.action_pause
                else -> R.string.action_start
            }
        )
        val enabled = state.isLogging || status == RecordStatus.Idle
        primary.isEnabled = enabled
        primary.alpha = if (enabled) 1f else DISABLED_ALPHA
        binding.actions.stopHolder.visibility = if (state.isLogging) View.VISIBLE else View.GONE

        val latest = viewModel.latestRun.value
        binding.stats.root.visibility = if (state.isLogging) View.VISIBLE else View.GONE
        binding.latestRun.root.visibility = if (!state.isLogging && latest != null) View.VISIBLE else View.GONE
        if (state.isLogging) renderStats(state) else if (latest != null) bindLatestRun(latest)
    }

    private fun statusSubtitle(status: RecordStatus, state: LoggingState, external: Boolean): String {
        val since = state.startTimeMillis?.let { getString(R.string.since, formats.clockShort(it)) }
        val start = state.startTimeMillis
        val lastFix = state.lastFixTimeMillis
        // Advances with the fixes and freezes while paused, so no clock has to tick.
        val span = if (start != null && lastFix != null && lastFix >= start) formatDuration(lastFix - start) else null
        val parts = when (status) {
            RecordStatus.Idle -> listOf(sourceName())
            RecordStatus.NoDevice -> listOf(getString(R.string.hint_no_device))
            RecordStatus.Unavailable -> listOf(getString(R.string.hint_no_precise_location))
            RecordStatus.Disabled -> listOf(getString(R.string.hint_location_off), since)
            RecordStatus.ReceiverOff -> listOf(
                getString(if (external) R.string.hint_waiting_for_receiver else R.string.hint_gnss_stopped),
                since,
            )
            RecordStatus.Searching -> listOf(getString(R.string.hint_waiting_for_fix), since)
            RecordStatus.Fix -> listOf(since, span)
            RecordStatus.Paused -> listOf(since, span?.let { getString(R.string.span_recorded, it) })
        }
        return parts.filterNotNull().joinToString(getString(R.string.list_separator))
    }

    private fun renderStats(state: LoggingState) {
        val stats = binding.stats
        stats.statSatellites.statValue.text =
            if (state.gnssRunning) "${state.satellitesUsedInFix}/${state.satellitesVisible}" else DASH
        stats.statAccuracy.statValue.text = formats.decimal(state.accuracyMeters)
        stats.statPoints.statValue.text = formats.count(state.pointCount)
        stats.statRate.statValue.text = formats.decimal(state.updateRateHz)
        val metric = unitsStore.isMetric
        stats.statSpeed.statValue.text = state.speedMetersPerSecond?.let { displaySpeed(it, metric).toString() } ?: DASH
        stats.statSpeed.statUnit.setText(if (metric) R.string.units_kmh else R.string.units_mph)
        stats.statTime.statValue.text = formats.clock(state.lastFixTimeMillis)
    }

    private fun bindLatestRun(run: RunInfo) {
        binding.latestRun.row.bind(
            ListRowItem(
                title = formats.runTitle(run.startTimeMillis),
                subtitle = runSummary(run, formats),
                onClick = {
                    startActivity(ExportActivity.intent(this, run.id))
                    noTransition()
                },
            )
        )
    }

    private fun sourceName(): String = when {
        sourceStore.isExternal -> sourceStore.bluetoothName ?: sourceStore.bluetoothAddress.orEmpty()
        hasInternalGps -> getString(R.string.source_internal)
        else -> getString(R.string.status_no_device)
    }

    // --- Permissions ----------------------------------------------------------------------

    private fun locationPermissions(): Array<String> {
        val location = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            location + Manifest.permission.POST_NOTIFICATIONS
        } else {
            location
        }
    }

    // Resuming after a reboot starts the service from the background, which the location
    // service type allows only with background location. Asked once, right after the grant.
    private fun requestBackgroundLocation() {
        if (!hasBackgroundLocation()) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            requestBatteryExemption()
        }
    }

    // Keeps the system from stopping a recording that runs for hours in the background.
    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        val power = getSystemService(PowerManager::class.java)
        if (power.isIgnoringBatteryOptimizations(packageName)) return
        runCatching { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri())) }
    }

    private fun packageUri(): Uri = Uri.fromParts("package", packageName, null)

    // --- Settings mode --------------------------------------------------------------------

    private fun invalidateSettings() {
        settingsTilesCache = null
        renderMode()
    }

    private fun buildSettingsTiles(): List<TileItem> = listOf(
        sourceTile(),
        TileItem(
            label = getString(R.string.units),
            subtitle = getString(if (unitsStore.isMetric) R.string.units_kmh else R.string.units_mph),
            onClick = { unitsStore.isMetric = !unitsStore.isMetric; invalidateSettings() },
        ),
        TileItem(
            label = getString(R.string.debug_log),
            subtitle = getString(if (debugLogStore.enabled) R.string.setting_on else R.string.setting_off),
            onClick = { debugLogStore.enabled = !debugLogStore.enabled; invalidateSettings() },
        ),
        shareDebugLogTile(),
        TileItem(
            label = getString(R.string.theme_label),
            subtitle = getString(if (themeStore.isDark) R.string.theme_dark else R.string.theme_light),
            // Recreates the activity; the view model keeps settings mode open.
            onClick = { themeStore.isDark = !themeStore.isDark },
        ),
        orientationTile(),
        TileItem(
            label = getString(R.string.nav_bar),
            subtitle = getString(if (navBarStore.showNavBar) R.string.nav_bar_visible else R.string.nav_bar_hidden),
            onClick = {
                val show = !navBarStore.showNavBar
                navBarStore.showNavBar = show
                window.enableImmersiveMode(show)
                invalidateSettings()
            },
        ),
        TileItem(
            label = getString(R.string.permissions),
            subtitle = getString(R.string.permissions_open),
            onClick = {
                runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())) }
            },
        ),
        updateTile(),
    )

    // The service reads the source when a run starts or resumes; switching it under a running
    // run would leave the screen describing a source the run is not using.
    private fun sourceTile(): TileItem {
        val locked = activeRun.runId != null
        return TileItem(
            label = getString(R.string.gps_source),
            subtitle = if (locked) {
                sourceName() + getString(R.string.list_separator) + getString(R.string.gps_source_locked)
            } else {
                sourceName()
            },
            onClick = { if (!locked) showSourcePicker() },
        )
    }

    /**
     * The internal GPS (when the device has one) and every paired classic Bluetooth device:
     * Bluetooth has no "GNSS receiver" class, so the user picks. The picker is never empty of a
     * way forward: without access it offers the grant, without paired devices the pairing screen.
     */
    private fun showSourcePicker() {
        val options = mutableListOf<SourceOption>()
        if (hasInternalGps) options += SourceOption(getString(R.string.source_internal)) { sourceStore.useInternal() }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter != null) {
            if (!hasBluetoothAccess()) {
                options += SourceOption(getString(R.string.source_allow_bluetooth)) {
                    bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            } else {
                val receivers = pairedReceivers(adapter)
                receivers.forEach { (address, name) ->
                    options += SourceOption(name) { sourceStore.useBluetooth(address, name) }
                }
                if (receivers.isEmpty()) {
                    options += SourceOption(getString(R.string.source_pair_receiver)) {
                        runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                    }
                }
            }
        }
        val current = if (sourceStore.isExternal) sourceStore.bluetoothName else getString(R.string.source_internal)
        showOptionsDialog(
            context = this,
            title = getString(R.string.gps_source),
            options = options.map { it.label },
            selectedIndex = options.indexOfFirst { it.label == current },
        ) { index ->
            options[index].onPick()
            invalidateSettings()
        }
    }

    /** Paired classic and dual-mode devices as (address, name), by name. Needs [hasBluetoothAccess]. */
    @SuppressLint("MissingPermission")
    private fun pairedReceivers(adapter: BluetoothAdapter): List<Pair<String, String>> =
        adapter.bondedDevices.orEmpty()
            .filter { it.type == BluetoothDevice.DEVICE_TYPE_CLASSIC || it.type == BluetoothDevice.DEVICE_TYPE_DUAL }
            .map { it.address to (it.name ?: it.address) }
            .sortedBy { it.second.lowercase(Locale.ROOT) }

    private fun shareDebugLogTile(): TileItem {
        val log = NmeaDebugLog.dir(this).listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }
        return TileItem(
            label = getString(R.string.share_debug_log),
            subtitle = log?.let { getString(R.string.debug_log_captured, formats.runTitle(it.lastModified())) }
                ?: getString(R.string.debug_log_none),
            onClick = { log?.let { shareFile(it, MIME_TEXT, getString(R.string.share_debug_log_title)) } },
        )
    }

    // A picker rather than a cycle: tapping through five orientations would apply every one on
    // the way to the wanted one.
    private fun orientationTile(): TileItem = TileItem(
        label = getString(R.string.orientation),
        subtitle = getString(ORIENTATION_OPTIONS[orientationIndex(orientationStore.orientation)].second),
        onClick = {
            showOptionsDialog(
                context = this,
                title = getString(R.string.orientation),
                options = ORIENTATION_OPTIONS.map { getString(it.second) },
                selectedIndex = orientationIndex(orientationStore.orientation),
            ) { index ->
                val picked = ORIENTATION_OPTIONS[index].first
                orientationStore.orientation = picked
                requestedOrientation = picked
                invalidateSettings()
            }
        },
    )

    private fun orientationIndex(value: Int): Int =
        ORIENTATION_OPTIONS.indexOfFirst { it.first == value }.coerceAtLeast(0)

    private fun updateTile(): TileItem {
        // While checking, the subtitle changes and the click is a no-op, against a double tap.
        val isChecking = viewModel.isCheckingUpdate
        val progressSub = viewModel.downloadProgressSubtitle
        val label = getString(R.string.check_for_updates)
        return TileItem(
            label = label,
            subtitle = when {
                progressSub != null -> progressSub
                isChecking -> getString(R.string.checking_updates)
                else -> BuildConfig.VERSION_NAME
            },
            onClick = if (isChecking) ({}) else ({
                viewModel.isCheckingUpdate = true
                invalidateSettings()
                runUpdateFlow(
                    setClickable = { enabled ->
                        if (enabled) {
                            viewModel.isCheckingUpdate = false
                            viewModel.downloadProgressSubtitle = null
                            invalidateSettings()
                        }
                    },
                    // Patches the tile in place, and on screen only while settings mode shows.
                    setSubtitle = { text ->
                        viewModel.downloadProgressSubtitle = text
                        settingsTilesCache = settingsTilesCache?.map {
                            if (it.label == label) it.copy(subtitle = text) else it
                        }
                        if (viewModel.isSettingsMode) {
                            settingsAdapter.updateItem({ it.label == label }) { it.copy(subtitle = text) }
                        }
                    },
                )
            }),
        )
    }

    companion object {
        private const val DISABLED_ALPHA = 0.35f
        private const val MIME_TEXT = "text/plain"

        // FULL_SENSOR is the exact superset of the four fixed options and follows the
        // accelerometer regardless of the system auto-rotate switch.
        private val ORIENTATION_OPTIONS = listOf(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to R.string.orientation_landscape,
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to R.string.orientation_portrait,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE to R.string.orientation_reverse_landscape,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT to R.string.orientation_reverse_portrait,
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR to R.string.orientation_sensor,
        )
    }
}

/** One row of the GPS source picker. */
private class SourceOption(val label: String, val onPick: () -> Unit)

private fun ViewStatTileBinding.setUp(@StringRes label: Int, unit: String?) {
    statLabel.setText(label)
    statUnit.text = unit
    statUnit.visibility = if (unit != null) View.VISIBLE else View.GONE
}

@get:StringRes
private val RecordStatus.label: Int
    get() = when (this) {
        RecordStatus.Idle -> R.string.status_ready
        RecordStatus.NoDevice -> R.string.status_no_device
        RecordStatus.Unavailable -> R.string.status_unavailable
        RecordStatus.Paused -> R.string.status_paused
        RecordStatus.Disabled -> R.string.status_gps_disabled
        RecordStatus.ReceiverOff -> R.string.status_receiver_off
        RecordStatus.Searching -> R.string.status_searching
        RecordStatus.Fix -> R.string.status_recording
    }

/** Accent while points arrive, alert for anything that stops or starves a run. */
@get:ColorRes
private val RecordStatus.color: Int
    get() = when (this) {
        RecordStatus.Fix -> R.color.tile_focused
        RecordStatus.Paused -> R.color.on_surface_muted
        RecordStatus.Idle, RecordStatus.Searching -> R.color.on_surface
        RecordStatus.NoDevice, RecordStatus.Unavailable, RecordStatus.Disabled, RecordStatus.ReceiverOff -> R.color.alert
    }

/**
 * Survives the theme toggle's recreate: the mode, the update check and its progress, and the
 * latest run, which the idle screen shows. The latest run is re-read whenever a run starts or
 * stops, and on resume.
 */
class RecordViewModel(app: Application) : AndroidViewModel(app) {

    var isSettingsMode: Boolean = false
    var isCheckingUpdate: Boolean = false
    var downloadProgressSubtitle: String? = null

    private val repository = RunRepository.open(app)
    private val _latestRun = MutableStateFlow<RunInfo?>(null)
    val latestRun: StateFlow<RunInfo?> = _latestRun.asStateFlow()

    init {
        viewModelScope.launch {
            LoggingStateHolder.state.map { it.runId }.distinctUntilChanged().collect { refreshLatestRun() }
        }
    }

    fun refreshLatestRun() {
        viewModelScope.launch {
            _latestRun.value = withContext(Dispatchers.IO) { repository.listRuns().firstOrNull() }
        }
    }
}
