package com.rfid.rolltrack.scanner

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class Feedback(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var toneGen: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    } catch (_: Exception) {
        null
    }

    fun tagAccepted() {
        toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
        vibrate(40)
    }

    fun duplicate() {
        toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 100)
        vibrate(80)
    }

    fun warn() {
        toneGen?.startTone(ToneGenerator.TONE_PROP_NACK, 180)
        vibrate(150)
    }

    fun error() {
        toneGen?.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 250)
        vibrate(250)
    }

    private fun vibrate(ms: Long) {
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
        }
    }
}
