package com.aviansh.audiodesyncdetector

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.aviansh.audiodesyncdetector.ui.screen.DetectorViewModel
import com.aviansh.audiodesyncdetector.ui.theme.AudioDesyncDetectorTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: DetectorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsState()

            AudioDesyncDetectorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val picker = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri ->
                        uri?.let {
                            copyToInternalStorage(it)?.let(viewModel::onFileSelected)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(20.dp)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Audio/Video Desync Detector", style = MaterialTheme.typography.headlineSmall)

                        Button(onClick = { picker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
                            Text("Select Video")
                        }

                        Text(
                            text = uiState.selectedFilePath ?: "No file selected",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Button(
                            onClick = viewModel::analyzeSelectedFile,
                            enabled = uiState.selectedFilePath != null && !uiState.isAnalyzing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (uiState.isAnalyzing) "Analyzing..." else "Analyze")
                        }

                        if (uiState.isAnalyzing || uiState.progressValue > 0f) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Stage: ${uiState.progressStage}")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { uiState.progressValue },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        uiState.resultMs?.let {
                            Text(
                                text = "Final desync: ${"%.1f".format(it)} ms",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        uiState.errorMessage?.let {
                            Text(text = "Error: $it", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    private fun copyToInternalStorage(uri: Uri): String? {
        return try {
            val name = getFileName(uri) ?: "selected_video"
            val destination = File(cacheDir, name)
            contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            destination.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    result = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = uri.toString().toUri().lastPathSegment
        }
        return result
    }
}
