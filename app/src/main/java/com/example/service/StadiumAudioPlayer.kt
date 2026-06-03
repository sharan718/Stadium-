package com.example.service

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

class StadiumAudioPlayer {
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (e: Exception) {
            Log.e("StadiumAudioPlayer", "Failed to initialize ToneGenerator", e)
        }
    }

    fun playScanSuccess() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
        } catch (e: Exception) {
            Log.e("StadiumAudioPlayer", "Error playing success scan chime", e)
        }
    }

    fun playEmergencyAlert() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_ERROR, 400)
        } catch (e: Exception) {
            Log.e("StadiumAudioPlayer", "Error playing emergency hazard beep", e)
        }
    }

    fun playPanicAlarm() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800)
        } catch (e: Exception) {
            Log.e("StadiumAudioPlayer", "Error playing absolute panic alert sound", e)
        }
    }
}
