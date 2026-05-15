package com.gamegear.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamegear.data.GameRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: GameRepository,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(repository))
    val scanState by vm.scanState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showResetDialog by remember { mutableStateOf(false) }
    var pendingSaveContent by remember { mutableStateOf<String?>(null) }
    var titleText by remember { mutableStateOf(vm.appTitle.value) }

    // File picker — open an existing save to load
    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val content = context.contentResolver.openInputStream(it)
                ?.bufferedReader()?.readText() ?: return@let
            vm.loadFromContent(content)
        }
    }

    // File creator — write the current save to a user-chosen location
    val createFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { dest ->
            val content = pendingSaveContent ?: return@let
            context.contentResolver.openOutputStream(dest)?.use { it.write(content.toByteArray()) }
            pendingSaveContent = null
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset All?") },
            text = {
                Text("This will clear all ownership toggles and notes. Game titles and images are not affected.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.resetAll()
                        showResetDialog = false
                    }
                ) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── List Title section ────────────────────────────────────────
            Text(
                text = "List Title",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            OutlinedTextField(
                value = titleText,
                onValueChange = {
                    titleText = it
                    vm.setAppTitle(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("App title") },
                singleLine = true,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Data section ──────────────────────────────────────────────
            Text(
                text = "Data",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            // Save current state to a user-chosen file (SAF-accessible)
            Button(
                onClick = {
                    scope.launch {
                        pendingSaveContent = vm.buildSaveContent()
                        createFileLauncher.launch("gamegear_save.json")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save Now")
            }

            // Load from a previously saved file
            OutlinedButton(
                onClick = { openFileLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Load Save File")
            }

            // Destructive reset
            Button(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text("Reset All")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ── Images section ────────────────────────────────────────────
            Text(
                text = "Images",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            val scanning = scanState is SettingsViewModel.ScanState.Scanning
                || scanState is SettingsViewModel.ScanState.Connecting

            OutlinedButton(
                onClick = { vm.scanMissingImages() },
                enabled = !scanning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (scanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = when (val s = scanState) {
                        is SettingsViewModel.ScanState.Connecting -> "Connecting to IGDB…"
                        is SettingsViewModel.ScanState.Scanning ->
                            "Scanning ${s.current} / ${s.total}…"
                        else -> "Scan Missing Images"
                    }
                )
            }

            if (scanState is SettingsViewModel.ScanState.Done) {
                Text(
                    text = (scanState as SettingsViewModel.ScanState.Done).message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
