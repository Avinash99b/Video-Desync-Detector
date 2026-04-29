package com.aviansh.audiodesyncdetector.util

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode

object FfprobeUtil {
    fun getAudioTracks(videoPath: String): List<JSONObject> {
        Log.d("FFprobe", "Running FFprobe")

        return try {
            val session = FFprobeKit.execute(
                "-v quiet -print_format json -show_streams \"$videoPath\""
            )

            if (!ReturnCode.isSuccess(session.returnCode)) {
                Log.e("FFprobe", "FFprobe failed: ${session.failStackTrace}")
                return emptyList()
            }

            val output = session.output
            val json = JSONObject(output)
            val streams = json.getJSONArray("streams")

            val audioTracks = mutableListOf<JSONObject>()

            for (i in 0 until streams.length()) {
                val stream = streams.getJSONObject(i)
                if (stream.getString("codec_type") == "audio") {
                    audioTracks.add(stream)
                }
            }

            audioTracks

        } catch (e: Exception) {
            Log.e("FFprobe", "Exception: ${e.message}")
            emptyList()
        }
    }
}