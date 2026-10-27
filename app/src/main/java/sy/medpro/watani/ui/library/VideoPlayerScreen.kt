package sy.medpro.watani.ui.library

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.VideoView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sy.medpro.watani.ui.theme.MaroonPrimary
import java.io.File

private fun getFirebaseUid(): String {
    return try {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
    } catch (e: Exception) {
        ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(videoFile: File?, title: String, onBack: () -> Unit, videoFileUrl: String? = null) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    val prefs = remember { context.getSharedPreferences("watani_library_${getFirebaseUid()}", Context.MODE_PRIVATE) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VideoView(ctx).apply {
                    if (videoFile != null) {
                        setVideoPath(videoFile.absolutePath)
                    } else if (videoFileUrl != null) {
                        setVideoURI(android.net.Uri.parse(videoFileUrl))
                    }
                    setOnPreparedListener { mp ->
                        mp.start()
                        isPlaying = true
                    }
                    setOnCompletionListener {
                        isPlaying = false
                    }
                }
            }
        )

        if (showControls) {
            TopAppBar(
                title = { Text(title, maxLines = 1, color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaroonPrimary)
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(MaroonPrimary.copy(alpha = 0.9f))
                    .padding(16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showControls = false }) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }
            }
        }
    }
}
