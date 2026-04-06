package com.danngalann.livecaption.ui.home

import android.Manifest
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresPermission

class ActionFeedback(context: Context) {

    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val toneGenerator = runCatching {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
    }.getOrNull()

    fun playStart() {
        play(
            toneType = ToneGenerator.TONE_PROP_BEEP2,
            toneDurationMs = 120,
            vibrationPattern = longArrayOf(0L, 35L)
        )
    }

    fun playStop() {
        play(
            toneType = ToneGenerator.TONE_PROP_BEEP,
            toneDurationMs = 90,
            vibrationPattern = longArrayOf(0L, 30L, 45L, 30L)
        )
    }

    fun playClear() {
        play(
            toneType = ToneGenerator.TONE_PROP_ACK,
            toneDurationMs = 70,
            vibrationPattern = longArrayOf(0L, 20L, 30L, 20L, 30L, 20L)
        )
    }

    fun release() {
        toneGenerator?.release()
    }

    private fun play(
        toneType: Int,
        toneDurationMs: Int,
        vibrationPattern: LongArray
    ) {
        toneGenerator?.startTone(toneType, toneDurationMs)
        vibrate(vibrationPattern)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate(pattern: LongArray) {
        val deviceVibrator = vibrator ?: return
        if (!deviceVibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            deviceVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            deviceVibrator.vibrate(pattern, -1)
        }
    }
}


