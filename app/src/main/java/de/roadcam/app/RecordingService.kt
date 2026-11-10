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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Display
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.effects.Frame
import androidx.camera.effects.OverlayEffect
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
import java.util.concurrent.atomic.AtomicBoolean

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
        private const val STATUS_RESTORE_MS = 2_500L
        private const val TAG = "RecordingService"

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
    private val snapshotExecutor = Executors.newSingleThreadExecutor()
    private val _state = MutableStateFlow(DashcamUiState())
    val state: StateFlow<DashcamUiState> = _state.asStateFlow()

    private lateinit var settingsStore: SettingsStore
    private lateinit var repository: ClipRepository
    private var settings = DashcamSettings()

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageCapture: ImageCapture? = null
    private val snapshotInProgress = AtomicBoolean(false)
    private var statusBeforeTransient: UiText? = null
    private val restoreStatus = Runnable {
        val status = statusBeforeTransient
        statusBeforeTransient = null
        if (status != null) updateState { it.copy(status = status) }
    }

    /**
     * Sets a permanent status. Any pending restoration of a transient status
     * (e.g. "Foto gespeichert") is cancelled so it cannot overwrite newer
     * information.
     */
    private fun setStatus(text: UiText) {
        statusBeforeTransient = null
        mainHandler.removeCallbacks(restoreStatus)
        updateState { it.copy(status = text) }
    }

    /**
     * Shows a short-lived status (e.g. snapshot feedback) and restores the
     * previous status afterwards, so messages like "Foto gespeichert" never
     * permanently hide the recording state.
     */
    private fun showTransientStatus(text: UiText) {
        if (statusBeforeTransient == null) statusBeforeTransient = _state.value.status
        mainHandler.removeCallbacks(restoreStatus)
        updateState { it.copy(status = text) }
        mainHandler.postDelayed(restoreStatus, STATUS_RESTORE_MS)
    }

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

    private var overlayEffect: OverlayEffect? = null
    private val speedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        color = Color.WHITE
    }
    private val speedBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 0, 0, 0)
    }

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var locationProvider: String? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var cameraRotation = Surface.ROTATION_0
    private var restartRecordingAfterRebind = false
    private val rebindRunnable = Runnable { bindCamera() }
    @Volatile
    private var speedKmh: Float? = null

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
        cleanupStaleSnapshots()
        updateState { it.copy(settings = settings) }
        setStatus(UiText(R.string.status_preparing))
        bindCamera()
        updateShockMonitoring()
        updateLocationUpdates()
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
        mainHandler.removeCallbacks(restoreStatus)
        activeRecording?.stop()
        stopShockMonitoring()
        stopLocationUpdates()
        unregisterDisplayListener()
        releaseWakeLock()
        cameraProvider?.unbindAll()
        overlayEffect?.close()
        overlayEffect = null
        imageCapture = null
        overlapExecutor.shutdown()
        snapshotExecutor.shutdown()
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
        if (settings.audioEnabled != previous.audioEnabled ||
            settings.gpsSpeedEnabled != previous.gpsSpeedEnabled
        ) {
            startForegroundWithType()
        }
        val needsCameraRebind = !recordingRequested && (
            settings.quality != previous.quality ||
                settings.hevcEnabled != previous.hevcEnabled ||
                settings.gpsSpeedEnabled != previous.gpsSpeedEnabled
            )
        if (needsCameraRebind) {
            bindCamera()
        }
        updateLocationUpdates()
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
            setStatus(UiText(R.string.status_camera_not_ready))
            return
        }
        recordingRequested = true
        previousSegment = null
        acquireWakeLock()
        updateShockMonitoring()
        updateState { it.copy(recording = true) }
        setStatus(UiText(R.string.status_recording, settings.clipMinutes))
        updateNotification()
        startSegment()
    }

    fun stopRecording() {
        restartRecordingAfterRebind = false
        recordingRequested = false
        eventRecordingActive = false
        mainHandler.removeCallbacks(segmentTimer)
        mainHandler.removeCallbacks(eventStopTimer)
        updateShockMonitoring()
        releaseWakeLock()
        val recording = activeRecording
        if (recording == null) {
            updateState { it.copy(recording = false, currentProtected = false) }
            setStatus(UiText(R.string.status_stopped))
            updateNotification()
            if (currentDisplayRotation() != cameraRotation) scheduleCameraRebind()
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
                setStatus(UiText(R.string.status_no_clip))
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
            DeleteResult.PROTECTED -> setStatus(UiText(R.string.status_clip_protected))
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

    /**
     * Captures a photo next to the video clips. The photo is taken with a
     * dedicated ImageCapture use case at the highest resolution the camera
     * supports for the video's 16:9 aspect ratio, so it never interrupts an
     * active recording.
     */
    fun takeSnapshot() {
        val capture = imageCapture
        if (capture == null) {
            showTransientStatus(UiText(R.string.status_snapshot_failed))
            return
        }
        if (!snapshotInProgress.compareAndSet(false, true)) return
        val directory = recordingsDirectory()
        if (directory.usableSpace < DashcamSettings.PHOTO_SPACE_RESERVE_BYTES) {
            snapshotInProgress.set(false)
            showTransientStatus(UiText(R.string.status_low_storage))
            return
        }
        val staging = snapshotsDirectory()
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US).format(Date())
        var candidate = File(staging, "AirRoadCam_Photo_$stamp.jpg")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(staging, "AirRoadCam_Photo_${stamp}_$suffix.jpg")
            suffix++
        }
        val options = ImageCapture.OutputFileOptions.Builder(candidate).build()
        try {
            capture.takePicture(
                options,
                snapshotExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        snapshotInProgress.set(false)
                        val saved = publishSnapshot(candidate, directory, stamp)
                        mainHandler.post {
                            if (saved) {
                                pruneClips()
                                showTransientStatus(UiText(R.string.status_snapshot_saved))
                            } else {
                                showTransientStatus(UiText(R.string.status_snapshot_failed))
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        snapshotInProgress.set(false)
                        candidate.delete()
                        Log.w(TAG, "Snapshot failed", exception)
                        mainHandler.post {
                            showTransientStatus(UiText(R.string.status_snapshot_failed))
                        }
                    }
                }
            )
        } catch (error: Exception) {
            snapshotInProgress.set(false)
            candidate.delete()
            Log.w(TAG, "Snapshot could not be started", error)
            showTransientStatus(UiText(R.string.status_snapshot_failed))
        }
    }

    private fun publishSnapshot(staged: File, directory: File, stamp: String): Boolean {
        var target = File(directory, "AirRoadCam_Photo_$stamp.jpg")
        var suffix = 1
        while (target.exists()) {
            target = File(directory, "AirRoadCam_Photo_${stamp}_$suffix.jpg")
            suffix++
        }
        return try {
            if (staged.renameTo(target)) {
                true
            } else {
                staged.copyTo(target, overwrite = false)
                staged.delete()
                true
            }
        } catch (error: Exception) {
            Log.w(TAG, "Snapshot could not be published", error)
            staged.delete()
            target.delete()
            false
        }
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
            setStatus(UiText(R.string.status_camera_unavailable))
            return
        }
        pruneClips()
        val directory = recordingsDirectory()
        if (directory.usableSpace < DashcamSettings.FREE_SPACE_RESERVE_BYTES) {
            stopRecording()
            setStatus(UiText(R.string.status_low_storage))
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
                setStatus(UiText(R.string.status_recording_error))
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
            updateState { it.copy(recording = false, currentProtected = false) }
            setStatus(UiText(R.string.status_saved))
            updateNotification()
            if (currentDisplayRotation() != cameraRotation) scheduleCameraRebind()
        }
    }

    private fun nextFile(directory: File): File {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        var candidate = File(directory, "AirRoadCam_$stamp.mp4")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(directory, "AirRoadCam_${stamp}_$suffix.mp4")
            suffix++
        }
        return candidate
    }

    private fun bindCamera() {
        if (recordingRequested) return
        if (!hasCameraPermission()) {
            updateState { it.copy(cameraReady = false) }
            setStatus(UiText(R.string.status_permission_required))
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
                setStatus(UiText(R.string.status_camera_error))
                return@addListener
            }
            cameraProvider = provider
            try {
                bindUseCases(provider)
            } catch (_: Exception) {
                preview = null
                videoCapture = null
                imageCapture = null
                overlayEffect?.close()
                overlayEffect = null
                updateState { it.copy(cameraReady = false, snapshotAvailable = false) }
                setStatus(UiText(R.string.status_camera_error))
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
        val displayRotation = currentDisplayRotation()
        displayRotation?.let { previewUseCase.targetRotation = it }
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
        displayRotation?.let { capture.targetRotation = it }

        val photoCapture = ImageCapture.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build()
            )
            .apply { displayRotation?.let { setTargetRotation(it) } }
            .build()

        val previousEffect = overlayEffect
        overlayEffect = null
        previousEffect?.close()

        fun createEffect(targets: Int): OverlayEffect? {
            if (!settings.gpsSpeedEnabled) return null
            return OverlayEffect(targets, 0, mainHandler) { error ->
                Log.e(TAG, "Speed overlay failed", error)
            }.also { effect ->
                effect.setOnDrawListener { frame ->
                    drawSpeedOverlay(frame)
                    true
                }
            }
        }

        fun buildGroup(includePhoto: Boolean): Pair<UseCaseGroup, OverlayEffect?> {
            val builder = UseCaseGroup.Builder()
                .addUseCase(previewUseCase)
                .addUseCase(capture)
            if (includePhoto) builder.addUseCase(photoCapture)
            var targets = CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE
            if (includePhoto) targets = targets or CameraEffect.IMAGE_CAPTURE
            val effect = createEffect(targets)
            effect?.let { builder.addEffect(it) }
            return builder.build() to effect
        }

        var boundPhoto = true
        var activeEffect: OverlayEffect? = null
        try {
            val (group, effect) = buildGroup(includePhoto = true)
            activeEffect = effect
            provider.unbindAll()
            provider.bindToLifecycle(this, selector, group)
        } catch (photoError: Exception) {
            Log.w(TAG, "Camera bind with ImageCapture failed, retrying without snapshots", photoError)
            activeEffect?.close()
            activeEffect = null
            boundPhoto = false
            val (group, effect) = buildGroup(includePhoto = false)
            activeEffect = effect
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, group)
            } catch (fallbackError: Exception) {
                activeEffect?.close()
                activeEffect = null
                throw fallbackError
            }
        }
        overlayEffect = activeEffect

        preview = previewUseCase
        videoCapture = capture
        imageCapture = if (boundPhoto) photoCapture else null
        registerDisplayListener()
        cameraRotation = displayRotation ?: Surface.ROTATION_0
        updateState {
            it.copy(
                cameraReady = true,
                lensFacing = lensFacing,
                canSwitchCamera = canSwitchCamera,
                hevcActive = hevcActive,
                snapshotAvailable = boundPhoto
            )
        }
        setStatus(UiText(R.string.status_ready))
        if (restartRecordingAfterRebind && !recordingRequested) {
            restartRecordingAfterRebind = false
            startRecording()
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

    private fun drawSpeedOverlay(frame: Frame) {
        val speed = speedKmh
        val text = if (speed != null) {
            getString(R.string.speed_overlay_kmh, speed)
        } else {
            getString(R.string.speed_no_fix)
        }

        val canvas: Canvas = frame.overlayCanvas
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val crop = frame.cropRect
        val rotation = frame.rotationDegrees
        val rotated = rotation == 90 || rotation == 270
        val displayWidth = if (rotated) crop.height() else crop.width()
        val displayHeight = if (rotated) crop.width() else crop.height()
        if (displayWidth <= 0 || displayHeight <= 0) return

        canvas.save()
        when (rotation) {
            90 -> {
                canvas.translate(crop.left.toFloat(), (crop.top + crop.height()).toFloat())
                canvas.rotate(-90f)
            }
            180 -> {
                canvas.translate(
                    (crop.left + crop.width()).toFloat(),
                    (crop.top + crop.height()).toFloat()
                )
                canvas.rotate(180f)
            }
            270 -> {
                canvas.translate((crop.left + crop.width()).toFloat(), crop.top.toFloat())
                canvas.rotate(90f)
            }
            else -> canvas.translate(crop.left.toFloat(), crop.top.toFloat())
        }

        val textSize = (displayHeight * 0.045f).coerceAtLeast(24f)
        speedTextPaint.textSize = textSize
        speedTextPaint.setShadowLayer(textSize * 0.10f, 0f, 0f, Color.BLACK)

        val margin = (displayWidth * 0.035f).coerceAtLeast(16f)
        val x = margin
        val baseline = displayHeight - margin - speedTextPaint.descent()
        val pad = textSize * 0.30f
        val textWidth = speedTextPaint.measureText(text)
        val background = RectF(
            x - pad,
            baseline + speedTextPaint.ascent() - pad,
            x + textWidth + pad,
            baseline + speedTextPaint.descent() + pad
        )
        canvas.drawRoundRect(background, pad, pad, speedBackgroundPaint)
        canvas.drawText(text, x, baseline, speedTextPaint)
        canvas.restore()
    }

    private fun updateLocationUpdates() {
        if (!settings.gpsSpeedEnabled || !hasLocationPermission()) {
            stopLocationUpdates()
            return
        }
        if (locationListener != null) return
        startForegroundWithType()
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        stopLocationUpdates()
        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val provider = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                manager.isProviderEnabled(LocationManager.FUSED_PROVIDER) ->
                LocationManager.FUSED_PROVIDER
            fineGranted && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            else -> return
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                setSpeed(
                    if (location.hasSpeed()) {
                        (location.speed * 3.6f).coerceAtLeast(0f)
                    } else {
                        null
                    }
                )
            }

            @Suppress("DEPRECATION")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) {
                if (provider == locationProvider) setSpeed(null)
            }
        }
        try {
            manager.requestLocationUpdates(provider, 1_000L, 0f, listener, Looper.getMainLooper())
            manager.getLastKnownLocation(provider)?.let { location ->
                if (location.hasSpeed() && System.currentTimeMillis() - location.time < 30_000L) {
                    setSpeed((location.speed * 3.6f).coerceAtLeast(0f))
                }
            }
            locationManager = manager
            locationListener = listener
            locationProvider = provider
        } catch (_: SecurityException) {
            setSpeed(null)
        }
    }

    private fun setSpeed(value: Float?) {
        speedKmh = value
        updateState { it.copy(speedKmh = value) }
    }

    /**
     * Keeps the camera session in sync with the display rotation. The preview
     * surface, the GPS speed overlay pipeline and the video orientation are
     * all negotiated at bind time, so a device rotation requires a rebind.
     * While a regular recording is active, the recording is stopped and
     * resumed once the camera has been rebound with the new orientation;
     * event recordings are never interrupted. Recordings started after a
     * rotation change pick up the new orientation immediately.
     */
    private fun registerDisplayListener() {
        val manager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
        if (displayListener == null) {
            val listener = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = Unit

                override fun onDisplayRemoved(displayId: Int) = Unit

                override fun onDisplayChanged(displayId: Int) {
                    if (displayId != Display.DEFAULT_DISPLAY) return
                    val rotation = manager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: return
                    videoCapture?.targetRotation = rotation
                    imageCapture?.targetRotation = rotation
                    if (rotation == cameraRotation) return
                    when {
                        eventRecordingActive -> Unit
                        recordingRequested -> {
                            stopRecording()
                            restartRecordingAfterRebind = true
                        }
                        activeRecording == null -> scheduleCameraRebind()
                    }
                }
            }
            manager.registerDisplayListener(listener, mainHandler)
            displayListener = listener
        }
        currentDisplayRotation()?.let { rotation ->
            videoCapture?.targetRotation = rotation
            imageCapture?.targetRotation = rotation
        }
    }

    private fun unregisterDisplayListener() {
        displayListener?.let { listener ->
            (getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
                ?.unregisterDisplayListener(listener)
        }
        displayListener = null
        mainHandler.removeCallbacks(rebindRunnable)
    }

    private fun currentDisplayRotation(): Int? =
        (getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation

    private fun scheduleCameraRebind() {
        mainHandler.removeCallbacks(rebindRunnable)
        mainHandler.postDelayed(rebindRunnable, 300L)
    }

    private fun stopLocationUpdates() {
        locationListener?.let { listener ->
            runCatching { locationManager?.removeUpdates(listener) }
        }
        locationManager = null
        locationListener = null
        locationProvider = null
        setSpeed(null)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun onShockDetected(forceG: Float) {
        if (recordingRequested) {
            if (isCurrentShockProtected) return
            val file = currentFile ?: return
            isCurrentProtected = true
            isCurrentShockProtected = true
            repository.addShockProtected(file.name)
            refreshClips()
            updateState { it.copy(currentProtected = true) }
            setStatus(UiText(R.string.status_shock_protected, forceG))
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
            updateState { it.copy(currentProtected = true) }
            setStatus(UiText(R.string.status_event_recording, forceG))
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

    private fun snapshotsDirectory(): File =
        File(recordingsDirectory(), ".snapshots").apply { mkdirs() }

    private fun cleanupStaleSnapshots() {
        val cutoff = System.currentTimeMillis() - 60L * 60L * 1000L
        snapshotsDirectory().listFiles()
            .orEmpty()
            .filter { it.isFile && it.lastModified() < cutoff }
            .forEach { it.delete() }
    }

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
            "AirRoadCam:Recording"
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
            if (settings.gpsSpeedEnabled && hasLocationPermission()) {
                value = value or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
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
