package com.obsidian.voicenote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.obsidian.voicenote.api.LinearClient
import com.obsidian.voicenote.api.LinearLabel
import com.obsidian.voicenote.api.LinearProject
import com.obsidian.voicenote.api.LinearTeam
import com.obsidian.voicenote.ui.theme.Theme
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Theme {
                SettingsScreen()
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("obsivoice_prefs", Context.MODE_PRIVATE)
    
    // Core Settings
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var folderUriStr by remember { mutableStateOf(prefs.getString("folder_uri", "") ?: "") }
    
    // Linear Settings
    var linearApiKey by remember { mutableStateOf(prefs.getString("linear_api_key", "") ?: "") }
    var selectedTeamId by remember { mutableStateOf(prefs.getString("linear_team_id", "") ?: "") }
    var selectedProjectId by remember { mutableStateOf(prefs.getString("linear_project_id", "") ?: "") }
    var selectedLabelId by remember { mutableStateOf(prefs.getString("linear_label_id", "") ?: "") }
    
    // Linear Data
    var teams by remember { mutableStateOf<List<LinearTeam>>(emptyList()) }
    var projects by remember { mutableStateOf<List<LinearProject>>(emptyList()) }
    var labels by remember { mutableStateOf<List<LinearLabel>>(emptyList()) }
    var fetchStatus by remember { mutableStateOf("") }
    
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or 
                           Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            
            folderUriStr = it.toString()
            prefs.edit().putString("folder_uri", folderUriStr).apply()
        }
    }

    // Main scrollable container
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // --- OpenAI ---
        Text("OpenAI Configuration", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { 
                apiKey = it
                prefs.edit().putString("api_key", it).apply()
            },
            label = { Text("OpenAI API Key") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // --- Linear ---
        Text("Linear Configuration", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = linearApiKey,
            onValueChange = { 
                linearApiKey = it
                prefs.edit().putString("linear_api_key", it).apply()
            },
            label = { Text("Linear API Key") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (linearApiKey.isNotBlank()) {
                    fetchStatus = "Fetching teams..."
                    scope.launch {
                        try {
                            teams = LinearClient.getTeams(linearApiKey)
                            fetchStatus = "Found ${teams.size} teams"
                        } catch (e: Exception) {
                            fetchStatus = "Error: ${e.message}"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = linearApiKey.isNotBlank()
        ) {
            Text("Fetch Teams")
        }
        
        if (fetchStatus.isNotBlank()) {
            Text(fetchStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        
        // Team Selection
        if (teams.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Select Team:", modifier = Modifier.align(Alignment.Start))
            
            Column {
                teams.forEach { team ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedTeamId = team.id
                            prefs.edit().putString("linear_team_id", team.id).apply()
                            
                            // Reset sub-selections when team changes
                            selectedProjectId = ""
                            prefs.edit().putString("linear_project_id", "").apply()
                            selectedLabelId = ""
                            prefs.edit().putString("linear_label_id", "").apply()
                            
                            // Fetch sub-items
                            fetchStatus = "Fetching projects/labels..."
                            scope.launch {
                                try {
                                    projects = LinearClient.getProjects(linearApiKey, team.id)
                                    labels = LinearClient.getLabels(linearApiKey, team.id)
                                    fetchStatus = "Found ${projects.size} projects, ${labels.size} labels"
                                } catch (e: Exception) {
                                    fetchStatus = "Error: ${e.message}"
                                }
                            }
                        }
                    ) {
                        RadioButton(
                            selected = team.id == selectedTeamId,
                            onClick = { /* handled by row click */ }
                        )
                        Text(team.name)
                    }
                }
            }
        }
        
        // Project Selection (Optional)
        if (selectedTeamId.isNotBlank() && projects.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Select Project (Optional):", modifier = Modifier.align(Alignment.Start))
            
            // "None" option
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable {
                    selectedProjectId = ""
                    prefs.edit().putString("linear_project_id", "").apply()
                }
            ) {
                RadioButton(selected = selectedProjectId.isEmpty(), onClick = { })
                Text("None")
            }

            projects.take(10).forEach { project ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        selectedProjectId = project.id
                        prefs.edit().putString("linear_project_id", project.id).apply()
                    }
                ) {
                    RadioButton(selected = project.id == selectedProjectId, onClick = { })
                    Text(project.name)
                }
            }
        }

        // Label Selection (Optional)
        if (selectedTeamId.isNotBlank() && labels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Select Label (Optional):", modifier = Modifier.align(Alignment.Start))
            
            // "None" option
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable {
                    selectedLabelId = ""
                    prefs.edit().putString("linear_label_id", "").apply()
                }
            ) {
                RadioButton(selected = selectedLabelId.isEmpty(), onClick = { })
                Text("None")
            }

            labels.take(10).forEach { label ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        selectedLabelId = label.id
                        prefs.edit().putString("linear_label_id", label.id).apply()
                    }
                ) {
                    RadioButton(selected = label.id == selectedLabelId, onClick = { })
                    Text(label.name)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // --- Storage ---
        Text("Backup Storage Location", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = { folderLauncher.launch(null) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select 'journals' Folder")
        }
        
        if (folderUriStr.isNotEmpty()) {
            Text(
                text = "Selected: ${Uri.parse(folderUriStr).lastPathSegment}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = { 
                if (apiKey.isBlank() || folderUriStr.isBlank()) {
                    Toast.makeText(context, "Please complete setup (Keys + Folder)", Toast.LENGTH_SHORT).show()
                } else {
                    (context as? ComponentActivity)?.finish() 
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save & Close")
        }
    }
}