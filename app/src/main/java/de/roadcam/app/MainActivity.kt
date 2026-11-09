package de.roadcam.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF0B1017)
private val Panel = Color(0xFF151D27)
private val Muted = Color(0xFF9AA7B5)
private val Mint = Color(0xFF56E0B2)
private val Alert = Color(0xFFFF5B64)

private enum class RecordQuality(
    val title: String,
    val badge: String,
    val quality: Quality,
    val bitrate: Int
) {
    SPAREND("Sparsam", "720p", Quality.HD, 4_000_000),
    AUSGEWOGEN("Ausgewogen", "1080p", Quality.FHD, 6_000_000),
    HOCH("Hoch", "1080p", Quality.FHD, 10_000_000)
}

private val helpSections = listOf(
    "Kopfzeile" to "Logo und App-Name links. Die Pille rechts zeigt den Status: grüner Punkt „BEREIT“, roter Punkt „LIVE“ während der Aufnahme.",
    "Kamerabild" to "Oben links steht „KAMERA AKTIV“ bzw. „AUFNAHME“ mit rotem Punkt. Oben rechts wechselt das Kamera-Symbol zwischen Front- und Rückkamera – nur wenn beide vorhanden sind und nicht aufgenommen wird. Unten rechts zeigt das Badge Auflösung und Bildrate, z. B. „1080p · 30 FPS“.",
    "Aufnahme & Schutz" to "Der große Button startet und beendet die Aufnahme. Das Schild daneben schützt den Clip: während der Aufnahme das laufende Segment, sonst die neueste Aufnahme. Geschützte Clips werden bei der automatischen Speicherbereinigung nie gelöscht. Mint = geschützt, erneut tippen hebt den Schutz auf. Nach einem Segmentwechsel ist das neue Segment wieder ungeschützt.",
    "Karten" to "„AUFNAHMEMODUS“ öffnet Clip-Länge (1, 3 oder 5 Minuten), Qualität (Sparsam 720p, Ausgewogen 1080p, Hoch 1080p) und Automatik: „Schutz beim Start“ schützt neue Aufnahmen automatisch, „Alte Clips löschen“ entfernt ungeschützte Clips nach 5 Tagen. Während einer Aufnahme ist die Karte gesperrt. „LOKAL GESPEICHERT“ zeigt belegten Speicher und Anzahl der Clips.",
    "Letzte Aufnahmen" to "Play öffnet den Clip im Video-Player. Das Schloss schützt oder entsperrt einen gespeicherten Clip. Geschützte Clips lassen sich erst nach Aufheben des Schutzes löschen; ungeschützte entfernt „Löschen“ nach einer Bestätigung endgültig.",
    "Hinweis" to "Audio und GPS sind deaktiviert, alle Aufnahmen bleiben lokal auf dem Gerät."
)

class MainActivity : ComponentActivity() {
    private var videoCapture by mutableStateOf<VideoCapture<Recorder>?>(null)
    private var lensFacing by mutableStateOf(CameraSelector.LENS_FACING_BACK)
    private var canSwitchCamera by mutableStateOf(false)
    private var clipMinutes by mutableStateOf(3)
    private var recordQuality by mutableStateOf(RecordQuality.AUSGEWOGEN)
    private var autoProtect by mutableStateOf(false)
    private var autoDeleteOld by mutableStateOf(false)
    private var isRecording by mutableStateOf(false)
    private var isCurrentProtected by mutableStateOf(false)
    private var statusText by mutableStateOf("Kamera wird vorbereitet")
    private var clips by mutableStateOf<List<DashClip>>(emptyList())
    private var currentFile: File? = null
    private var activeRecording: Recording? = null
    private var recordingRequested = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val segmentLengthMs: Long get() = clipMinutes * 60 * 1000L
    private val maxStorageBytes = 10L * 1024 * 1024 * 1024
    private val freeSpaceReserveBytes = 1024L * 1024 * 1024

    private val segmentTimer = Runnable {
        activeRecording?.stop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(11, 16, 23)
        window.navigationBarColor = android.graphics.Color.rgb(11, 16, 23)
        window.decorView.systemUiVisibility = 0
        val settings = getSharedPreferences("settings", MODE_PRIVATE)
        clipMinutes = settings.getInt("clipMinutes", clipMinutes)
        recordQuality = RecordQuality.entries.firstOrNull {
            it.name == settings.getString("quality", null)
        } ?: recordQuality
        autoProtect = settings.getBoolean("autoProtect", autoProtect)
        autoDeleteOld = settings.getBoolean("autoDeleteOld", autoDeleteOld)
        deleteOldClips()
        refreshClips()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                    color = Ink
                ) {
                    DashcamHome(
                        capture = videoCapture,
                        recording = isRecording,
                        currentProtected = if (isRecording) isCurrentProtected else clips.firstOrNull()?.protected == true,
                        status = statusText,
                        clips = clips,
                        lensFacing = lensFacing,
                        canSwitchCamera = canSwitchCamera,
                        clipMinutes = clipMinutes,
                        recordQuality = recordQuality,
                        autoProtect = autoProtect,
                        autoDeleteOld = autoDeleteOld,
                        onApplyMode = { minutes, quality, protect, deleteOld ->
                            clipMinutes = minutes
                            recordQuality = quality
                            autoProtect = protect
                            autoDeleteOld = deleteOld
                            getSharedPreferences("settings", MODE_PRIVATE).edit()
                                .putInt("clipMinutes", minutes)
                                .putString("quality", quality.name)
                                .putBoolean("autoProtect", protect)
                                .putBoolean("autoDeleteOld", deleteOld)
                                .apply()
                            if (deleteOld) {
                                deleteOldClips()
                                refreshClips()
                            }
                        },
                        onCaptureReady = { videoCapture = it },
                        onSwitchCamera = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                                CameraSelector.LENS_FACING_FRONT
                            else CameraSelector.LENS_FACING_BACK
                        },
                        onCameraAvailability = { hasFront, hasBack ->
                            canSwitchCamera = hasFront && hasBack
                        },
                        onStartStop = { if (isRecording) stopLoop() else startLoop() },
                        onProtectCurrent = ::toggleCurrentProtection,
                        onToggleClipProtection = ::toggleClipProtection,
                        onDeleteClip = ::deleteClip,
                        onPlayClip = ::playClip,
                        onCameraError = { statusText = it }
                    )
                }
            }
        }
    }

    private fun recordingsDirectory(): File = File(
        getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir,
        "RoadCam"
    ).apply { mkdirs() }

    private fun protectedNames(): MutableSet<String> =
        getSharedPreferences("clips", MODE_PRIVATE)
            .getStringSet("protected", emptySet())
            .orEmpty().toMutableSet()

    private fun refreshClips() {
        val protected = protectedNames()
        clips = recordingsDirectory().listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("mp4", true) && it.length() > 0L }
            .sortedByDescending(File::lastModified)
            .map { DashClip(it, it.name in protected) }
    }

    private fun startLoop() {
        if (videoCapture == null) {
            statusText = "Kamera ist noch nicht bereit"
            return
        }
        recordingRequested = true
        isRecording = true
        statusText = "Aufnahme läuft · Clips à 3 Min."
        startSegment()
    }

    private fun startSegment() {
        val capture = videoCapture ?: run {
            stopLoop("Kamera nicht verfügbar")
            return
        }
        if (!recordingRequested) return
        pruneClips()
        val directory = recordingsDirectory()
        val usable = directory.usableSpace
        if (usable < freeSpaceReserveBytes) {
            stopLoop("Zu wenig freier Speicher")
            return
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(directory, "RoadCam_$stamp.mp4")
        currentFile = file
        isCurrentProtected = autoProtect
        if (autoProtect) addProtected(file.name)
        val outputOptions = FileOutputOptions.Builder(file).build()
        activeRecording = capture.output
            .prepareRecording(this, outputOptions)
            .start(ContextCompat.getMainExecutor(this)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    mainHandler.removeCallbacks(segmentTimer)
                    if (event.hasError()) {
                        file.delete()
                        if (recordingRequested) stopLoop("Aufnahmefehler · bitte Kamera prüfen")
                        else isRecording = false
                    } else {
                        if (isCurrentProtected) addProtected(file.name) else removeProtected(file.name)
                        refreshClips()
                        if (recordingRequested) {
                            mainHandler.postDelayed({ startSegment() }, 350L)
                        } else {
                            isRecording = false
                            statusText = "Aufnahme gespeichert"
                        }
                    }
                    activeRecording = null
                    currentFile = null
                }
            }
        mainHandler.removeCallbacks(segmentTimer)
        mainHandler.postDelayed(segmentTimer, segmentLengthMs)
    }

    private fun stopLoop(message: String = "Aufnahme beendet") {
        recordingRequested = false
        mainHandler.removeCallbacks(segmentTimer)
        statusText = message
        activeRecording?.stop()
        if (activeRecording == null) isRecording = false
    }

    private fun toggleCurrentProtection() {
        if (isRecording) {
            val file = currentFile ?: return
            isCurrentProtected = !isCurrentProtected
            if (isCurrentProtected) addProtected(file.name) else removeProtected(file.name)
        } else {
            val newest = clips.firstOrNull()
            if (newest == null) {
                statusText = "Noch keine Aufnahme vorhanden"
                return
            }
            if (newest.protected) removeProtected(newest.file.name) else addProtected(newest.file.name)
        }
        refreshClips()
    }

    private fun toggleClipProtection(clip: DashClip) {
        if (clip.protected) removeProtected(clip.file.name) else addProtected(clip.file.name)
        refreshClips()
    }

    private fun addProtected(name: String) {
        val protected = protectedNames().apply { add(name) }
        getSharedPreferences("clips", MODE_PRIVATE).edit().putStringSet("protected", protected).apply()
    }

    private fun removeProtected(name: String) {
        val protected = protectedNames().apply { remove(name) }
        getSharedPreferences("clips", MODE_PRIVATE).edit().putStringSet("protected", protected).apply()
    }

    private fun deleteClip(clip: DashClip) {
        if (activeRecording != null && clip.file == currentFile) return
        if (clip.protected) {
            statusText = "Clip ist geschützt – Schutz zuerst aufheben"
            return
        }
        clip.file.delete()
        removeProtected(clip.file.name)
        refreshClips()
    }

    private fun playClip(clip: DashClip) {
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", clip.file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            statusText = "Kein Video-Player auf diesem Gerät verfügbar"
        }
    }

    private fun deleteOldClips() {
        if (!autoDeleteOld) return
        val protected = protectedNames()
        val cutoff = System.currentTimeMillis() - 5L * 24L * 60L * 60L * 1000L
        recordingsDirectory().listFiles().orEmpty()
            .filter {
                it.isFile && it.extension.equals("mp4", true) &&
                    it.name !in protected && it != currentFile && it.lastModified() < cutoff
            }
            .forEach { it.delete() }
    }

    private fun pruneClips() {
        deleteOldClips()
        val directory = recordingsDirectory()
        val protected = protectedNames()
        val files = directory.listFiles().orEmpty()
            .filter { it.isFile && it.extension.equals("mp4", true) }
        var total = files.sumOf(File::length)
        val oldestFirst = files.filter { it.name !in protected && it != currentFile }
            .sortedBy(File::lastModified)
        for (file in oldestFirst) {
            if (total <= maxStorageBytes && directory.usableSpace >= freeSpaceReserveBytes) break
            val length = file.length()
            if (file.delete()) total -= length
        }
        refreshClips()
    }

    override fun onDestroy() {
        recordingRequested = false
        mainHandler.removeCallbacks(segmentTimer)
        activeRecording?.stop()
        super.onDestroy()
    }
}

data class DashClip(val file: File, val protected: Boolean)

@Composable
private fun DashcamHome(
    capture: VideoCapture<Recorder>?,
    recording: Boolean,
    currentProtected: Boolean,
    status: String,
    clips: List<DashClip>,
    lensFacing: Int,
    canSwitchCamera: Boolean,
    clipMinutes: Int,
    recordQuality: RecordQuality,
    autoProtect: Boolean,
    autoDeleteOld: Boolean,
    onApplyMode: (Int, RecordQuality, Boolean, Boolean) -> Unit,
    onCaptureReady: (VideoCapture<Recorder>?) -> Unit,
    onSwitchCamera: () -> Unit,
    onCameraAvailability: (Boolean, Boolean) -> Unit,
    onStartStop: () -> Unit,
    onProtectCurrent: () -> Unit,
    onToggleClipProtection: (DashClip) -> Unit,
    onDeleteClip: (DashClip) -> Unit,
    onPlayClip: (DashClip) -> Unit,
    onCameraError: (String) -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var clipToDelete by remember { mutableStateOf<DashClip?>(null) }
    var showModeDialog by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Mint.copy(alpha = .14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Videocam, contentDescription = null, tint = Mint, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ROADCAM", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                        Text("FAHRTSICHERHEIT", color = Muted, fontSize = 10.sp, letterSpacing = 1.7.sp)
                    }
                    Surface(shape = CircleShape, color = Panel) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(if (recording) Alert else Mint))
                            Spacer(Modifier.width(7.dp))
                            Text(if (recording) "LIVE" else "BEREIT", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                IconButton(
                    onClick = { showHelp = true },
                    modifier = Modifier.align(Alignment.Center).size(40.dp)
                ) {
                    Icon(Icons.Filled.Menu, contentDescription = "Hilfe zur Oberfläche", tint = Muted, modifier = Modifier.size(22.dp))
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
                    if (hasPermission) {
                        CameraPreview(lensFacing, recordQuality, onCaptureReady, onCameraError, onCameraAvailability, Modifier.fillMaxSize())
                    } else {
                        Column(Modifier.fillMaxSize().background(Color(0xFF111820)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Filled.Videocam, contentDescription = null, tint = Muted, modifier = Modifier.size(38.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("Kamerazugriff erforderlich", color = Color.White, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Zugriff erlauben", color = Mint) }
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
                                Text(if (recording) "AUFNAHME" else "KAMERA AKTIV", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
                            }
                        }
                    }
                    if (hasPermission && canSwitchCamera) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                                .clickable(enabled = !recording, onClick = onSwitchCamera),
                            shape = CircleShape, color = Color.Black.copy(alpha = .58f)
                        ) {
                            Box(Modifier.padding(8.dp)) {
                                Icon(Icons.Filled.Cameraswitch, contentDescription = "Kamera wechseln", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        shape = RoundedCornerShape(9.dp), color = Color.Black.copy(alpha = .58f)
                    ) {
                        Text(
                            (if (lensFacing == CameraSelector.LENS_FACING_FRONT) "FRONT  ·  " else "") + "${recordQuality.badge}  ·  30 FPS",
                            Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onStartStop,
                    enabled = capture != null,
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (recording) Color(0xFF38232A) else Mint, contentColor = if (recording) Alert else Ink, disabledContainerColor = Panel)
                ) {
                    if (capture == null && hasPermission) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Mint)
                    else Box(Modifier.size(12.dp).clip(CircleShape).background(if (recording) Alert else Ink))
                    Spacer(Modifier.width(10.dp))
                    Text(if (recording) "AUFNAHME BEENDEN" else "AUFNAHME STARTEN", fontWeight = FontWeight.ExtraBold, letterSpacing = .4.sp)
                }
                Surface(
                    modifier = Modifier.size(58.dp).clickable(onClick = onProtectCurrent),
                    shape = RoundedCornerShape(18.dp), color = if (currentProtected) Mint.copy(alpha = .16f) else Panel
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Shield, contentDescription = "Aktuellen Clip schützen", tint = if (currentProtected) Mint else Color.White, modifier = Modifier.size(23.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(status, color = Muted, fontSize = 12.sp)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    "AUFNAHMEMODUS", "Schleife",
                    "$clipMinutes Min · ${recordQuality.title}",
                    Modifier.weight(1f),
                    onClick = { showModeDialog = true },
                    enabled = !recording
                )
                val used = clips.sumOf { it.file.length() }
                MetricCard("LOKAL GESPEICHERT", Formatter.formatShortFileSize(context, used), "${clips.size} Clips", Modifier.weight(1f))
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Letzte Aufnahmen", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Lokal auf diesem Gerät", color = Muted, fontSize = 12.sp)
                }
                Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
            }
        }

        if (clips.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Videocam, null, tint = Muted)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Noch keine Aufnahmen", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Deine Clips erscheinen nach der ersten Fahrt hier.", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            items(clips.take(8), key = { it.file.name }) { clip ->
                ClipRow(clip, onToggleClipProtection, onDelete = { clipToDelete = it }, onPlayClip)
            }
        }

        item {
            Text("Audio und GPS sind deaktiviert. Aufnahmen bleiben auf deinem Gerät.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }

    clipToDelete?.let { clip ->
        val isProtected = clip.protected
        AlertDialog(
            onDismissRequest = { clipToDelete = null },
            containerColor = Panel,
            titleContentColor = Color.White,
            textContentColor = Muted,
            title = { Text(if (isProtected) "Clip ist geschützt" else "Clip löschen?") },
            text = {
                Text(
                    if (isProtected) "Geschützte Clips werden nicht automatisch gelöscht. Hebe zuerst den Schutz auf, um diesen Clip löschen zu können."
                    else "Die Aufnahme wird endgültig vom Gerät entfernt."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isProtected) onToggleClipProtection(clip) else onDeleteClip(clip)
                    clipToDelete = null
                }) { Text(if (isProtected) "Schutz aufheben" else "Endgültig löschen", color = if (isProtected) Mint else Alert) }
            },
            dismissButton = {
                TextButton(onClick = { clipToDelete = null }) { Text("Abbrechen", color = Muted) }
            }
        )
    }

    if (showModeDialog) {
        var draftMinutes by remember { mutableStateOf(clipMinutes) }
        var draftQuality by remember { mutableStateOf(recordQuality) }
        var draftProtect by remember { mutableStateOf(autoProtect) }
        var draftDeleteOld by remember { mutableStateOf(autoDeleteOld) }
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            containerColor = Panel,
            titleContentColor = Color.White,
            textContentColor = Muted,
            title = { Text("Aufnahmemodus") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Clip-Länge", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    listOf(1, 3, 5).forEach { minutes ->
                        OptionRow("$minutes Minuten", draftMinutes == minutes) { draftMinutes = minutes }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Qualität", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    RecordQuality.entries.forEach { quality ->
                        OptionRow("${quality.title} · ${quality.badge}", draftQuality == quality) { draftQuality = quality }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Automatik", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    SwitchRow(
                        "Schutz beim Start",
                        "Neue Aufnahmen automatisch schützen",
                        draftProtect
                    ) { draftProtect = it }
                    SwitchRow(
                        "Alte Clips löschen",
                        "Ungeschützte Clips nach 5 Tagen entfernen",
                        draftDeleteOld
                    ) { draftDeleteOld = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onApplyMode(draftMinutes, draftQuality, draftProtect, draftDeleteOld)
                    showModeDialog = false
                }) { Text("Übernehmen", color = Mint) }
            },
            dismissButton = {
                TextButton(onClick = { showModeDialog = false }) { Text("Abbrechen", color = Muted) }
            }
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            containerColor = Panel,
            titleContentColor = Color.White,
            textContentColor = Muted,
            title = { Text("Oberfläche") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    helpSections.forEachIndexed { index, (title, body) ->
                        if (index > 0) Spacer(Modifier.height(14.dp))
                        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(body, color = Muted, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("Verstanden", color = Mint) }
            }
        )
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = Mint, unselectedColor = Muted)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
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
            Text(title, color = Color.White, fontSize = 14.sp)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink,
                checkedTrackColor = Mint,
                uncheckedThumbColor = Muted,
                uncheckedTrackColor = Panel
            )
        )
    }
}

@Composable
private fun CameraPreview(
    lensFacing: Int,
    recordQuality: RecordQuality,
    onCaptureReady: (VideoCapture<Recorder>?) -> Unit,
    onError: (String) -> Unit,
    onAvailability: (hasFront: Boolean, hasBack: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)

    LaunchedEffect(lifecycleOwner, previewView, lensFacing, recordQuality) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val hasFront = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                val hasBack = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                onAvailability(hasFront, hasBack)
                val selector = when {
                    lensFacing == CameraSelector.LENS_FACING_FRONT && hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                    lensFacing == CameraSelector.LENS_FACING_BACK && hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
                    hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
                    hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> throw IllegalStateException("Keine Kamera gefunden")
                }
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(recordQuality.quality, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                    .setTargetVideoEncodingBitRate(recordQuality.bitrate)
                    .build()
                val capture = VideoCapture.withOutput(recorder)
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                onCaptureReady(capture)
            } catch (error: Exception) {
                onCaptureReady(null)
                onError("Kamera konnte nicht gestartet werden")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            provider?.unbindAll()
            onCaptureReady(null)
        }
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
            Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ClipRow(clip: DashClip, onToggleProtection: (DashClip) -> Unit, onDelete: (DashClip) -> Unit, onPlay: (DashClip) -> Unit) {
    val date = remember(clip.file.lastModified()) {
        SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.GERMANY).format(Date(clip.file.lastModified()))
    }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF202B37)).clickable { onPlay(clip) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Clip abspielen", tint = Mint)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(date, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(Formatter.formatShortFileSize(LocalContext.current, clip.file.length()), color = Muted, fontSize = 11.sp)
            }
            Icon(
                if (clip.protected) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = if (clip.protected) "Schutz aufheben" else "Clip schützen",
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
                Text("Löschen", color = Muted, fontSize = 11.sp)
            }
        }
    }
}
