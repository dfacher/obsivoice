package com.obsidian.voicenote

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.obsidian.voicenote.api.VoiceNoteMetadata
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object MarkdownWriter {

    fun createVoiceNoteCode(
        transcription: String,
        metadata: VoiceNoteMetadata,
        timestamp: LocalDateTime
    ): String {
        // Formatter for the key in "created" map
        val keyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val dateKey = timestamp.format(keyFormatter)
        
        // Tags
        val allTags = mutableListOf("journal")
        allTags.addAll(metadata.tags)
        val tagsYaml = allTags.joinToString("\n") { "  - $it" }
        
        return """
---
created:
  "$dateKey":
tags:
$tagsYaml
topic: ${metadata.topic}
processed: false
---

$transcription
""".trimStart()
    }

    // Changed to throw exception for better error reporting
    @Throws(Exception::class)
    fun saveToObsidian(
        contentResolver: ContentResolver,
        folderUri: Uri,
        filename: String,
        content: String
    ) {
        // Fix: Convert Tree URI to Document URI for the directory
        val docId = DocumentsContract.getTreeDocumentId(folderUri)
        val dirUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)

        val docUri = DocumentsContract.createDocument(
            contentResolver,
            dirUri,
            "text/markdown",
            filename
        ) ?: throw IOException("Failed to create document: URI returned null")
        
        contentResolver.openOutputStream(docUri)?.use { outputStream ->
            outputStream.write(content.toByteArray())
        } ?: throw IOException("Failed to open output stream")
    }
}
