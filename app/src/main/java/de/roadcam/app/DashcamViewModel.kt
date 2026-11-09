package de.roadcam.app

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.camera.core.Preview
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DashcamViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = SettingsStore(application)
    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<DashcamSettings> = _settings.asStateFlow()

    private val _service = MutableStateFlow<RecordingService?>(null)
    private var previewProvider: Preview.SurfaceProvider? = null
    private var bound = false
    private var autoStartConsumed = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? RecordingService.LocalBinder)?.service() ?: return
            _service.value = service
            service.updateSettings(_settings.value)
            previewProvider?.let(service::setPreviewSurfaceProvider)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
        }
    }

    private val serviceState = _service.flatMapLatest { service ->
        service?.state ?: flowOf(DashcamUiState())
    }

    val uiState: StateFlow<DashcamUiState> = combine(serviceState, _settings) { state, settings ->
        state.copy(settings = settings)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DashcamUiState(settings = _settings.value)
    )

    init {
        viewModelScope.launch {
            uiState.collect { maybeAutoStart() }
        }
    }

    fun onForeground() {
        autoStartConsumed = false
        maybeAutoStart()
    }

    fun onPowerConnected() {
        autoStartConsumed = false
        maybeAutoStart()
    }

    private fun maybeAutoStart() {
        if (autoStartConsumed) return
        val state = uiState.value
        if (!state.settings.autoStartRecording || !state.cameraReady || state.recording) return
        val service = _service.value ?: return
        autoStartConsumed = true
        service.startRecording()
    }

    fun connect() {
        if (bound) return
        val context = getApplication<Application>()
        runCatching { RecordingService.start(context) }
        val intent = Intent(context, RecordingService::class.java)
        bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun disconnect() {
        if (!bound) return
        runCatching { getApplication<Application>().unbindService(connection) }
        bound = false
        _service.value = null
    }

    fun shutdown() {
        _service.value?.shutdown()
        disconnect()
    }

    fun setPreviewSurfaceProvider(provider: Preview.SurfaceProvider?) {
        previewProvider = provider
        _service.value?.setPreviewSurfaceProvider(provider)
    }

    fun applySettings(settings: DashcamSettings) {
        _settings.value = settings
        settingsStore.save(settings)
        _service.value?.updateSettings(settings)
    }

    fun refreshSettings() {
        _service.value?.updateSettings(_settings.value)
    }

    fun startRecording() = _service.value?.startRecording() ?: Unit
    fun stopRecording() = _service.value?.stopRecording() ?: Unit
    fun toggleRecording() {
        val service = _service.value ?: return
        if (uiState.value.recording) service.stopRecording() else service.startRecording()
    }

    fun toggleCurrentProtection() = _service.value?.toggleCurrentProtection() ?: Unit
    fun toggleClipProtection(clip: DashClip) = _service.value?.toggleClipProtection(clip) ?: Unit
    fun deleteClip(clip: DashClip) = _service.value?.deleteClip(clip) ?: Unit
    fun switchCamera() = _service.value?.switchCamera() ?: Unit

    fun playClip(clip: DashClip) {
        val context = getApplication<Application>()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", clip.file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun shareClip(clip: DashClip) {
        val context = getApplication<Application>()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", clip.file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareClips(clips: List<DashClip>) {
        if (clips.isEmpty()) return
        val context = getApplication<Application>()
        val uris = ArrayList<Uri>(clips.map {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it.file)
        })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "video/mp4"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
