package com.obsidian.voicenote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.obsidian.voicenote.api.LinearClient
import com.obsidian.voicenote.api.NoteType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class RecordingService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var startTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecordingAndProcess()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (isRecording) return
        
        createNotificationChannel()
        val notification = createNotification("Recording voice note...", true)
        startForeground(NOTIFICATION_ID, notification)

        try {
            audioFile = File(externalCacheDir, "temp_recording.m4a")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            startTime = System.currentTimeMillis()
            broadcastStatus("Recording...")
        } catch (e: Exception) {
            e.printStackTrace()
            broadcastStatus("Error starting recording: ${e.message}")
            stopSelf()
        }
    }

    private fun stopRecordingAndProcess() {
        if (!isRecording) return
        
        val durationMs = System.currentTimeMillis() - startTime
        val durationSec = durationMs / 1000
        val durationStr = String.format("%02d:%02d", durationSec / 60, durationSec % 60)

        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
        isRecording = false

        // Update notification to "Processing"
        updateNotification("Transcribing and analyzing...")
        broadcastStatus("Recording stopped ($durationStr).\nProcessing...")

        serviceScope.launch(Dispatchers.IO) {
            processRecording()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun processRecording() {
        val prefs = getSharedPreferences("obsivoice_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: ""
        val folderUriStr = prefs.getString("folder_uri", "") ?: ""
        
        val linearApiKey = prefs.getString("linear_api_key", "") ?: ""
        val linearTeamId = prefs.getString("linear_team_id", "") ?: ""
        
        // Defaults
        val journalProjectId = prefs.getString("linear_journal_project_id", null)
        val journalLabelId = prefs.getString("linear_journal_label_id", null)
        val todoProjectId = prefs.getString("linear_todo_project_id", null)
        val todoLabelId = prefs.getString("linear_todo_label_id", null)

        if (apiKey.isBlank() || folderUriStr.isBlank()) {
            broadcastStatus("Error: Setup not complete")
            return
        }

        val audio = audioFile ?: return
        val timestamp = LocalDateTime.now()
        val timestampStr = timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))
        val folderUri = Uri.parse(folderUriStr)

        try {
            broadcastStatus("Transcribing audio...")
            val processed = MetadataGenerator.processAudio(audio, apiKey)
            
            // Check if Linear is configured
            if (linearApiKey.isNotBlank() && linearTeamId.isNotBlank()) {
                broadcastStatus("Creating Linear Issue (${processed.metadata.type})...")
                
                val description = """
                    ${processed.transcription}
                    
                    **Tags**: ${processed.metadata.tags.joinToString(", ")}
                """.trimIndent()
                
                // Decide Project/Label based on classification
                val (projectId, labelId) = if (processed.metadata.type == NoteType.TODO) {
                    Pair(todoProjectId, todoLabelId)
                } else {
                    Pair(journalProjectId, journalLabelId)
                }
                
                val labelIds = if (!labelId.isNullOrBlank()) listOf(labelId) else null
                
                val issueUrl = LinearClient.createIssue(
                    apiKey = linearApiKey,
                    teamId = linearTeamId,
                    title = processed.metadata.topic,
                    description = description,
                    projectId = projectId,
                    labelIds = labelIds
                )
                
                broadcastStatus("Success! Issue created.")
            } else {
                // Fallback to Obsidian File
                broadcastStatus("Saving to Obsidian...")
                val filename = "${timestampStr}_voice-note.md"
                val markdown = MarkdownWriter.createVoiceNoteCode(
                    processed.transcription,
                    processed.metadata,
                    timestamp
                )

                MarkdownWriter.saveToObsidian(contentResolver, folderUri, filename, markdown)
                broadcastStatus("Success! Saved as:\n$filename")
            }
            
            // Clean up
            audio.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            broadcastStatus("Online processing failed: ${e.message}\nSaving offline backup...")
            
            try {
                // Fallback: Save audio file to Obsidian folder
                val audioFilename = "${timestampStr}_recording.m4a"
                saveAudioToFolder(folderUri, audioFilename, audio)
                broadcastStatus("Saved offline audio: $audioFilename")
                audio.delete()
            } catch (backupError: Exception) {
                backupError.printStackTrace()
                // Last resort: Save to app internal storage
                try {
                    val localBackup = File(filesDir, "${timestampStr}_backup.m4a")
                    audio.copyTo(localBackup, overwrite = true)
                    broadcastStatus("Saved internally: ${localBackup.name}")
                    audio.delete()
                } catch (localError: Exception) {
                    broadcastStatus("Critical Error: Could not save backup.")
                }
            }
        }
    }
    
    private fun saveAudioToFolder(folderUri: Uri, filename: String, audioFile: File) {
         val docId = DocumentsContract.getTreeDocumentId(folderUri)
         val dirUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)

         val docUri = DocumentsContract.createDocument(
            contentResolver,
            dirUri,
            "audio/mp4", 
            filename
        ) ?: throw IOException("Failed to create document URI")
        
        contentResolver.openOutputStream(docUri)?.use { outputStream ->
            audioFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw IOException("Failed to open output stream")
    }
    
    private fun broadcastStatus(status: String) {
        val intent = Intent("com.obsidian.voicenote.STATUS_UPDATE").apply {
            putExtra("status", status)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recording Status",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String, isRecording: Boolean): Notification {
        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ObsiVoice")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)

        if (isRecording) {
            builder.addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
        }
        
        return builder.build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text, false)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        mediaRecorder?.release()
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val CHANNEL_ID = "recording_channel"
        const val NOTIFICATION_ID = 1
    }
}
