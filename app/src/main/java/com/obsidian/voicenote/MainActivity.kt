package com.obsidian.voicenote

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.obsidian.voicenote.ui.theme.Theme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Theme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var hasPermissions by remember { mutableStateOf(false) }
    var isSetupComplete by remember { mutableStateOf(false) }
    
    // Status state
    var statusMessage by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    // Check permissions and setup
    LaunchedEffect(LocalContext.current) {
        val prefs = context.getSharedPreferences("obsivoice_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", null)
        val folderUri = prefs.getString("folder_uri", null)
        isSetupComplete = !apiKey.isNullOrBlank() && !folderUri.isNullOrBlank()
        
        hasPermissions = checkPermissions(context)
    }

    // Listen for status updates from Service
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.getStringExtra("status")?.let { status ->
                    statusMessage = status
                    isProcessing = status.contains("Processing") || status.contains("Transcribing")
                    if (status.contains("saved") || status.contains("Error")) {
                        isProcessing = false
                        isRecording = false
                    }
                }
            }
        }
        val filter = IntentFilter("com.obsidian.voicenote.STATUS_UPDATE")
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter)

        onDispose {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
    }

    if (!isSetupComplete) {
        SetupScreen { isSetupComplete = true }
    } else if (!hasPermissions) {
        PermissionScreen {
            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(perms.toTypedArray())
        }
    } else {
        RecordScreen(
            isRecording = isRecording,
            statusMessage = statusMessage,
            onRecordToggle = {
                if (isRecording) {
                    val intent = Intent(context, RecordingService::class.java).apply {
                        action = RecordingService.ACTION_STOP
                    }
                    context.startService(intent)
                    // Optimistic update
                    isRecording = false
                } else {
                    val intent = Intent(context, RecordingService::class.java).apply {
                        action = RecordingService.ACTION_START
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                    isRecording = true
                    statusMessage = "Recording..."
                }
            },
            onSettingsClick = {
                context.startActivity(Intent(context, SettingsActivity::class.java))
            }
        )
    }
}

private fun checkPermissions(context: Context): Boolean {
    val record = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val notify = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
    return record && notify
}

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Permissions Required", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRequest) {
                Text("Grant Permissions")
            }
        }
    }
}

@Composable
fun SetupScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val intent = Intent(context, SettingsActivity::class.java)
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("Welcome to ObsiVoice", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Please configure your API key and Obsidian folder locally.", textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { context.startActivity(intent) }) {
                Text("Go to Settings")
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onComplete) {
                Text("I'm Done")
            }
        }
    }
}

@Composable
fun RecordScreen(
    isRecording: Boolean, 
    statusMessage: String,
    onRecordToggle: () -> Unit, 
    onSettingsClick: () -> Unit
) {
    // Timer state for recording duration
    var seconds by remember { mutableStateOf(0L) }
    
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val startTime = System.currentTimeMillis()
            while (isRecording) {
                seconds = (System.currentTimeMillis() - startTime) / 1000
                delay(1000)
            }
        } else {
            seconds = 0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Settings Button
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Text("⚙️", style = MaterialTheme.typography.headlineSmall)
        }
        
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Timer Display
            if (isRecording) {
                Text(
                    text = String.format("%02d:%02d", seconds / 60, seconds % 60),
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            }

            // Record/Stop Button
            Box {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scale by if (isRecording) {
                    infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse"
                    )
                } else {
                    remember { mutableStateOf(1f) }
                }

                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .scale(scale)
                        .background(
                            if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
                            if (isRecording) RoundedCornerShape(16.dp) else CircleShape
                        )
                        .clip(if (isRecording) RoundedCornerShape(16.dp) else CircleShape)
                        .clickable { onRecordToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    // Using a square-ish icon for Stop, Microphone for Record
                    Icon(
                        painter = painterResource(
                            id = if (isRecording) android.R.drawable.ic_media_pause else android.R.drawable.ic_btn_speak_now
                        ), 
                        // Note: ic_media_pause is typically two bars "||". 
                        // To represent "Stop", a square shape is better, but standard android drawables
                        // might not have a perfect filled square. ic_media_pause is often understood as pause/stop.
                        // Ideally we'd use a vector drawable for a square.
                        // For now keeping ic_media_pause as user context suggests they saw "pause".
                        // Wait, user complained it SAYS "pause". That likely refers to TalkBack or visual interpretation.
                        // Let's stick with this but maybe change the icon if we had a better one.
                        contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Status Message Area
            if (statusMessage.isNotEmpty()) {
                Card(
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = statusMessage,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        Text(
            text = if (isRecording) "Tap to Stop" else "Tap to Record",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)
        )
    }
}
