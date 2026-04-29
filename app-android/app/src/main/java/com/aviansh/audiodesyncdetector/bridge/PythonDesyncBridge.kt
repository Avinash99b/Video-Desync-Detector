package com.aviansh.audiodesyncdetector.bridge

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.aviansh.audiodesyncdetector.util.FfprobeUtil
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import java.io.File

data class ProgressUpdate(val stage: String, val progress: Float)

class PythonDesyncBridge(private val context: Context) {

    private fun ensurePythonStarted() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }

        val python = Python.getInstance()
        val os = python.getModule("os")

    }

    fun runDetection(
        filePath: String, onProgress: (ProgressUpdate) -> Unit
    ): Double {

        try {
            ensurePythonStarted()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val python = Python.getInstance()
        val module = python.getModule("desync_test")

        val callback = PyObject.fromJava { stage: String, progress: Int ->
            onProgress(
                ProgressUpdate(
                    stage, (progress.coerceIn(0, 100)) / 100f
                )
            )
            null
        }

        // -------------------------
        // 1. Get audio tracks
        // -------------------------
        val tracks = FfprobeUtil.getAudioTracks(filePath)

        if (tracks.size < 2) {
            throw IllegalArgumentException("Need at least 2 audio tracks")
        }

        val refIdx = tracks[0].getInt("index")
        val otherIdx = tracks[1].getInt("index")

        // -------------------------
        // 2. Prepare output WAV files
        // -------------------------
        val cacheDir = context.cacheDir
        val refWav = File(cacheDir, "ref.wav")
        val otherWav = File(cacheDir, "other.wav")

        refWav.delete()
        otherWav.delete()

        // -------------------------
        // 3. Compute midpoint window
        // -------------------------
        val duration = getDurationSeconds(filePath)

        val clipDuration = 60.0 // 10 minutes
        val start = (duration / 2.0) - (clipDuration / 2.0)

        // clamp to valid range
        val safeStart = maxOf(0.0, start)

        // -------------------------
        // 4. Extract only middle chunk
        // -------------------------
        onProgress(ProgressUpdate("Extracting Audio", 0.1f))

        FFmpegKit.execute(
            "-y -ss $safeStart -t $clipDuration -i \"$filePath\" -map 0:$refIdx -ac 1 -ar 44100 \"$refWav\""
        )

        onProgress(ProgressUpdate("Extracting Audio", 0.4f))

        FFmpegKit.execute(
            "-y -ss $safeStart -t $clipDuration -i \"$filePath\" -map 0:$otherIdx -ac 1 -ar 44100 \"$otherWav\""
        )

        onProgress(ProgressUpdate("Extracting Audio", 0.7f))

        // -------------------------
        // 4. Call Python
        // -------------------------
        val result = module.callAttr(
            "run_detection", refWav.absolutePath, otherWav.absolutePath, callback
        )

        return result.toDouble()
    }

    fun getDurationSeconds(filePath: String): Double {
        val session = com.arthenica.ffmpegkit.FFprobeKit.execute(
            "-v quiet -print_format json -show_format \"$filePath\""
        )

        val output = session.output
        val json = org.json.JSONObject(output)
        val format = json.getJSONObject("format")

        return format.getString("duration").toDouble()
    }

    fun jsonToMap(json: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = json.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = json.get(key)
        }

        return map
    }
}