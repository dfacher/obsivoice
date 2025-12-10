package com.obsidian.voicenote.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class VoiceNoteMetadata(
    val tags: List<String>,
    val topic: String
)

object GptClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generateMetadata(transcription: String, apiKey: String): VoiceNoteMetadata {
        val prompt = """
            Analyze this voice note transcription and provide:
            1. Relevant tags from ONLY these options: Product, Self-improvement, Productivity
               (include only tags that genuinely apply, can be none)
            2. A 1-2 sentence topic summary
            
            Transcription:
            "$transcription"
            
            Respond in JSON format:
            {"tags": ["Tag1", "Tag2"], "topic": "Brief summary here"}
        """.trimIndent()
        
        val jsonBody = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("response_format", JSONObject().put("type", "json_object"))
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    throw IOException("Metadata generation failed: ${response.code} - $errorBody")
                }
                
                val responseJson = JSONObject(response.body!!.string())
                val content = responseJson.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                
                val result = JSONObject(content)
                val tagsArray = result.optJSONArray("tags")
                val tags = mutableListOf<String>()
                if (tagsArray != null) {
                    for (i in 0 until tagsArray.length()) {
                        tags.add(tagsArray.getString(i))
                    }
                }
                
                val topic = result.optString("topic", "No summary available")
                
                VoiceNoteMetadata(tags, topic)
            }
        }
    }
}
