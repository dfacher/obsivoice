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
data class LinearProject(val id: String, val name: String)
data class LinearLabel(val id: String, val name: String)

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

    suspend fun getProjects(apiKey: String, teamId: String): List<LinearProject> {
        val query = """
            query {
              team(id: "$teamId") {
                projects {
                  nodes {
                    id
                    name
                  }
                }
              }
            }
        """.trimIndent()

        val responseJson = executeGraphQL(apiKey, query)
        val nodes = responseJson.getJSONObject("data")
            .getJSONObject("team")
            .getJSONObject("projects")
            .getJSONArray("nodes")

        val projects = mutableListOf<LinearProject>()
        for (i in 0 until nodes.length()) {
            val node = nodes.getJSONObject(i)
            projects.add(LinearProject(node.getString("id"), node.getString("name")))
        }
        return projects
    }

    suspend fun getLabels(apiKey: String, teamId: String): List<LinearLabel> {
        val query = """
            query {
              team(id: "$teamId") {
                labels {
                  nodes {
                    id
                    name
                  }
                }
              }
            }
        """.trimIndent()

        val responseJson = executeGraphQL(apiKey, query)
        val nodes = responseJson.getJSONObject("data")
            .getJSONObject("team")
            .getJSONObject("labels")
            .getJSONArray("nodes")

        val labels = mutableListOf<LinearLabel>()
        for (i in 0 until nodes.length()) {
            val node = nodes.getJSONObject(i)
            labels.add(LinearLabel(node.getString("id"), node.getString("name")))
        }
        return labels
    }

    suspend fun createIssue(
        apiKey: String, 
        teamId: String, 
        title: String, 
        description: String,
        projectId: String? = null,
        labelIds: List<String>? = null
    ): String {
        // Escape strings for JSON
        val safeTitle = JSONObject.quote(title).drop(1).dropLast(1)
        val safeDesc = JSONObject.quote(description).drop(1).dropLast(1)
        
        // Build optional fields
        val projectField = if (projectId != null && projectId.isNotBlank()) "projectId: \"$projectId\"" else ""
        
        val labelsField = if (labelIds != null && labelIds.isNotEmpty()) {
             val ids = labelIds.joinToString("\", \"") { it }
             "labelIds: [\"$ids\"]"
        } else ""

        val mutation = """
            mutation {
              issueCreate(input: {
                teamId: "$teamId"
                title: "$safeTitle"
                description: "$safeDesc"
                $projectField
                $labelsField
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