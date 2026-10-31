package de.codevoid.mototracklog.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.location.Location
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import de.codevoid.mototracklog.R
import de.codevoid.mototracklog.RecordActivity
import de.codevoid.mototracklog.data.ActiveRunStore
import de.codevoid.mototracklog.data.DebugLogStore
import de.codevoid.mototracklog.data.RunReader
import de.codevoid.mototracklog.data.RunRepository
import de.codevoid.mototracklog.data.RunWriter
import de.codevoid.mototracklog.data.SourceStore
import de.codevoid.mototracklog.hasBackgroundLocation
import de.codevoid.mototracklog.hasBluetoothAccess
import de.codevoid.mototracklog.hasPreciseLocation
import de.codevoid.mototracklog.model.GpsRecord
import java.io.File
import java.util.Locale
import java.util.concurrent.Executor

/**
 * Foreground service that records raw GPS fixes at the chipset rate, from either the internal
 * provider or an external Bluetooth NMEA receiver. Fixes are delivered on a dedicated HandlerThread
 * (never the main Looper); each one is appended to the writer's in-memory buffer and flushed on a
 * fixed cadence, while UI state and the notification are updated at a throttled rate. No wake lock
 * is held: the location service type keeps the subsystems running.
 *
 * The service exists only for the duration of a run and resumes an interrupted one on a
 * system-driven restart (START_STICKY) or a reboot (BootReceiver). Pausing releases the fix source
 * entirely and is persisted, so a paused run survives a restart without drawing power.
 */
class LoggingService : Service(), FixSink {

    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private val handlerExecutor = Executor { handler.post(it) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var locationManager: LocationManager
    private lateinit var activeRun: ActiveRunStore
    private lateinit var sourceStore: SourceStore
    private lateinit var debugLogStore: DebugLogStore
    private lateinit var runs: RunRepository

    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val tapIntent by lazy {
        PendingIntent.getActivity(this, 0, Intent(this, RecordActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    }
    private val pauseAction by lazy { notificationAction(R.string.notif_action_pause, ACTION_PAUSE) }
    private val resumeAction by lazy { notificationAction(R.string.notif_action_resume, ACTION_UNPAUSE) }
    private val stopAction by lazy { notificationAction(R.string.notif_action_stop, ACTION_STOP) }

    @Volatile
    private var running = false

    // Handler-thread-only state.
    private var paused = false
    private var writer: RunWriter? = null
    private var bluetoothSource: BluetoothNmeaSource? = null
    private var debugLog: NmeaDebugLog? = null
    private var runId: Long = -1L
    private var pointCount: Long = 0
    private val fixTimestampsNanos = ArrayDeque<Long>()
    private var lastUiUpdateMs = 0L
    private var lastNotifUpdateMs = 0L

    override fun onCreate() {
        super.onCreate()
        activeRun = ActiveRunStore(this)
        sourceStore = SourceStore(this)
        debugLogStore = DebugLogStore(this)
        runs = RunRepository.open(this)
        locationManager = getSystemService(LocationManager::class.java)
        createChannel()
        handlerThread = HandlerThread("gps-logger").apply { start() }
        handler = Handler(handlerThread.looper)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                handler.post { stopLogging(clearActive = true) }
                return START_NOT_STICKY
            }
            // Guarded: these also arrive from the notification, which can outlive a dead run.
            ACTION_PAUSE -> handler.post { if (running) setPaused(true) }
            ACTION_UNPAUSE -> handler.post { if (running) setPaused(false) }
            // Only Stop ends a run: a Start while one is pending continues it.
            ACTION_START -> beginForegroundAndLog(newRun = activeRun.runId == null)
            else -> beginForegroundAndLog(newRun = false) // ACTION_RESUME or sticky restart (null)
        }
        return START_STICKY
    }

    private fun beginForegroundAndLog(newRun: Boolean) {
        // A Start or Resume for the live run (a double press, a redelivered intent) changes nothing.
        if (running) return
        val id = if (newRun) {
            System.currentTimeMillis().also { activeRun.start(it) }
        } else {
            activeRun.runId
        }
        if (id == null) {
            stopSelf()
            return
        }
        running = true
        val notification = buildNotification(getString(R.string.notif_starting))
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (e: RuntimeException) {
            // The location service type needs a location permission at runtime, and a background
            // start (BootReceiver, a sticky restart) additionally needs ACCESS_BACKGROUND_LOCATION.
            // The platform answers a shortfall by throwing on the main thread. A run that never
            // began leaves no marker behind; an interrupted one keeps its own, so it can still be
            // resumed once the permission is there.
            Log.w(TAG, "foreground service refused; not logging", e)
            running = false
            if (newRun) activeRun.clear()
            stopSelf()
            return
        }
        handler.post { startLogging(id, newRun) }
    }

    private fun startLogging(id: Long, newRun: Boolean) {
        // A Start always comes from the Record screen; only a resume can be a background start.
        if (!newRun && isBackgroundStartWithoutLocation()) {
            Log.w(TAG, "resumed in the background without background location; waiting for the app")
            stopLogging(clearActive = false)
            return
        }
        val file = runs.runFile(id)
        runId = id
        val summary = RunReader.summary(file)
        pointCount = summary.pointCount
        val startTimeMillis = summary.first?.timeMillis ?: id
        fixTimestampsNanos.clear()
        lastUiUpdateMs = 0L
        lastNotifUpdateMs = 0L
        // A run paused before a reboot or a process kill comes back paused: nothing is acquired.
        paused = activeRun.isPaused
        // Acquired before the writer exists so a refusal leaves no empty run file behind. Only a
        // run that never began gives up its marker: at BOOT_COMPLETED the Bluetooth adapter is
        // routinely not up yet, and clearing there would end a run the user never stopped.
        if (!paused && !startSource()) {
            stopLogging(clearActive = newRun)
            return
        }
        writer = RunWriter(file)
        LoggingStateHolder.set(
            LoggingState(
                runId = id,
                startTimeMillis = startTimeMillis,
                pointCount = pointCount,
                isPaused = paused,
                // External "enabled" flips true once the link is up.
                gpsEnabled = isInternalSource() && internalGpsEnabled(),
            )
        )
        if (paused) {
            updateNotification(getString(R.string.notif_paused, pointCount))
        } else {
            handler.postDelayed(flushRunnable, FLUSH_INTERVAL_MS)
        }
    }

    private fun isInternalSource(): Boolean = !sourceStore.isExternal

    /**
     * From Android 11, a location service started while no screen is visible (BootReceiver, a
     * sticky restart) gets no fixes from the internal GPS without background location.
     */
    private fun isBackgroundStartWithoutLocation(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            isInternalSource() &&
            !LoggingStateHolder.uiVisible &&
            !hasBackgroundLocation()

    /**
     * Whether the internal provider is switched on. "True if the provider exists and is enabled",
     * so a device with no chipset reads as off here too; telling absent from switched off is
     * `hasProvider`'s job.
     */
    private fun internalGpsEnabled(): Boolean =
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    /**
     * Acquires the configured fix source. Returns false when it cannot be started (permission
     * refused, no chipset, Bluetooth off), having registered nothing. Logger thread only.
     */
    private fun startSource(): Boolean {
        val address = sourceStore.bluetoothAddress
        val permitted = if (address == null) hasPreciseLocation() else hasBluetoothAccess()
        if (!permitted) {
            Log.w(TAG, "source permission not granted; refusing to log")
            return false
        }
        // requestLocationUpdates throws for a provider that does not exist, on this thread.
        if (address == null && !LocationManagerCompat.hasProvider(locationManager, LocationManager.GPS_PROVIDER)) {
            Log.w(TAG, "device has no GPS provider; refusing to log")
            return false
        }
        val debugFile = if (debugLogStore.enabled) {
            File(NmeaDebugLog.dir(this).apply { mkdirs() }, "nmea-$runId.log")
        } else {
            null
        }
        if (address == null) {
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 0L, 0f, listener, handlerThread.looper,
                )
                LocationManagerCompat.registerGnssStatusCallback(locationManager, handlerExecutor, gnssCallback)
            } catch (e: SecurityException) {
                Log.w(TAG, "location updates refused", e)
                return false
            } catch (e: IllegalArgumentException) {
                // hasProvider and this call are separate round trips; the provider can go away.
                Log.w(TAG, "GPS provider gone since it was checked; refusing to log", e)
                return false
            }
            debugFile?.let { openInternalDebugLog(it) }
        } else {
            val adapter = getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null || !adapter.isEnabled) {
                Log.w(TAG, "Bluetooth unavailable or disabled; refusing to log")
                return false
            }
            bluetoothSource = BluetoothNmeaSource(
                context = this,
                adapter = adapter,
                deviceAddress = address,
                sink = this,
                deliver = handler,
                debugFile = debugFile,
            ).also { it.start() }
        }
        return true
    }

    /** Debug only: the chipset's identity and capabilities, then its NMEA as the HAL emits it. */
    private fun openInternalDebugLog(file: File) {
        val log = runCatching { NmeaDebugLog(file) }.getOrNull() ?: return
        debugLog = log
        log.note("session start; internal GPS_PROVIDER")
        log.note("hardwareModel=${LocationManagerCompat.getGnssHardwareModelName(locationManager) ?: "unknown"}")
        log.note("hardwareYear=${LocationManagerCompat.getGnssYearOfHardware(locationManager)}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            log.note("capabilities=${locationManager.gnssCapabilities}")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                locationManager.addNmeaListener(handlerExecutor, nmeaListener)
            } else {
                @Suppress("DEPRECATION")
                locationManager.addNmeaListener(nmeaListener, handler)
            }
        } catch (e: SecurityException) {
            log.note("NMEA listener refused: ${e.message}")
        }
    }

    /** Releases the fix source and its debug log. Safe to call when nothing is acquired. */
    private fun stopSource() {
        bluetoothSource?.stop()
        bluetoothSource = null
        try {
            locationManager.removeUpdates(listener)
            LocationManagerCompat.unregisterGnssStatusCallback(locationManager, gnssCallback)
            locationManager.removeNmeaListener(nmeaListener)
        } catch (_: Exception) {
        }
        debugLog?.note("session end")
        debugLog?.close()
        debugLog = null
    }

    // The compat interface supplies the provider and status callbacks, which are abstract in
    // LocationListener before Android 11; a lambda would throw AbstractMethodError there.
    private val listener = object : LocationListenerCompat {
        override fun onLocationChanged(location: Location) = onFix(location.toRecord())
        override fun onProviderEnabled(provider: String) = onSourceEnabled(true)
        override fun onProviderDisabled(provider: String) = onSourceEnabled(false)
    }

    /** Debug only: tees the internal chipset's NMEA (delivered on the handler thread) to the log. */
    private val nmeaListener = OnNmeaMessageListener { message, _ -> debugLog?.line(message.trim()) }

    private val gnssCallback = object : GnssStatusCompat.Callback() {
        override fun onStarted() {
            LoggingStateHolder.update { it.copy(gnssRunning = true) }
        }

        override fun onStopped() {
            LoggingStateHolder.update { it.receiverStopped() }
        }

        override fun onSatelliteStatusChanged(status: GnssStatusCompat) {
            var used = 0
            for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
            onSatelliteStatus(visible = status.satelliteCount, usedInFix = used)
        }
    }

    /** [FixSink] — receiver/provider on/off; off also drops the (now meaningless) satellite state. */
    override fun onSourceEnabled(enabled: Boolean) {
        if (paused) return
        LoggingStateHolder.update {
            if (enabled) it.copy(gpsEnabled = true) else it.receiverStopped().copy(gpsEnabled = false)
        }
        if (!enabled) updateNotification(getString(R.string.notif_receiver_off))
    }

    /**
     * Pause releases the fix source outright rather than just dropping fixes, so the GPS chipset
     * (or the Bluetooth link) powers down and a paused run costs no battery. Resuming re-acquires
     * it; a GPS warm start is quick because the ephemeris is still cached.
     */
    private fun setPaused(value: Boolean) {
        if (paused == value) return
        if (value) {
            paused = true
            activeRun.isPaused = true
            handler.removeCallbacks(flushRunnable)
            stopSource()
            writer?.flush()
            fixTimestampsNanos.clear()
            LoggingStateHolder.update {
                it.receiverStopped().copy(
                    isPaused = true,
                    updateRateHz = 0f,
                    speedMetersPerSecond = null,
                    accuracyMeters = null,
                )
            }
            updateNotificationNow(getString(R.string.notif_paused, pointCount))
        } else {
            paused = false
            if (!startSource()) {
                // Stay paused rather than discard a run the user still owns; the notification
                // names the problem and Resume can be pressed again once it is fixed.
                paused = true
                stopSource()
                updateNotificationNow(getString(R.string.notif_paused_blocked))
                return
            }
            activeRun.isPaused = false
            fixTimestampsNanos.clear()
            LoggingStateHolder.update {
                it.copy(
                    isPaused = false,
                    // Re-read: registering a listener does not report the provider's current
                    // state, and onProviderDisabled only fires on a change.
                    gpsEnabled = isInternalSource() && internalGpsEnabled(),
                )
            }
            updateNotificationNow(loggingNotifText())
            handler.postDelayed(flushRunnable, FLUSH_INTERVAL_MS)
        }
    }

    /** Also stamps the throttle clock, so a fix cannot overwrite it in the next throttle window. */
    private fun updateNotificationNow(text: String) {
        lastNotifUpdateMs = SystemClock.elapsedRealtime()
        updateNotification(text)
    }

    private fun loggingNotifText(rate: Float = computeRateHz()): String =
        getString(R.string.notif_logging, pointCount, String.format(Locale.US, "%.1f", rate))

    /** [FixSink] — ~1 Hz from the GNSS engine (or NMEA GGA/GSV); drives the no-fix notification. */
    override fun onSatelliteStatus(visible: Int, usedInFix: Int) {
        if (paused) return
        LoggingStateHolder.update { state ->
            if (LoggingStateHolder.uiVisible) {
                state.copy(gnssRunning = true, satellitesVisible = visible, satellitesUsedInFix = usedInFix)
            } else {
                // gnssRunning must stay accurate for the Bluetooth path, where it is the only sign
                // the receiver is live. StateFlow skips an unchanged value, so this is free.
                state.copy(gnssRunning = true)
            }
        }
        if (usedInFix > 0) return
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastNotifUpdateMs >= NOTIF_THROTTLE_MS) {
            lastNotifUpdateMs = nowMs
            updateNotification(getString(R.string.notif_no_fix, visible))
        }
    }

    /** [FixSink] — one recorded fix, from the internal provider or an external NMEA source. */
    override fun onFix(record: GpsRecord) {
        val w = writer ?: return
        if (paused) return
        w.append(record)
        pointCount += 1

        fixTimestampsNanos.addLast(record.elapsedRealtimeNanos)
        while (fixTimestampsNanos.size > RATE_WINDOW) fixTimestampsNanos.removeFirst()

        val nowMs = SystemClock.elapsedRealtime()
        val uiDue = LoggingStateHolder.uiVisible && nowMs - lastUiUpdateMs >= UI_THROTTLE_MS
        val notifDue = nowMs - lastNotifUpdateMs >= NOTIF_THROTTLE_MS
        if (uiDue || notifDue) {
            val rate = computeRateHz()
            if (uiDue) {
                lastUiUpdateMs = nowMs
                LoggingStateHolder.update {
                    it.copy(
                        pointCount = pointCount,
                        updateRateHz = rate,
                        lastFixTimeMillis = record.timeMillis,
                        speedMetersPerSecond = record.speed,
                        accuracyMeters = record.accuracy,
                    )
                }
            }
            if (notifDue) {
                lastNotifUpdateMs = nowMs
                updateNotification(loggingNotifText(rate))
            }
        }
    }

    private fun computeRateHz(): Float {
        if (fixTimestampsNanos.size < 2) return 0f
        val span = fixTimestampsNanos.last() - fixTimestampsNanos.first()
        if (span <= 0L) return 0f
        return (fixTimestampsNanos.size - 1) * 1_000_000_000f / span
    }

    private val flushRunnable = object : Runnable {
        override fun run() {
            writer?.flush()
            handler.postDelayed(this, FLUSH_INTERVAL_MS)
        }
    }

    /** Releases what a run holds: the flush timer, the fix source and the writer. Logger thread only. */
    private fun releaseRun() {
        handler.removeCallbacks(flushRunnable)
        stopSource()
        writer?.close()
        writer = null
    }

    /** Logger thread only. [clearActive] drops the resume marker; false keeps an interrupted run resumable. */
    private fun stopLogging(clearActive: Boolean) {
        releaseRun()
        running = false
        if (clearActive) activeRun.clear()
        LoggingStateHolder.reset()
        mainHandler.post {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Posted: the run state is logger-thread-only, and quitSafely still delivers a due message.
        handler.post(::releaseRun)
        handlerThread.quitSafely()
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        notificationManager.createNotificationChannel(channel)
    }

    /** [action] adds the matching Pause or Resume button; null builds the plain notification. */
    private fun buildNotification(text: String, action: String? = null): Notification {
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setOngoing(true)
            .setContentIntent(tapIntent)
        if (action != null) builder.addAction(if (action == ACTION_UNPAUSE) resumeAction else pauseAction)
        // Stop is always offered, after the toggle so the Pause/Resume slot never moves.
        builder.addAction(stopAction)
        return builder.build()
    }

    /**
     * Built once per action and reused by every update. No icon: the standard template does not
     * draw action icons. Distinct request codes keep the three intents from overwriting each other.
     */
    private fun notificationAction(label: Int, action: String): Notification.Action =
        Notification.Action.Builder(null as Icon?, getString(label), serviceIntent(action)).build()

    private fun serviceIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        intent(this, action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Logger thread only — it reads [paused] to decide which action the notification offers. */
    private fun updateNotification(text: String) {
        notificationManager.notify(NOTIF_ID, buildNotification(text, if (paused) ACTION_UNPAUSE else ACTION_PAUSE))
    }

    companion object {
        const val ACTION_START = "de.codevoid.mototracklog.action.START"
        const val ACTION_STOP = "de.codevoid.mototracklog.action.STOP"
        const val ACTION_RESUME = "de.codevoid.mototracklog.action.RESUME"
        const val ACTION_PAUSE = "de.codevoid.mototracklog.action.PAUSE"
        const val ACTION_UNPAUSE = "de.codevoid.mototracklog.action.UNPAUSE"

        private const val TAG = "LoggingService"
        private const val CHANNEL_ID = "recording"
        private const val NOTIF_ID = 1
        private const val FLUSH_INTERVAL_MS = 60_000L
        private const val UI_THROTTLE_MS = 2_000L
        private const val NOTIF_THROTTLE_MS = 2_000L
        private const val RATE_WINDOW = 10

        /** Starts a new run, or continues the pending one if it was never stopped. */
        fun start(context: Context) = context.startForegroundService(intent(context, ACTION_START))
        fun stop(context: Context) = context.startService(intent(context, ACTION_STOP))
        fun resume(context: Context) = context.startForegroundService(intent(context, ACTION_RESUME))
        fun pause(context: Context) = context.startService(intent(context, ACTION_PAUSE))
        fun unpause(context: Context) = context.startService(intent(context, ACTION_UNPAUSE))

        fun intent(context: Context, action: String): Intent =
            Intent(context, LoggingService::class.java).setAction(action)
    }
}

private fun Location.toRecord(): GpsRecord = GpsRecord(
    timeMillis = time,
    elapsedRealtimeNanos = elapsedRealtimeNanos,
    latitude = latitude,
    longitude = longitude,
    altitude = if (hasAltitude()) altitude else null,
    accuracy = if (hasAccuracy()) accuracy else null,
    verticalAccuracy = if (hasVerticalAccuracy()) verticalAccuracyMeters else null,
    speed = if (hasSpeed()) speed else null,
    speedAccuracy = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
    bearing = if (hasBearing()) bearing else null,
    bearingAccuracy = if (hasBearingAccuracy()) bearingAccuracyDegrees else null,
)
