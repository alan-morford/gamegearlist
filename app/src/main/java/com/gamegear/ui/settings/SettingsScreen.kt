package com.gamegear.ui.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    var showDeleteImagesDialog by remember { mutableStateOf(false) }
    var showBackupOptionsDialog by remember { mutableStateOf(false) }
    var titleText by remember { mutableStateOf(vm.appTitle.value) }

    // File picker — open an existing save to load (JSON or ZIP backup)
    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it)
            if (mimeType?.contains("zip") == true) {
                context.contentResolver.openInputStream(it)?.let { stream -> vm.loadFromZip(stream) }
            } else {
                val content = context.contentResolver.openInputStream(it)
                    ?.bufferedReader()?.readText() ?: return@let
                vm.loadFromContent(content)
            }
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

    if (showDeleteImagesDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteImagesDialog = false },
            title = { Text("Delete All Images?") },
            text = {
                Text("This will remove all cover images from the app. You can restore them using Scan Missing Images.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteAllImages()
                        showDeleteImagesDialog = false
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteImagesDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showBackupOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showBackupOptionsDialog = false },
            title = { Text("Share Backup") },
            text = { Text("Include cover images in the backup?") },
            confirmButton = {
                TextButton(onClick = {
                    showBackupOptionsDialog = false
                    scope.launch {
                        val content = vm.buildSaveContent()
                        val timestamp = java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy_HHmm"))
                        val zipFile = withContext(Dispatchers.IO) {
                            val file = File(context.cacheDir, "gamegear_backup_${timestamp}.zip")
                            java.util.zip.ZipOutputStream(file.outputStream().buffered()).use { zip ->
                                zip.putNextEntry(java.util.zip.ZipEntry("gamegear_save.json"))
                                zip.write(content.toByteArray())
                                zip.closeEntry()
                                val coversDir = vm.getCoversDir()
                                if (coversDir.exists()) {
                                    coversDir.listFiles()?.forEach { img ->
                                        zip.putNextEntry(java.util.zip.ZipEntry("covers/${img.name}"))
                                        img.inputStream().use { it.copyTo(zip) }
                                        zip.closeEntry()
                                    }
                                }
                            }
                            file
                        }
                        val uri = FileProvider.getUriForFile(context, "com.gamegear.fileprovider", zipFile)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Backup"))
                    }
                }) { Text("Database + Images") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackupOptionsDialog = false
                    scope.launch {
                        val content = vm.buildSaveContent()
                        val timestamp = java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy_HHmm"))
                        val file = File(context.cacheDir, "gamegear_backup_${timestamp}.json")
                        file.writeText(content)
                        val uri = FileProvider.getUriForFile(context, "com.gamegear.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Backup"))
                    }
                }) { Text("Database Only") }
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

            OutlinedButton(
                onClick = { showBackupOptionsDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Backup")
            }

            // Load from a previously saved file
            OutlinedButton(
                onClick = { openFileLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Load Backup")
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
                        is SettingsViewModel.ScanState.Connecting -> "Connecting…"
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

            Button(
                onClick = { showDeleteImagesDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text("Delete All Images")
            }
        }
    }
}
