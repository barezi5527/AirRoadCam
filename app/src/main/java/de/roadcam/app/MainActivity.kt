package de.roadcam.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                DisposableEffect(state.settings.keepScreenOn) {
                    view.keepScreenOn = state.settings.keepScreenOn
                    onDispose { view.keepScreenOn = false }
                }

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
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashcamHome(
                        state = state,
                        hasCameraPermission = hasCameraPermission,
                        hasAudioPermission = hasAudioPermission,
                        hasNotificationPermission = hasNotificationPermission,
                        hasLocationPermission = hasLocationPermission,
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

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    override fun onDestroy() {
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
private fun textOf(text: UiText): String =
    stringResource(text.resId, *text.args.toTypedArray())

@Composable
private fun qualityLabel(quality: RecordQuality): String = stringResource(
    when (quality) {
        RecordQuality.SPAREND -> R.string.quality_sparsam
        RecordQuality.AUSGEWOGEN -> R.string.quality_ausgewogen
        RecordQuality.HOCH -> R.string.quality_hoch
    }
)

@Composable
private fun gSensorLabel(sensitivity: GSensorSensitivity): String = stringResource(
    when (sensitivity) {
        GSensorSensitivity.NIEDRIG -> R.string.gsensor_low
        GSensorSensitivity.MITTEL -> R.string.gsensor_medium
        GSensorSensitivity.HOCH -> R.string.gsensor_high
    }
)

@Composable
private fun DashcamHome(
    state: DashcamUiState,
    hasCameraPermission: Boolean,
    hasAudioPermission: Boolean,
    hasNotificationPermission: Boolean,
    hasLocationPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onPreviewSurface: (Preview.SurfaceProvider?) -> Unit,
    onApplyMode: (DashcamSettings) -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleRecording: () -> Unit,
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                    Text(stringResource(R.string.header_title), color = OnPanel, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    Text(stringResource(R.string.header_subtitle), color = Muted, fontSize = 10.sp, letterSpacing = 1.7.sp)
                }
                Surface(shape = CircleShape, color = Panel) {
                    Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (recording) Alert else Mint))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            stringResource(if (recording) R.string.status_live else R.string.status_ready),
                            color = OnPanel, fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
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
            Spacer(Modifier.height(8.dp))
            Text(textOf(state.status), color = Muted, fontSize = 12.sp)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    stringResource(R.string.mode_card_title),
                    stringResource(R.string.mode_card_value),
                    stringResource(R.string.mode_card_summary, settings.clipMinutes, qualityLabel(settings.quality)),
                    Modifier.weight(1f),
                    onClick = { showModeDialog = true },
                    enabled = !recording
                )
                val used = state.clips.sumOf { it.file.length() }
                MetricCard(
                    stringResource(R.string.storage_card_title),
                    Formatter.formatShortFileSize(context, used),
                    stringResource(R.string.storage_card_clips, state.clips.size),
                    Modifier.weight(1f)
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.clips_title), color = OnPanel, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.clips_subtitle), color = Muted, fontSize = 12.sp)
                }
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
                            onSelect = { draft = draft.copy(gSensorSensitivity = it) }
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
                    SwitchRow(
                        stringResource(R.string.mode_keep_screen_on),
                        stringResource(R.string.mode_keep_screen_on_hint),
                        draft.keepScreenOn
                    ) { draft = draft.copy(keepScreenOn = it) }
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
                            gSensorLabel(sensitivity),
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
    onSelect: (T) -> Unit
) {
    Column {
        Text(title, color = OnPanel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.clip_play), tint = Mint)
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
