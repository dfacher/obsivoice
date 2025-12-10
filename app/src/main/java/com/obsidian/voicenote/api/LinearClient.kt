package com.obsidian.voicenote.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class LinearTeam(val id: String, val name: String)

object LinearClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun getTeams(apiKey: String): List<LinearTeam> {
        val query = """
            query {
              teams {
                nodes {
                  id
                  name
                }
              }
            }
        """.trimIndent()
        
        val responseJson = executeGraphQL(apiKey, query)
        val nodes = responseJson.getJSONObject("data")
            .getJSONObject("teams")
            .getJSONArray("nodes")
            
        val teams = mutableListOf<LinearTeam>()
        for (i in 0 until nodes.length()) {
            val node = nodes.getJSONObject(i)
            teams.add(LinearTeam(node.getString("id"), node.getString("name")))
        }
        return teams
    }

    suspend fun createIssue(apiKey: String, teamId: String, title: String, description: String): String {
        // Escape strings for JSON
        val safeTitle = JSONObject.quote(title).drop(1).dropLast(1)
        val safeDesc = JSONObject.quote(description).drop(1).dropLast(1)

        val mutation = """
            mutation {
              issueCreate(input: {
                teamId: "$teamId"
                title: "$safeTitle"
                description: "$safeDesc"
              }) {
                success
                issue {
                  url
                }
              }
            }
        """.trimIndent()

        val responseJson = executeGraphQL(apiKey, mutation)
        val data = responseJson.getJSONObject("data").getJSONObject("issueCreate")
        if (!data.getBoolean("success")) {
            throw IOException("Linear API reported failure")
        }
        return data.getJSONObject("issue").getString("url")
    }

    private suspend fun executeGraphQL(apiKey: String, query: String): JSONObject {
        val jsonBody = JSONObject().apply {
            put("query", query)
        }

        val request = Request.Builder()
            .url("https://api.linear.app/graphql")
            .addHeader("Authorization", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Linear API request failed: ${response.code} ${response.message}")
                }
                val body = response.body?.string() ?: throw IOException("Empty response")
                val json = JSONObject(body)
                if (json.has("errors")) {
                    throw IOException("GraphQL Errors: ${json.getJSONArray("errors")}")
                }
                json
            }
        }
    }
}