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
    
    // Journal Defaults
    var journalProjectId by remember { mutableStateOf(prefs.getString("linear_journal_project_id", "") ?: "") }
    var journalLabelId by remember { mutableStateOf(prefs.getString("linear_journal_label_id", "") ?: "") }
    
    // Todo Defaults
    var todoProjectId by remember { mutableStateOf(prefs.getString("linear_todo_project_id", "") ?: "") }
    var todoLabelId by remember { mutableStateOf(prefs.getString("linear_todo_label_id", "") ?: "") }
    
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
                    fetchStatus = "Fetching data..."
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
        
        // Conditional Configurations for Journal vs Todo
        if (selectedTeamId.isNotBlank() && projects.isNotEmpty() && labels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Journal Default Settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
            
            Text("Journal Project:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top=8.dp).align(Alignment.Start))
            DropdownSelector(
                items = projects,
                selectedId = journalProjectId,
                onSelect = { id -> 
                    journalProjectId = id
                    prefs.edit().putString("linear_journal_project_id", id).apply()
                }
            )

            Text("Journal Label:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top=8.dp).align(Alignment.Start))
            DropdownSelector(
                items = labels,
                selectedId = journalLabelId,
                onSelect = { id -> 
                    journalLabelId = id
                    prefs.edit().putString("linear_journal_label_id", id).apply()
                },
                isLabel = true
            )

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("To-Do Default Settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
            
            Text("To-Do Project:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top=8.dp).align(Alignment.Start))
            DropdownSelector(
                items = projects,
                selectedId = todoProjectId,
                onSelect = { id -> 
                    todoProjectId = id
                    prefs.edit().putString("linear_todo_project_id", id).apply()
                }
            )

            Text("To-Do Label:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top=8.dp).align(Alignment.Start))
            DropdownSelector(
                items = labels,
                selectedId = todoLabelId,
                onSelect = { id -> 
                    todoLabelId = id
                    prefs.edit().putString("linear_todo_label_id", id).apply()
                },
                isLabel = true
            )
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
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownSelector(
    items: List<T>,
    selectedId: String,
    onSelect: (String) -> Unit,
    isLabel: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    // Helper to get name
    fun getName(item: T): String {
        return when(item) {
            is LinearProject -> item.name
            is LinearLabel -> item.name
            else -> ""
        }
    }
    fun getId(item: T): String {
         return when(item) {
            is LinearProject -> item.id
            is LinearLabel -> item.id
            else -> ""
        }
    }

    val selectedName = items.find { getId(it) == selectedId }?.let { getName(it) } ?: "None"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onSelect("")
                    expanded = false
                }
            )
            items.take(20).forEach { item ->
                DropdownMenuItem(
                    text = { Text(getName(item)) },
                    onClick = {
                        onSelect(getId(item))
                        expanded = false
                    }
                )
            }
        }
    }
}
