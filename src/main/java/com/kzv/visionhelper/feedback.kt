package com.kzv.visionhelper

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

class FeedbackManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("vision_settings", Context.MODE_PRIVATE)
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private var lastFeedbackTime = 0L
    private val cooldown: Long
        get() = prefs.getInt("feedback_cooldown", 500).toLong()


    fun playFeedback(label: String) {
        val now = System.currentTimeMillis()
        if (now - lastFeedbackTime < cooldown) return
        lastFeedbackTime = now

        val enableVibration = prefs.getBoolean("vibration", true)
        val enableSound = prefs.getBoolean("sound", true)

        val feedback = comboFeedbackMap[label] ?: comboFeedbackMap["default"]!!

        if (enableVibration) vibratePattern(feedback.vibration)
        if (enableSound) toneGenerator.startTone(feedback.tone, 200)
    }

    fun playComboFeedback(labels: List<String>) {
        val key = labels.distinct().sorted().joinToString("+")
        Log.d("Feedback", "Combo key: $key")

        playFeedback(key)
    }

    private fun vibratePattern(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            vibrator.vibrate(pattern, -1)
        }
    }

    data class FeedbackPattern(val vibration: LongArray, val tone: Int)

    private val comboFeedbackMap = mapOf(
        // Solo detections
        "Green light for car" to FeedbackPattern(longArrayOf(0, 100, 100, 100), ToneGenerator.TONE_PROP_ACK),
        "Red light for car" to FeedbackPattern(longArrayOf(0, 150, 100, 150), ToneGenerator.TONE_PROP_NACK),
        "Traffic light (green)" to FeedbackPattern(longArrayOf(0, 80, 80, 80), ToneGenerator.TONE_PROP_ACK),
        "Traffic light (red)" to FeedbackPattern(longArrayOf(0, 300), ToneGenerator.TONE_PROP_NACK),
        "Pedestrian crossing" to FeedbackPattern(longArrayOf(0, 100, 70, 100, 70, 100), ToneGenerator.TONE_SUP_BUSY),
        "Crossing sign" to FeedbackPattern(longArrayOf(0, 200, 200, 300), ToneGenerator.TONE_SUP_BUSY),

        // Confirmed danger
        "Green light for car+Traffic light (red)" to FeedbackPattern(longArrayOf(0, 300, 100, 300), ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD),
        "Green light for car+Pedestrian crossing" to FeedbackPattern(longArrayOf(0, 300, 80, 300), ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD),
        "Traffic light (green)+Red light for car" to FeedbackPattern(longArrayOf(0, 300, 80, 300), ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD),

        // Alert combos
        "Pedestrian crossing+Crossing sign" to FeedbackPattern(longArrayOf(0, 100, 70, 100, 70, 200), ToneGenerator.TONE_SUP_BUSY),
        "Traffic light (red)+Pedestrian crossing" to FeedbackPattern(longArrayOf(0, 250, 100, 250), ToneGenerator.TONE_SUP_BUSY),
        "Green light for car+Crossing sign" to FeedbackPattern(longArrayOf(0, 80, 80, 150), ToneGenerator.TONE_SUP_BUSY),
        "Crossing sign+Traffic light (red)" to FeedbackPattern(longArrayOf(0, 200, 100, 300), ToneGenerator.TONE_SUP_RADIO_ACK),

        // Extended stop conditions
        "Traffic light (red)+Red light for car" to FeedbackPattern(longArrayOf(0, 400), ToneGenerator.TONE_PROP_NACK),
        "Red light for car+Pedestrian crossing" to FeedbackPattern(longArrayOf(0, 250, 150, 250), ToneGenerator.TONE_PROP_NACK),

        // Clear to go
        "Traffic light (green)+Green light for car" to FeedbackPattern(longArrayOf(0, 100, 100, 100), ToneGenerator.TONE_PROP_ACK),
        "Green light for car+Traffic light (green)" to FeedbackPattern(longArrayOf(0, 90, 90, 90), ToneGenerator.TONE_PROP_ACK),

        // Large mixed combo
        "Crossing sign+Pedestrian crossing+Traffic light (red)" to FeedbackPattern(longArrayOf(0, 150, 80, 150, 80, 250), ToneGenerator.TONE_SUP_ERROR),
        "Green light for car+Traffic light (red)+Crossing sign" to FeedbackPattern(longArrayOf(0, 350, 200, 350), ToneGenerator.TONE_SUP_CONGESTION),

        // Additional combos
        "Green light for car+Red light for car" to FeedbackPattern(longArrayOf(0, 228, 211), ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD),
        "Crossing sign+Green light for car" to FeedbackPattern(longArrayOf(0, 133, 192), ToneGenerator.TONE_SUP_RADIO_ACK),
        "Red light for car+Traffic light (green)" to FeedbackPattern(longArrayOf(0, 166, 273, 225, 144, 168), ToneGenerator.TONE_SUP_ERROR),
        "Red light for car+Traffic light (red)" to FeedbackPattern(longArrayOf(0, 101, 229, 144), ToneGenerator.TONE_PROP_ACK),
        "Pedestrian crossing+Red light for car" to FeedbackPattern(longArrayOf(0, 146, 82, 200, 260, 197), ToneGenerator.TONE_PROP_ACK),
        "Crossing sign+Red light for car" to FeedbackPattern(longArrayOf(0, 145, 92, 164, 127, 254), ToneGenerator.TONE_PROP_NACK),
        "Traffic light (green)+Traffic light (red)" to FeedbackPattern(longArrayOf(0, 119, 201, 135), ToneGenerator.TONE_SUP_ERROR),
        "Pedestrian crossing+Traffic light (green)" to FeedbackPattern(longArrayOf(0, 214, 133, 124, 105), ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD),
        "Crossing sign+Traffic light (green)" to FeedbackPattern(longArrayOf(0, 222, 214), ToneGenerator.TONE_SUP_CONGESTION),
        "Pedestrian crossing+Traffic light (red)" to FeedbackPattern(longArrayOf(0, 220, 274), ToneGenerator.TONE_SUP_CONGESTION),
        "Red light for car+Crossing sign+Traffic light (red)" to FeedbackPattern(longArrayOf(0, 250, 250, 300), ToneGenerator.TONE_SUP_CONGESTION),
        "Green light for car+Traffic light (red)+Pedestrian crossing" to FeedbackPattern(longArrayOf(0, 300, 100, 100, 300), ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD),
        "Traffic light (green)+Pedestrian crossing+Crossing sign" to FeedbackPattern(longArrayOf(0, 100, 80, 100, 250), ToneGenerator.TONE_SUP_CONGESTION),
        "Green light for car+Crossing sign+Pedestrian crossing" to FeedbackPattern(longArrayOf(0, 100, 80, 200), ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD),

        // Fallback
        "default" to FeedbackPattern(longArrayOf(0, 150), ToneGenerator.TONE_PROP_BEEP)
    )
}
