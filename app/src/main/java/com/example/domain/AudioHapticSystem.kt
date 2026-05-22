package com.example.domain

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.media.AudioManager
import android.media.ToneGenerator

class AudioHapticSystem(private val context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 75)
        } catch (e: Exception) {
            // fallback gracefully
        }
    }

    fun vibrateCrash() {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(200)
                }
            }
        } catch (e: Exception) {
            // ignored
        }
    }

    fun vibrateCollect() {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(45, 160))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(45)
                }
            }
        } catch (e: Exception) {
            // ignored
        }
    }

    fun playCrashSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_ERROR, 220)
        } catch (e: Exception) {
            // ignored
        }
    }

    fun playCollectSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
        } catch (e: Exception) {
            // ignored
        }
    }

    fun playCheckpointSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_PIP, 280)
        } catch (e: Exception) {
            // ignored
        }
    }

    fun playBoostSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_CONGESTION, 180)
        } catch (e: Exception) {
            // ignored
        }
    }

    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
