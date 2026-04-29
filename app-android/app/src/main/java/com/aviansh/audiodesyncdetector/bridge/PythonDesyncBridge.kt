package com.aviansh.audiodesyncdetector.bridge

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

data class ProgressUpdate(val stage: String, val progress: Float)

class PythonDesyncBridge(private val context: Context) {

    private fun ensurePythonStarted() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
    }

    fun runDetection(filePath: String, onProgress: (ProgressUpdate) -> Unit): Double {
        ensurePythonStarted()
        val python = Python.getInstance()
        val module = python.getModule("desync_test")

        val callback = PyObject.fromJava { stage: String, progress: Int ->
            onProgress(ProgressUpdate(stage, progress.coerceIn(0, 100) / 100f))
            null
        }

        val result = module.callAttr("run_detection", filePath, callback)
        return result.toDouble()
    }
}
