package de.roadcam.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaFormat
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.ExperimentalMimeTypeApi
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class RecordingService : LifecycleService() {

    inner class LocalBinder : android.os.Binder() {
        fun service(): RecordingService = this@RecordingService
    }

    companion object {
        private const val ACTION_START = "de.roadcam.app.action.START"
        private const val ACTION_STOP = "de.roadcam.app.action.STOP"
        private const val ACTION_STOP_RECORDING = "de.roadcam.app.action.STOP_RECORDING"
        private const val CHANNEL_ID = "roadcam_recording"
        private const val NOTIFICATION_ID = 42
        private const val EVENT_RECORDING_MS = 60_000L

        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecordingService::class.java))
        }
    }

    private val binder = LocalBinder()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val overlapExecutor = Executors.newSingleThreadExecutor()
    private val _state = MutableStateFlow(DashcamUiState())
    val state: StateFlow<DashcamUiState> = _state.asStateFlow()

    private lateinit var settingsStore: SettingsStore
    private lateinit var repository: ClipRepository
    private var settings = DashcamSettings()

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var surfaceProvider: Preview.SurfaceProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var canSwitchCamera = false
    private var hevcActive = false

    private var recordingRequested = false
    private var activeRecording: Recording? = null
    private var currentFile: File? = null
    private var previousSegment: File? = null
    private var isCurrentProtected = false
    private var isCurrentShockProtected = false
    private var eventRecordingActive = false
    private var wakeLock: PowerManager.WakeLock? = null

    private var sensorManager: SensorManager? = null
    private var shockSensor: Sensor? = null
    private var shockDetector: ShockDetector? = null

    private val shockListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor != shockSensor) return
            shockDetector?.onAcceleration(
                event.values[0],
                event.values[1],
                event.values[2],
                event.timestamp / 1_000_000L
            )
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val segmentTimer = Runnable { activeRecording?.stop() }
    private val eventStopTimer = Runnable { finishEventRecording() }

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        settings = settingsStore.load()
        repository = ClipRepository(
            directory = recordingsDirectory(),
            protectionStore = SharedPreferencesProtectionStore(this)
        )
        createNotificationChannel()
        try {
            startForegroundWithType()
        } catch (_: Exception) {
            stopSelf()
            return
        }
        refreshClips()
        updateState { it.copy(settings = settings, status = UiText(R.string.status_preparing)) }
        bindCamera()
        updateShockMonitoring()
    }

    override fun onBind(intent: Intent): android.os.IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                return START_NOT_STICKY
            }
            ACTION_STOP_RECORDING -> stopRecording()
            ACTION_START -> Unit
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        shutdown()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        recordingRequested = false
        eventRecordingActive = false
        mainHandler.removeCallbacks(segmentTimer)
        mainHandler.removeCallbacks(eventStopTimer)
        activeRecording?.stop()
        stopShockMonitoring()
        releaseWakeLock()
        cameraProvider?.unbindAll()
        overlapExecutor.shutdown()
        super.onDestroy()
    }

    fun setPreviewSurfaceProvider(provider: Preview.SurfaceProvider?) {
        surfaceProvider = provider
        preview?.setSurfaceProvider(provider)
    }

    fun updateSettings(settings: DashcamSettings) {
        val previous = this.settings
        this.settings = settings
        settingsStore.save(settings)
        updateState { it.copy(settings = settings) }
        if (settings.audioEnabled != previous.audioEnabled) {
            startForegroundWithType()
        }
        val needsCameraRebind = !recordingRequested && (
            settings.quality != previous.quality ||
                settings.hevcEnabled != previous.hevcEnabled
            )
        if (needsCameraRebind) {
            bindCamera()
        }
        if (settings.gSensorEnabled != previous.gSensorEnabled ||
            settings.gSensorSensitivity != previous.gSensorSensitivity ||
            settings.eventRecordingEnabled != previous.eventRecordingEnabled
        ) {
            updateShockMonitoring()
        }
        if (settings.deleteOldClips) {
            pruneClips()
            refreshClips()
        }
    }

    fun startRecording() {
        if (recordingRequested) return
        if (videoCapture == null) {
            updateState { it.copy(status = UiText(R.string.status_camera_not_ready)) }
            return
        }
        recordingRequested = true
        previousSegment = null
        acquireWakeLock()
        updateShockMonitoring()
        updateState {
            it.copy(
                recording = true,
                status = UiText(R.string.status_recording, settings.clipMinutes)
            )
        }
        updateNotification()
        startSegment()
    }

    fun stopRecording() {
        recordingRequested = false
        eventRecordingActive = false
        mainHandler.removeCallbacks(segmentTimer)
        mainHandler.removeCallbacks(eventStopTimer)
        updateShockMonitoring()
        releaseWakeLock()
        val recording = activeRecording
        if (recording == null) {
            updateState {
                it.copy(recording = false, currentProtected = false, status = UiText(R.string.status_stopped))
            }
            updateNotification()
        } else {
            recording.stop()
        }
    }

    fun toggleCurrentProtection() {
        if (recordingRequested) {
            val file = currentFile ?: return
            isCurrentProtected = !isCurrentProtected
            if (isCurrentProtected) {
                repository.addProtected(file.name)
            } else {
                repository.removeProtected(file.name)
                isCurrentShockProtected = false
            }
        } else {
            val newest = repository.listClips().firstOrNull()
            if (newest == null) {
                updateState { it.copy(status = UiText(R.string.status_no_clip)) }
                return
            }
            if (newest.protected) repository.removeProtected(newest.file.name)
            else repository.addProtected(newest.file.name)
        }
        refreshClips()
    }

    fun toggleClipProtection(clip: DashClip) {
        if (clip.protected) repository.removeProtected(clip.file.name)
        else repository.addProtected(clip.file.name)
        refreshClips()
    }

    fun deleteClip(clip: DashClip) {
        when (repository.deleteClip(clip, currentFile)) {
            DeleteResult.DELETED -> refreshClips()
            DeleteResult.PROTECTED -> updateState {
                it.copy(status = UiText(R.string.status_clip_protected))
            }
            DeleteResult.MISSING -> Unit
        }
    }

    fun switchCamera() {
        if (recordingRequested || !canSwitchCamera) return
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        bindCamera()
    }

    fun recordsDirectory(): File = recordingsDirectory()

    fun shutdown() {
        recordingRequested = false
        eventRecordingActive = false
        mainHandler.removeCallbacks(segmentTimer)
        mainHandler.removeCallbacks(eventStopTimer)
        activeRecording?.stop()
        stopShockMonitoring()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startSegment() {
        if (!recordingRequested) return
        val capture = videoCapture
        if (capture == null) {
            stopRecording()
            updateState { it.copy(status = UiText(R.string.status_camera_unavailable)) }
            return
        }
        pruneClips()
        val directory = recordingsDirectory()
        if (directory.usableSpace < DashcamSettings.FREE_SPACE_RESERVE_BYTES) {
            stopRecording()
            updateState { it.copy(status = UiText(R.string.status_low_storage)) }
            return
        }
        val file = nextFile(directory)
        currentFile = file
        isCurrentProtected = settings.autoProtect
        isCurrentShockProtected = false
        if (isCurrentProtected) repository.addProtected(file.name)
        updateState { it.copy(currentProtected = isCurrentProtected) }

        val outputOptions = FileOutputOptions.Builder(file).build()
        val pending = capture.output.prepareRecording(this, outputOptions).apply {
            if (settings.audioEnabled && hasAudioPermission()) withAudioEnabled()
        }
        activeRecording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            if (event is VideoRecordEvent.Finalize) handleFinalize(file, event)
        }
        mainHandler.removeCallbacks(segmentTimer)
        mainHandler.postDelayed(segmentTimer, settings.segmentLengthMs)
    }

    private fun handleFinalize(file: File, event: VideoRecordEvent.Finalize) {
        mainHandler.removeCallbacks(segmentTimer)
        activeRecording = null
        currentFile = null

        if (event.hasError()) {
            file.delete()
            repository.removeProtected(file.name)
            if (recordingRequested) {
                stopRecording()
                updateState { it.copy(status = UiText(R.string.status_recording_error)) }
            } else {
                updateState { it.copy(recording = false, currentProtected = false) }
            }
            refreshClips()
            updateNotification()
            return
        }

        if (isCurrentProtected) repository.addProtected(file.name)
        else repository.removeProtected(file.name)
        refreshClips()

        val previous = previousSegment
        if (previous != null && settings.overlapSeconds > 0 && previous.exists()) {
            val seconds = settings.overlapSeconds
            overlapExecutor.execute {
                SegmentOverlap.prependTail(previous, file, seconds)
                mainHandler.post { refreshClips() }
            }
        }
        previousSegment = file

        if (recordingRequested) {
            startSegment()
        } else {
            updateState {
                it.copy(
                    recording = false,
                    currentProtected = false,
                    status = UiText(R.string.status_saved)
                )
            }
            updateNotification()
        }
    }

    private fun nextFile(directory: File): File {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        var candidate = File(directory, "RoadCam_$stamp.mp4")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(directory, "RoadCam_${stamp}_$suffix.mp4")
            suffix++
        }
        return candidate
    }

    private fun bindCamera() {
        if (recordingRequested) return
        if (!hasCameraPermission()) {
            updateState {
                it.copy(
                    cameraReady = false,
                    status = UiText(R.string.status_permission_required)
                )
            }
            return
        }
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = try {
                future.get()
            } catch (_: Exception) {
                null
            }
            if (provider == null) {
                updateState { it.copy(status = UiText(R.string.status_camera_error)) }
                return@addListener
            }
            cameraProvider = provider
            try {
                bindUseCases(provider)
            } catch (_: Exception) {
                videoCapture = null
                updateState {
                    it.copy(cameraReady = false, status = UiText(R.string.status_camera_error))
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalMimeTypeApi::class)
    private fun bindUseCases(provider: ProcessCameraProvider) {
        val hasFront = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        val hasBack = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
        canSwitchCamera = hasFront && hasBack
        val selector = when {
            lensFacing == CameraSelector.LENS_FACING_FRONT && hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
            lensFacing == CameraSelector.LENS_FACING_BACK && hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
            hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
            hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> throw IllegalStateException("No camera available")
        }

        val previewUseCase = Preview.Builder()
            .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
            .build()
        surfaceProvider?.let { previewUseCase.setSurfaceProvider(it) }

        val hevcSupported = runCatching {
            Recorder.getSupportedVideoMimeTypes().contains(MediaFormat.MIMETYPE_VIDEO_HEVC)
        }.getOrDefault(false)
        hevcActive = settings.hevcEnabled && hevcSupported

        val recorderBuilder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    settings.quality.quality,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )
            )
            .setTargetVideoEncodingBitRate(settings.quality.bitrate)
        if (hevcActive) {
            recorderBuilder
                .setVideoMimeType(MediaFormat.MIMETYPE_VIDEO_HEVC)
                .setVideoCapabilitiesSource(Recorder.VIDEO_CAPABILITIES_SOURCE_CODEC_CAPABILITIES)
        }

        val capture = VideoCapture.withOutput(recorderBuilder.build())
        provider.unbindAll()
        provider.bindToLifecycle(this, selector, previewUseCase, capture)

        preview = previewUseCase
        videoCapture = capture
        updateState {
            it.copy(
                cameraReady = true,
                lensFacing = lensFacing,
                canSwitchCamera = canSwitchCamera,
                hevcActive = hevcActive,
                status = UiText(R.string.status_ready)
            )
        }
    }

    private fun updateShockMonitoring() {
        if (recordingRequested || settings.eventRecordingEnabled) {
            startShockMonitoring()
        } else {
            stopShockMonitoring()
        }
    }

    private fun startShockMonitoring() {
        stopShockMonitoring()
        if (!settings.gSensorEnabled && !settings.eventRecordingEnabled) return
        val manager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val linearSensor = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val sensor = linearSensor ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager = manager
        shockSensor = sensor
        shockDetector = ShockDetector(
            thresholdG = settings.gSensorSensitivity.thresholdG,
            removeGravity = linearSensor == null
        ) { forceG -> onShockDetected(forceG) }
        manager.registerListener(shockListener, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun stopShockMonitoring() {
        sensorManager?.unregisterListener(shockListener)
        sensorManager = null
        shockSensor = null
        shockDetector = null
    }

    private fun onShockDetected(forceG: Float) {
        if (recordingRequested) {
            if (isCurrentShockProtected) return
            val file = currentFile ?: return
            isCurrentProtected = true
            isCurrentShockProtected = true
            repository.addShockProtected(file.name)
            refreshClips()
            updateState {
                it.copy(
                    currentProtected = true,
                    status = UiText(R.string.status_shock_protected, forceG)
                )
            }
        } else if (settings.eventRecordingEnabled) {
            startEventRecording(forceG)
        }
    }

    private fun startEventRecording(forceG: Float) {
        if (recordingRequested) return
        eventRecordingActive = true
        startRecording()
        if (!recordingRequested) {
            eventRecordingActive = false
            return
        }
        val file = currentFile
        if (file != null) {
            isCurrentProtected = true
            isCurrentShockProtected = true
            repository.addShockProtected(file.name)
            refreshClips()
            updateState {
                it.copy(
                    currentProtected = true,
                    status = UiText(R.string.status_event_recording, forceG)
                )
            }
        }
        mainHandler.removeCallbacks(eventStopTimer)
        mainHandler.postDelayed(eventStopTimer, EVENT_RECORDING_MS)
    }

    private fun finishEventRecording() {
        if (!eventRecordingActive) return
        eventRecordingActive = false
        stopRecording()
    }

    private fun refreshClips() {
        updateState { it.copy(clips = repository.listClips()) }
    }

    private fun pruneClips() {
        repository.prune(settings, currentFile)
        refreshClips()
    }

    private fun recordingsDirectory(): File = File(
        getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir,
        "RoadCam"
    ).apply { mkdirs() }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun updateState(transform: (DashcamUiState) -> DashcamUiState) {
        _state.value = transform(_state.value)
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RoadCam:Recording"
        ).apply {
            setReferenceCounted(false)
            acquire(12L * 60L * 60L * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP_RECORDING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val recording = _state.value.recording
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(
                getString(
                    if (recording) R.string.notification_text_recording
                    else R.string.notification_text_ready
                )
            )
            .setOngoing(recording)
            .setContentIntent(openIntent)
            .apply {
                if (recording) {
                    addAction(0, getString(R.string.notification_action_stop), stopIntent)
                }
            }
            .build()
    }

    private fun startForegroundWithType() {
        val audioActive = settings.audioEnabled && hasAudioPermission()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            var value = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            if (audioActive) value = value or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            value
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }
}
