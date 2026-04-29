package com.aviansh.audiodesyncdetector.ui.screen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aviansh.audiodesyncdetector.bridge.PythonDesyncBridge
import com.aviansh.audiodesyncdetector.ui.state.DetectorUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DetectorViewModel(application: Application) : AndroidViewModel(application) {

    private val bridge = PythonDesyncBridge(application.applicationContext)
    private val _uiState = MutableStateFlow(DetectorUiState())
    val uiState: StateFlow<DetectorUiState> = _uiState.asStateFlow()

    fun onFileSelected(path: String) {
        _uiState.update { it.copy(selectedFilePath = path, resultMs = null, errorMessage = null) }
    }

    fun analyzeSelectedFile() {
        val filePath = _uiState.value.selectedFilePath ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isAnalyzing = true,
                    progressStage = "Parsing Input",
                    progressValue = 0.01f,
                    resultMs = null,
                    errorMessage = null,
                )
            }
            try {
                val result = bridge.runDetection(filePath) { progress ->
                    _uiState.update { state ->
                        state.copy(progressStage = progress.stage, progressValue = progress.progress)
                    }
                }
                _uiState.update { it.copy(isAnalyzing = false, resultMs = result, progressValue = 1f) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isAnalyzing = false, errorMessage = e.message ?: "Unknown error")
                }
            }
        }
    }
}
