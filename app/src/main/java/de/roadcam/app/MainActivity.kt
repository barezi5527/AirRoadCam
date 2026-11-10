package de.roadcam.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.Formatter
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val Panel: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surface

private val Elevated: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surfaceVariant

private val OnPanel: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurface

private val Muted: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

private val Mint: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.primary

private val Alert: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.error

class MainActivity : ComponentActivity() {
    private val viewModel: DashcamViewModel by viewModels()
    private val landscapeLocked = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.d(
            "RoadCamOrientation",
            "onCreate this=${Integer.toHexString(System.identityHashCode(this))} requested=$requestedOrientation locked=${landscapeLocked.value} state=${savedInstanceState != null}"
        )
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            RoadCamTheme(darkTheme = state.settings.darkTheme) {
                val context = LocalContext.current
                var hasCameraPermission by remember {
                    mutableStateOf(hasPermission(context, Manifest.permission.CAMERA))
                }
                var hasAudioPermission by remember {
                    mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO))
                }
                var hasNotificationPermission by remember {
                    mutableStateOf(notificationPermissionGranted(context))
                }
                var hasLocationPermission by remember {
                    mutableStateOf(locationPermissionGranted(context))
                }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    hasCameraPermission = result[Manifest.permission.CAMERA] == true
                    hasNotificationPermission = notificationPermissionGranted(context)
                }
                val audioLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> hasAudioPermission = granted }
                val notificationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> hasNotificationPermission = granted }
                val locationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    hasLocationPermission = locationPermissionGranted(context)
                    viewModel.refreshSettings()
                }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            hasCameraPermission = hasPermission(context, Manifest.permission.CAMERA)
                            hasAudioPermission = hasPermission(context, Manifest.permission.RECORD_AUDIO)
                            hasNotificationPermission = notificationPermissionGranted(context)
                            hasLocationPermission = locationPermissionGranted(context)
                            viewModel.refreshSettings()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LaunchedEffect(Unit) {
                    if (!hasCameraPermission) {
                        val requests = mutableListOf(Manifest.permission.CAMERA)
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            requests += Manifest.permission.POST_NOTIFICATIONS
                        }
                        permissionLauncher.launch(requests.toTypedArray())
                    }
                }
                LaunchedEffect(hasCameraPermission) {
                    if (hasCameraPermission) {
                        viewModel.connect()
                    } else {
                        viewModel.disconnect()
                    }
                }

                val view = LocalView.current
                LaunchedEffect(state.settings.screenTimeoutMinutes) {
                    val timeoutMinutes = state.settings.screenTimeoutMinutes
                    view.keepScreenOn = true
                    if (timeoutMinutes > 0) {
                        delay(timeoutMinutes * 60_000L)
                        view.keepScreenOn = false
                    }
                }
                DisposableEffect(Unit) {
                    onDispose { view.keepScreenOn = false }
                }

                LaunchedEffect(Unit) { applyOrientation() }

                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(receiverContext: Context?, intent: Intent?) {
                            if (intent?.action == Intent.ACTION_POWER_CONNECTED) {
                                viewModel.onPowerConnected()
                            }
                        }
                    }
                    ContextCompat.registerReceiver(
                        context,
                        receiver,
                        IntentFilter(Intent.ACTION_POWER_CONNECTED),
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                    onDispose { context.unregisterReceiver(receiver) }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(Modifier.fillMaxSize()) {
                        DashcamHome(
                            state = state,
                            hasCameraPermission = hasCameraPermission,
                            hasAudioPermission = hasAudioPermission,
                            hasNotificationPermission = hasNotificationPermission,
                            hasLocationPermission = hasLocationPermission,
                            landscapeLocked = landscapeLocked.value,
                            onToggleLandscapeLock = ::toggleLandscapeLock,
                            onRequestCameraPermission = {
                                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                            },
                            onRequestAudioPermission = {
                                audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onRequestNotificationPermission = {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            onRequestLocationPermission = {
                                locationLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            onPreviewSurface = viewModel::setPreviewSurfaceProvider,
                            onApplyMode = viewModel::applySettings,
                            onSwitchCamera = viewModel::switchCamera,
                            onToggleRecording = viewModel::toggleRecording,
                            onTakeSnapshot = viewModel::takeSnapshot,
                            onProtectCurrent = viewModel::toggleCurrentProtection,
                            onToggleClipProtection = viewModel::toggleClipProtection,
                            onDeleteClip = viewModel::deleteClip,
                            onPlayClip = viewModel::playClip,
                            onShareClip = viewModel::shareClip,
                            onShareAllClips = { viewModel.shareClips(state.clips) }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    private fun applyOrientation() {
        requestedOrientation = if (landscapeLocked.value) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
        android.util.Log.d("RoadCamOrientation", "apply locked=${landscapeLocked.value} requested=$requestedOrientation")
    }

    private fun toggleLandscapeLock() {
        landscapeLocked.value = !landscapeLocked.value
        android.util.Log.d("RoadCamOrientation", "toggle locked=${landscapeLocked.value}")
        applyOrientation()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        android.util.Log.d("RoadCamOrientation", "onConfigurationChanged this=${Integer.toHexString(System.identityHashCode(this))} orientation=${newConfig.orientation}")
        super.onConfigurationChanged(newConfig)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        android.util.Log.d("RoadCamOrientation", "onSaveInstanceState this=${Integer.toHexString(System.identityHashCode(this))} locked=${landscapeLocked.value}")
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        android.util.Log.d("RoadCamOrientation", "onDestroy this=${Integer.toHexString(System.identityHashCode(this))} finishing=$isFinishing")
        if (isFinishing) viewModel.shutdown() else viewModel.disconnect()
        super.onDestroy()
    }
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun notificationPermissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 || hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)

private fun locationPermissionGranted(context: Context): Boolean =
    hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
        hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

@Composable
private fun qualityLabel(quality: RecordQuality): String = stringResource(
    when (quality) {
        RecordQuality.SPAREND -> R.string.quality_sparsam
        RecordQuality.AUSGEWOGEN -> R.string.quality_ausgewogen
        RecordQuality.HOCH -> R.string.quality_hoch
    }
)

@Composable
private fun gSensorBaseName(sensitivity: GSensorSensitivity): String = stringResource(
    R.string.gsensor_level,
    stringResource(
        when (sensitivity) {
            GSensorSensitivity.NIEDRIG_1, GSensorSensitivity.NIEDRIG_2 -> R.string.gsensor_low
            GSensorSensitivity.MITTEL_1, GSensorSensitivity.MITTEL_2 -> R.string.gsensor_medium
            GSensorSensitivity.HOCH_1, GSensorSensitivity.HOCH_2 -> R.string.gsensor_high
        }
    ),
    sensitivity.level
)

@Composable
private fun gSensorLabel(sensitivity: GSensorSensitivity): String = stringResource(
    R.string.gsensor_chip,
    gSensorBaseName(sensitivity),
    sensitivity.thresholdG
)

@Composable
private fun DashcamHome(
    state: DashcamUiState,
    hasCameraPermission: Boolean,
    hasAudioPermission: Boolean,
    hasNotificationPermission: Boolean,
    hasLocationPermission: Boolean,
    landscapeLocked: Boolean,
    onToggleLandscapeLock: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onPreviewSurface: (Preview.SurfaceProvider?) -> Unit,
    onApplyMode: (DashcamSettings) -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleRecording: () -> Unit,
    onTakeSnapshot: () -> Unit,
    onProtectCurrent: () -> Unit,
    onToggleClipProtection: (DashClip) -> Unit,
    onDeleteClip: (DashClip) -> Unit,
    onPlayClip: (DashClip) -> Unit,
    onShareClip: (DashClip) -> Unit,
    onShareAllClips: () -> Unit
) {
    val context = LocalContext.current
    val recording = state.recording
    val settings = state.settings
    var clipToDelete by remember { mutableStateOf<DashClip?>(null) }
    var showModeDialog by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showPermissions by remember { mutableStateOf(false) }
    var showSensorTest by remember { mutableStateOf(false) }
    var showGSensorInfo by remember { mutableStateOf(false) }
    var showClips by remember { mutableStateOf(false) }

    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        DashcamLandscape(
            state = state,
            hasCameraPermission = hasCameraPermission,
            hasLocationPermission = hasLocationPermission,
            landscapeLocked = landscapeLocked,
            onToggleLandscapeLock = onToggleLandscapeLock,
            onRequestCameraPermission = onRequestCameraPermission,
            onPreviewSurface = onPreviewSurface,
            onApplyMode = onApplyMode,
            onSwitchCamera = onSwitchCamera,
            onToggleRecording = onToggleRecording,
            onTakeSnapshot = onTakeSnapshot,
            onProtectCurrent = onProtectCurrent,
            onOpenSettings = { showModeDialog = true },
            onOpenClips = { showClips = true },
            onOpenMenu = { showMenu = true }
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                        .background(Mint.copy(alpha = .14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Videocam, contentDescription = null, tint = Mint, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    AutoFitText(
                        text = stringResource(R.string.header_title),
                        color = OnPanel,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.4.sp
                    )
                    AutoFitText(
                        text = stringResource(R.string.header_subtitle),
                        color = Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 1.7.sp
                    )
                }
                IconButton(
                    onClick = onToggleLandscapeLock,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.ScreenRotation,
                        contentDescription = stringResource(R.string.landscape_toggle),
                        tint = if (landscapeLocked) Mint else Muted,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu_open), tint = Muted, modifier = Modifier.size(22.dp))
                }
                IconButton(
                    onClick = { onApplyMode(settings.copy(darkTheme = !settings.darkTheme)) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (settings.darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = stringResource(
                            if (settings.darkTheme) R.string.theme_switch_to_light else R.string.theme_switch_to_dark
                        ),
                        tint = Muted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(25.dp),
                colors = CardDefaults.cardColors(containerColor = Panel)
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1.48f)) {
                    if (hasCameraPermission) {
                        CameraPreview(onPreviewSurface, Modifier.fillMaxSize())
                    } else {
                        Column(
                            Modifier.fillMaxSize().background(Elevated),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.Videocam, contentDescription = null, tint = Muted, modifier = Modifier.size(38.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(stringResource(R.string.camera_permission_required), color = OnPanel, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = onRequestCameraPermission) {
                                Text(stringResource(R.string.camera_permission_grant), color = Mint)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, color = Color.Black.copy(alpha = .58f)) {
                            Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (recording) Box(Modifier.size(7.dp).clip(CircleShape).background(Alert))
                                else Icon(Icons.Filled.CheckCircle, null, tint = Mint, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(if (recording) R.string.camera_recording else R.string.camera_active),
                                    color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp
                                )
                            }
                        }
                    }
                    if (hasCameraPermission && state.canSwitchCamera) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                                .clickable(enabled = !recording, onClick = onSwitchCamera),
                            shape = CircleShape, color = Color.Black.copy(alpha = .58f)
                        ) {
                            Box(Modifier.padding(8.dp)) {
                                Icon(Icons.Filled.Cameraswitch, contentDescription = stringResource(R.string.camera_switch), tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        shape = RoundedCornerShape(9.dp), color = Color.Black.copy(alpha = .58f)
                    ) {
                        val front = if (state.lensFacing == CameraSelector.LENS_FACING_FRONT) {
                            stringResource(R.string.camera_badge_front) + "  ·  "
                        } else ""
                        val codec = if (state.hevcActive) "HEVC" else "H.264"
                        Text(
                            front + "${settings.quality.badge}  ·  30 FPS  ·  $codec",
                            Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onToggleRecording,
                    enabled = state.cameraReady,
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (recording) MaterialTheme.colorScheme.errorContainer else Mint,
                        contentColor = if (recording) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = Elevated
                    )
                ) {
                    if (!state.cameraReady && hasCameraPermission) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Mint)
                    } else {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(if (recording) Alert else MaterialTheme.colorScheme.onPrimary))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(if (recording) R.string.recording_stop else R.string.recording_start),
                        fontWeight = FontWeight.ExtraBold, letterSpacing = .4.sp
                    )
                }
                Surface(
                    modifier = Modifier.size(58.dp).clickable(onClick = onProtectCurrent),
                    shape = RoundedCornerShape(18.dp),
                    color = if (state.currentProtected) Mint.copy(alpha = .16f) else Panel
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = stringResource(R.string.protect_current),
                            tint = if (state.currentProtected) Mint else OnPanel,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    stringResource(R.string.mode_card_title),
                    stringResource(R.string.mode_card_value),
                    stringResource(R.string.mode_card_summary, settings.clipMinutes, qualityLabel(settings.quality)),
                    Modifier.weight(1f).fillMaxHeight(),
                    onClick = { showModeDialog = true },
                    enabled = !recording
                )
                SnapshotCard(
                    Modifier.weight(1f).fillMaxHeight(),
                    enabled = state.cameraReady && state.snapshotAvailable,
                    onClick = onTakeSnapshot
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.clips_title), color = OnPanel, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.clips_subtitle), color = Muted, fontSize = 12.sp)
                }
                val used = state.clips.sumOf { it.file.length() }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.storage_card_title), color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                    Text(
                        Formatter.formatShortFileSize(context, used) + "  ·  " +
                            stringResource(R.string.storage_card_clips, state.clips.size),
                        color = OnPanel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(14.dp))
                Icon(
                    Icons.Filled.Share, contentDescription = stringResource(R.string.clips_share_all),
                    tint = if (state.clips.isEmpty()) Muted.copy(alpha = .4f) else Muted,
                    modifier = Modifier.size(20.dp).clickable(enabled = state.clips.isNotEmpty(), onClick = onShareAllClips)
                )
            }
        }

        if (state.clips.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Videocam, null, tint = Muted)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.clips_empty_title), color = OnPanel, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.clips_empty_body), color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            items(state.clips.take(8), key = { it.file.name }) { clip ->
                ClipRow(
                    clip = clip,
                    onToggleProtection = onToggleClipProtection,
                    onDelete = { clipToDelete = it },
                    onPlay = onPlayClip,
                    onShare = onShareClip
                )
            }
        }

        item {
            Text(stringResource(R.string.footer_note), color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
    }

    clipToDelete?.let { clip ->
        val isProtected = clip.protected
        AlertDialog(
            onDismissRequest = { clipToDelete = null },
            containerColor = Panel,
            titleContentColor = OnPanel,
            textContentColor = Muted,
            title = { Text(stringResource(if (isProtected) R.string.dialog_protected_title else R.string.dialog_delete_title)) },
            text = {
                Text(stringResource(if (isProtected) R.string.dialog_protected_body else R.string.dialog_delete_body))
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isProtected) onToggleClipProtection(clip) else onDeleteClip(clip)
                    clipToDelete = null
                }) {
                    Text(
                        stringResource(if (isProtected) R.string.dialog_protected_confirm else R.string.dialog_delete_confirm),
                        color = if (isProtected) Mint else Alert
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { clipToDelete = null }) { Text(stringResource(R.string.dialog_cancel), color = Muted) }
            }
        )
    }

    if (showModeDialog) {
        var draft by remember { mutableStateOf(settings) }
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            containerColor = Panel,
            titleContentColor = OnPanel,
            textContentColor = Muted,
            title = { Text(stringResource(R.string.mode_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ChoiceRow(
                        title = stringResource(R.string.mode_clip_length),
                        options = DashcamSettings.CLIP_MINUTES,
                        selected = draft.clipMinutes,
                        label = { stringResource(R.string.mode_minutes, it) },
                        onSelect = { draft = draft.copy(clipMinutes = it) }
                    )
                    Spacer(Modifier.height(14.dp))
                    ChoiceRow(
                        title = stringResource(R.string.mode_quality),
                        options = RecordQuality.entries,
                        selected = draft.quality,
                        label = { "${qualityLabel(it)} · ${it.badge}" },
                        onSelect = { draft = draft.copy(quality = it) }
                    )
                    Spacer(Modifier.height(14.dp))
                    ChoiceRow(
                        title = stringResource(R.string.mode_retention),
                        options = DashcamSettings.RETENTION_DAYS,
                        selected = draft.retentionDays,
                        label = { stringResource(R.string.mode_retention_days, it) },
                        onSelect = { draft = draft.copy(retentionDays = it) }
                    )
                    Spacer(Modifier.height(14.dp))
                    ChoiceRow(
                        title = stringResource(R.string.mode_storage_limit),
                        options = DashcamSettings.STORAGE_LIMIT_GB,
                        selected = draft.storageLimitGb,
                        label = { stringResource(R.string.mode_storage_limit_value, it) },
                        onSelect = { draft = draft.copy(storageLimitGb = it) }
                    )
                    Spacer(Modifier.height(14.dp))
                    ChoiceRow(
                        title = stringResource(R.string.mode_overlap),
                        options = DashcamSettings.OVERLAP_SECONDS,
                        selected = draft.overlapSeconds,
                        label = {
                            if (it == 0) stringResource(R.string.mode_overlap_off)
                            else stringResource(R.string.mode_overlap_seconds, it)
                        },
                        onSelect = { draft = draft.copy(overlapSeconds = it) }
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.mode_automatic), color = OnPanel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    SwitchRow(
                        stringResource(R.string.mode_protect_start),
                        stringResource(R.string.mode_protect_start_hint),
                        draft.autoProtect
                    ) { draft = draft.copy(autoProtect = it) }
                    SwitchRow(
                        stringResource(R.string.mode_autostart),
                        stringResource(R.string.mode_autostart_hint),
                        draft.autoStartRecording
                    ) { draft = draft.copy(autoStartRecording = it) }
                    SwitchRow(
                        stringResource(R.string.mode_delete_old),
                        stringResource(R.string.mode_delete_old_hint),
                        draft.deleteOldClips
                    ) { draft = draft.copy(deleteOldClips = it) }
                    SwitchRow(
                        stringResource(R.string.mode_hevc),
                        stringResource(R.string.mode_hevc_hint),
                        draft.hevcEnabled
                    ) { draft = draft.copy(hevcEnabled = it) }
                    SwitchRow(
                        stringResource(R.string.mode_audio),
                        if (hasAudioPermission) stringResource(R.string.mode_audio_hint)
                        else stringResource(R.string.mode_audio_permission),
                        draft.audioEnabled
                    ) { enabled ->
                        draft = draft.copy(audioEnabled = enabled)
                        if (enabled && !hasAudioPermission) onRequestAudioPermission()
                    }
                    SwitchRow(
                        stringResource(R.string.mode_gps_speed),
                        if (hasLocationPermission) stringResource(R.string.mode_gps_speed_hint)
                        else stringResource(R.string.mode_gps_permission),
                        draft.gpsSpeedEnabled
                    ) { enabled ->
                        draft = draft.copy(gpsSpeedEnabled = enabled)
                        if (enabled && !hasLocationPermission) onRequestLocationPermission()
                    }
                    SwitchRow(
                        stringResource(R.string.mode_watermark),
                        if (hasLocationPermission) stringResource(R.string.mode_watermark_hint)
                        else stringResource(R.string.mode_gps_permission),
                        draft.watermarkEnabled
                    ) { enabled ->
                        draft = draft.copy(watermarkEnabled = enabled)
                        if (enabled && !hasLocationPermission) onRequestLocationPermission()
                    }
                    SwitchRow(
                        stringResource(R.string.mode_gsensor),
                        stringResource(R.string.mode_gsensor_hint),
                        draft.gSensorEnabled
                    ) { enabled ->
                        draft = draft.copy(
                            gSensorEnabled = enabled,
                            eventRecordingEnabled = if (enabled) draft.eventRecordingEnabled else false
                        )
                    }
                    if (draft.gSensorEnabled) {
                        Spacer(Modifier.height(10.dp))
                        ChoiceRow(
                            title = stringResource(R.string.mode_gsensor_sensitivity),
                            options = GSensorSensitivity.entries,
                            selected = draft.gSensorSensitivity,
                            label = { gSensorLabel(it) },
                            onSelect = { draft = draft.copy(gSensorSensitivity = it) },
                            onInfo = { showGSensorInfo = true }
                        )
                    }
                    SwitchRow(
                        stringResource(R.string.mode_event_recording),
                        stringResource(R.string.mode_event_recording_hint),
                        draft.eventRecordingEnabled
                    ) { enabled ->
                        draft = draft.copy(
                            eventRecordingEnabled = enabled,
                            gSensorEnabled = if (enabled) true else draft.gSensorEnabled
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    ChoiceRow(
                        title = stringResource(R.string.mode_display_timeout),
                        options = DashcamSettings.SCREEN_TIMEOUT_MINUTES,
                        selected = draft.screenTimeoutMinutes,
                        label = {
                            when (it) {
                                0 -> stringResource(R.string.mode_display_timeout_always)
                                1 -> stringResource(R.string.mode_display_timeout_minute)
                                else -> stringResource(R.string.mode_minutes, it)
                            }
                        },
                        onSelect = { draft = draft.copy(screenTimeoutMinutes = it) }
                    )
                    Text(
                        stringResource(R.string.mode_display_timeout_hint),
                        color = Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onApplyMode(draft)
                    showModeDialog = false
                }) { Text(stringResource(R.string.dialog_apply), color = Mint) }
            },
            dismissButton = {
                TextButton(onClick = { showModeDialog = false }) { Text(stringResource(R.string.dialog_cancel), color = Muted) }
            }
        )
    }

    if (showHelp) {
        val titles = stringArrayResource(R.array.help_titles)
        val bodies = stringArrayResource(R.array.help_bodies)
        AlertDialog(
            onDismissRequest = { showHelp = false },
            containerColor = Panel,
            titleContentColor = OnPanel,
            textContentColor = Muted,
            title = { Text(stringResource(R.string.help_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    titles.forEachIndexed { index, title ->
                        if (index > 0) Spacer(Modifier.height(14.dp))
                        Text(title, color = OnPanel, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(bodies.getOrElse(index) { "" }, color = Muted, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text(stringResource(R.string.help_confirm), color = Mint) }
            }
        )
    }

    if (showGSensorInfo) {
        AlertDialog(
            onDismissRequest = { showGSensorInfo = false },
            containerColor = Panel,
            titleContentColor = OnPanel,
            textContentColor = Muted,
            title = { Text(stringResource(R.string.mode_gsensor_info_title)) },
            text = {
                Text(
                    stringResource(R.string.mode_gsensor_info_body),
                    color = Muted,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showGSensorInfo = false }) {
                    Text(stringResource(R.string.mode_gsensor_info_confirm), color = Mint)
                }
            }
        )
    }

    if (showClips) {
        AlertDialog(
            onDismissRequest = { showClips = false },
            containerColor = Panel,
            titleContentColor = OnPanel,
            textContentColor = Muted,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.clips_title), color = OnPanel, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = stringResource(R.string.clips_share_all),
                        tint = if (state.clips.isEmpty()) Muted.copy(alpha = .4f) else Muted,
                        modifier = Modifier.size(20.dp)
                            .clickable(enabled = state.clips.isNotEmpty(), onClick = onShareAllClips)
                    )
                }
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (state.clips.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Videocam, null, tint = Muted)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.clips_empty_title), color = OnPanel, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.clips_empty_body), color = Muted, fontSize = 12.sp)
                            }
                        }
                    } else {
                        state.clips.forEachIndexed { index, clip ->
                            if (index > 0) Spacer(Modifier.height(10.dp))
                            ClipRow(
                                clip = clip,
                                onToggleProtection = onToggleClipProtection,
                                onDelete = { clipToDelete = it },
                                onPlay = onPlayClip,
                                onShare = onShareClip
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showClips = false }) {
                    Text(stringResource(R.string.clips_close), color = Mint)
                }
            }
        )
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            containerColor = Panel,
            titleContentColor = OnPanel,
            textContentColor = Muted,
            title = { Text(stringResource(R.string.menu_title)) },
            text = {
                Column {
                    MenuItemRow(
                        icon = Icons.Filled.Shield,
                        title = stringResource(R.string.menu_permissions),
                        subtitle = stringResource(R.string.menu_permissions_hint),
                        onClick = {
                            showMenu = false
                            showPermissions = true
                        }
                    )
                    MenuItemRow(
                        icon = Icons.Filled.Warning,
                        title = stringResource(R.string.menu_sensor_test),
                        subtitle = stringResource(R.string.menu_sensor_test_hint),
                        onClick = {
                            showMenu = false
                            showSensorTest = true
                        }
                    )
                    MenuItemRow(
                        icon = Icons.Filled.Menu,
                        title = stringResource(R.string.menu_help),
                        subtitle = stringResource(R.string.menu_help_hint),
                        onClick = {
                            showMenu = false
                            showHelp = true
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showMenu = false }) {
                    Text(stringResource(R.string.permissions_close), color = Mint)
                }
            }
        )
    }

    if (showPermissions) {
        PermissionsDialog(
            hasCameraPermission = hasCameraPermission,
            hasAudioPermission = hasAudioPermission,
            hasNotificationPermission = hasNotificationPermission,
            hasLocationPermission = hasLocationPermission,
            onRequestCameraPermission = onRequestCameraPermission,
            onRequestAudioPermission = onRequestAudioPermission,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onRequestLocationPermission = onRequestLocationPermission,
            onDismiss = { showPermissions = false }
        )
    }

    if (showSensorTest) {
        SensorTestDialog(
            sensitivity = settings.gSensorSensitivity,
            onDismiss = { showSensorTest = false }
        )
    }
}

@Composable
private fun MenuItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                .background(Elevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Mint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = OnPanel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PermissionsDialog(
    hasCameraPermission: Boolean,
    hasAudioPermission: Boolean,
    hasNotificationPermission: Boolean,
    hasLocationPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var batteryUnrestricted by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryUnrestricted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        titleContentColor = OnPanel,
        textContentColor = Muted,
        title = { Text(stringResource(R.string.permissions_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PermissionStatusRow(
                    title = stringResource(R.string.permissions_camera),
                    subtitle = stringResource(R.string.permissions_camera_hint),
                    granted = hasCameraPermission,
                    action = if (hasCameraPermission) null else stringResource(R.string.permission_grant),
                    onAction = onRequestCameraPermission
                )
                PermissionStatusRow(
                    title = stringResource(R.string.permissions_microphone),
                    subtitle = stringResource(R.string.permissions_microphone_hint),
                    granted = hasAudioPermission,
                    action = if (hasAudioPermission) null else stringResource(R.string.permission_grant),
                    onAction = onRequestAudioPermission
                )
                PermissionStatusRow(
                    title = stringResource(R.string.permissions_location),
                    subtitle = stringResource(R.string.permissions_location_hint),
                    granted = hasLocationPermission,
                    action = if (hasLocationPermission) null else stringResource(R.string.permission_grant),
                    onAction = onRequestLocationPermission
                )
                PermissionStatusRow(
                    title = stringResource(R.string.permissions_notifications),
                    subtitle = stringResource(R.string.permissions_notifications_hint),
                    granted = hasNotificationPermission,
                    action = if (hasNotificationPermission) null else stringResource(R.string.permission_grant),
                    onAction = onRequestNotificationPermission
                )
                PermissionStatusRow(
                    title = stringResource(R.string.permissions_battery),
                    subtitle = if (batteryUnrestricted) {
                        stringResource(R.string.permissions_battery_ok)
                    } else {
                        stringResource(R.string.permissions_battery_bad)
                    },
                    granted = batteryUnrestricted,
                    action = if (batteryUnrestricted) null else stringResource(R.string.permissions_battery_open),
                    onAction = {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.permissions_autostart_title),
                    color = OnPanel,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.permissions_autostart_hint), color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { openAppSettings(context) }) {
                    Text(stringResource(R.string.permissions_app_settings), color = Mint, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.permissions_close), color = Mint)
            }
        }
    )
}

@Composable
private fun PermissionStatusRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    action: String?,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (granted) Mint else Alert,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = OnPanel, fontSize = 14.sp)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        if (action != null) {
            TextButton(onClick = onAction) {
                Text(action, color = Mint, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SensorTestDialog(
    sensitivity: GSensorSensitivity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentG by remember { mutableStateOf(0f) }
    var peakG by remember { mutableStateOf(0f) }
    var triggered by remember { mutableStateOf(false) }
    var available by remember { mutableStateOf(true) }

    DisposableEffect(sensitivity) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val linearSensor = manager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val sensor = linearSensor ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) {
            available = false
            onDispose { }
        } else {
            available = true
            val detector = ShockDetector(
                thresholdG = sensitivity.thresholdG,
                removeGravity = linearSensor == null,
                onSample = { force ->
                    currentG = force
                    if (force > peakG) peakG = force
                    triggered = peakG >= sensitivity.thresholdG
                },
                onShock = {}
            )
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor != sensor) return
                    detector.onAcceleration(
                        event.values[0],
                        event.values[1],
                        event.values[2],
                        event.timestamp / 1_000_000L
                    )
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            onDispose { manager.unregisterListener(listener) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        titleContentColor = OnPanel,
        textContentColor = Muted,
        title = { Text(stringResource(R.string.sensor_test_title)) },
        text = {
            Column {
                if (!available) {
                    Text(stringResource(R.string.sensor_test_unavailable), color = Muted, fontSize = 12.sp)
                } else {
                    Text(stringResource(R.string.sensor_test_hint), color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.sensor_test_current, currentG),
                        color = if (triggered) Alert else OnPanel,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        stringResource(R.string.sensor_test_peak, peakG),
                        color = Muted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    val fraction = (currentG / (sensitivity.thresholdG * 1.6f)).coerceIn(0f, 1f)
                    Box(
                        Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                            .background(Elevated)
                    ) {
                        Box(
                            Modifier.fillMaxWidth(fraction).fillMaxHeight()
                                .background(if (triggered) Alert else Mint)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(
                            R.string.sensor_test_threshold,
                            gSensorBaseName(sensitivity),
                            sensitivity.thresholdG
                        ),
                        color = Muted,
                        fontSize = 12.sp
                    )
                    TextButton(onClick = {
                        peakG = 0f
                        triggered = false
                    }) {
                        Text(stringResource(R.string.sensor_test_reset), color = Mint, fontSize = 12.sp)
                    }
                    if (triggered) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.sensor_test_triggered),
                            color = Alert,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.sensor_test_close), color = Mint)
            }
        }
    )
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
    context.startActivity(intent)
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onInfo: (() -> Unit)? = null
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = OnPanel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (onInfo != null) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Filled.Info,
                    contentDescription = stringResource(R.string.mode_gsensor_info_title),
                    tint = Muted,
                    modifier = Modifier.size(15.dp).clickable { onInfo() }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { onSelect(option) },
                    shape = RoundedCornerShape(9.dp),
                    color = if (isSelected) Mint.copy(alpha = .18f) else Elevated
                ) {
                    Text(
                        label(option),
                        color = if (isSelected) Mint else OnPanel,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = OnPanel, fontSize = 14.sp)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = Mint,
                uncheckedThumbColor = Muted,
                uncheckedTrackColor = Elevated
            )
        )
    }
}

@Composable
private fun DashcamLandscape(
    state: DashcamUiState,
    hasCameraPermission: Boolean,
    hasLocationPermission: Boolean,
    landscapeLocked: Boolean,
    onToggleLandscapeLock: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onPreviewSurface: (Preview.SurfaceProvider?) -> Unit,
    onApplyMode: (DashcamSettings) -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleRecording: () -> Unit,
    onTakeSnapshot: () -> Unit,
    onProtectCurrent: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenClips: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val context = LocalContext.current
    val recording = state.recording
    val settings = state.settings
    val used = state.clips.sumOf { it.file.length() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            CameraPreview(onPreviewSurface, Modifier.fillMaxSize())
        } else {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.Videocam, contentDescription = null, tint = Muted, modifier = Modifier.size(38.dp))
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.camera_permission_required), color = Color.White, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onRequestCameraPermission) {
                    Text(stringResource(R.string.camera_permission_grant), color = Mint)
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OverlayPill {
                if (recording) Box(Modifier.size(7.dp).clip(CircleShape).background(Alert))
                else Icon(Icons.Filled.CheckCircle, null, tint = Mint, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(if (recording) R.string.camera_recording else R.string.camera_active),
                    color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp
                )
            }
            if (settings.gpsSpeedEnabled && hasLocationPermission) {
                OverlayPill {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = stringResource(R.string.landscape_gps),
                        tint = Mint,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        state.speedKmh?.let { stringResource(R.string.speed_overlay_kmh, it) }
                            ?: stringResource(R.string.speed_no_fix),
                        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            OverlayPill {
                Text(
                    Formatter.formatShortFileSize(context, used) + " · " +
                        stringResource(R.string.storage_card_clips, state.clips.size),
                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            }
            OverlayPill {
                val front = if (state.lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    stringResource(R.string.camera_badge_front) + " · "
                } else ""
                val codec = if (state.hevcActive) "HEVC" else "H.264"
                Text(
                    front + "${settings.quality.badge} · 30 FPS · $codec",
                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.canSwitchCamera) {
                OverlayIconButton(
                    icon = Icons.Filled.Cameraswitch,
                    contentDescription = stringResource(R.string.camera_switch),
                    enabled = !recording,
                    onClick = onSwitchCamera
                )
            }
            OverlayIconButton(
                icon = Icons.Filled.ScreenRotation,
                contentDescription = stringResource(R.string.landscape_toggle),
                tint = if (landscapeLocked) Mint else Color.White,
                onClick = onToggleLandscapeLock
            )
            OverlayIconButton(
                icon = Icons.Filled.Menu,
                contentDescription = stringResource(R.string.menu_open),
                onClick = onOpenMenu
            )
            OverlayIconButton(
                icon = if (settings.darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                contentDescription = stringResource(
                    if (settings.darkTheme) R.string.theme_switch_to_light else R.string.theme_switch_to_dark
                ),
                onClick = { onApplyMode(settings.copy(darkTheme = !settings.darkTheme)) }
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.weight(1f))
            OverlayIconButton(
                icon = Icons.Filled.VideoLibrary,
                contentDescription = stringResource(R.string.landscape_gallery),
                onClick = onOpenClips
            )
            OverlayIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.landscape_settings),
                enabled = !recording,
                onClick = onOpenSettings
            )
            Surface(
                modifier = Modifier.size(46.dp).clickable(onClick = onProtectCurrent),
                shape = RoundedCornerShape(14.dp),
                color = if (state.currentProtected) Mint.copy(alpha = .35f) else Color.Black.copy(alpha = .58f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = stringResource(R.string.protect_current),
                        tint = if (state.currentProtected) Mint else Color.White,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.width(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onToggleRecording,
                    enabled = state.cameraReady,
                    modifier = Modifier.weight(2f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (recording) MaterialTheme.colorScheme.errorContainer else Mint,
                        contentColor = if (recording) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = Elevated
                    )
                ) {
                    if (!state.cameraReady && hasCameraPermission) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Mint)
                    } else {
                        Box(Modifier.size(11.dp).clip(CircleShape).background(if (recording) Alert else MaterialTheme.colorScheme.onPrimary))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (recording) R.string.recording_stop else R.string.recording_start),
                        fontWeight = FontWeight.ExtraBold, letterSpacing = .4.sp, fontSize = 12.sp
                    )
                }
                LandscapeSnapshotTile(
                    modifier = Modifier.weight(1f),
                    enabled = state.cameraReady && state.snapshotAvailable,
                    onClick = onTakeSnapshot
                )
            }
        }
    }
}

@Composable
private fun OverlayPill(content: @Composable RowScope.() -> Unit) {
    Surface(shape = CircleShape, color = Color.Black.copy(alpha = .58f)) {
        Row(
            Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun OverlayIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = Color.White
) {
    Surface(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = Color.Black.copy(alpha = .58f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun LandscapeSnapshotTile(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else .4f
    Surface(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = .58f)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.PhotoCamera,
                contentDescription = stringResource(R.string.snapshot_take),
                tint = Mint.copy(alpha = alpha),
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.snapshot_tile),
                color = Color.White.copy(alpha = alpha),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = .4.sp,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun AutoFitText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    letterSpacing: TextUnit,
    maxLines: Int = 1
) {
    BoxWithConstraints {
        val textMeasurer = rememberTextMeasurer()
        val measured = textMeasurer.measure(
            text,
            TextStyle(
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                letterSpacing = letterSpacing
            ),
            maxLines = maxLines,
            softWrap = false
        )
        val maxWidthPx = constraints.maxWidth
        val scale = if (measured.size.width > maxWidthPx && measured.size.width > 0) {
            maxWidthPx.toFloat() / measured.size.width
        } else 1f
        Text(
            text,
            color = color,
            fontSize = fontSize * scale,
            fontWeight = fontWeight,
            letterSpacing = letterSpacing * scale,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CameraPreview(
    onSurfaceProvider: (Preview.SurfaceProvider?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(previewView) {
        onSurfaceProvider(previewView.surfaceProvider)
        onDispose { onSurfaceProvider(null) }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val cardModifier = if (onClick != null) modifier.clickable(enabled = enabled, onClick = onClick) else modifier
    Card(cardModifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp, modifier = Modifier.weight(1f))
                if (onClick != null) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Muted,
                        modifier = Modifier.size(15.dp).alpha(if (enabled) 1f else .4f)
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(value, color = OnPanel, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SnapshotCard(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else .5f
    Card(
        modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.PhotoCamera,
                contentDescription = stringResource(R.string.snapshot_take),
                tint = Mint.copy(alpha = alpha),
                modifier = Modifier.size(38.dp)
            )
        }
    }
}

@Composable
private fun ClipRow(
    clip: DashClip,
    onToggleProtection: (DashClip) -> Unit,
    onDelete: (DashClip) -> Unit,
    onPlay: (DashClip) -> Unit,
    onShare: (DashClip) -> Unit
) {
    val date = remember(clip.file.lastModified()) {
        SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.GERMANY).format(Date(clip.file.lastModified()))
    }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Elevated).clickable { onPlay(clip) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (clip.isPhoto) Icons.Filled.PhotoCamera else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (clip.isPhoto) R.string.clip_photo else R.string.clip_play
                    ),
                    tint = Mint
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(date, color = OnPanel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(Formatter.formatShortFileSize(LocalContext.current, clip.file.length()), color = Muted, fontSize = 11.sp)
            }
            if (clip.shockProtected) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = stringResource(R.string.clip_shock_protected),
                    tint = Alert,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(12.dp))
            }
            Icon(
                Icons.Filled.Share,
                contentDescription = stringResource(R.string.clip_share),
                tint = Muted,
                modifier = Modifier.size(19.dp).clickable { onShare(clip) }
            )
            Spacer(Modifier.width(12.dp))
            Icon(
                if (clip.protected) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = stringResource(if (clip.protected) R.string.clip_unprotect else R.string.clip_protect),
                tint = if (clip.protected) Mint else Muted,
                modifier = Modifier.size(20.dp).clickable { onToggleProtection(clip) }
            )
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDelete(clip) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(stringResource(R.string.clip_delete), color = Muted, fontSize = 11.sp)
            }
        }
    }
}
