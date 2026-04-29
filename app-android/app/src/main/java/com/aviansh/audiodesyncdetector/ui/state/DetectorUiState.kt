package com.aviansh.audiodesyncdetector.ui.state

data class DetectorUiState(
    val selectedFilePath: String? = null,
    val progressStage: String = "Idle",
    val progressValue: Float = 0f,
    val isAnalyzing: Boolean = false,
    val resultMs: Double? = null,
    val errorMessage: String? = null,
)
