package com.obsidian.voicenote

import com.obsidian.voicenote.api.GptClient
import com.obsidian.voicenote.api.VoiceNoteMetadata
import com.obsidian.voicenote.api.WhisperClient
import java.io.File

data class ProcessedNote(
    val transcription: String,
    val metadata: VoiceNoteMetadata
)

object MetadataGenerator {
    suspend fun processAudio(audioFile: File, apiKey: String): ProcessedNote {
        // 1. Transcribe
        val transcription = WhisperClient.transcribe(audioFile, apiKey)
        
        // 2. Generate Metadata
        val metadata = GptClient.generateMetadata(transcription, apiKey)
        
        return ProcessedNote(transcription, metadata)
    }
}
