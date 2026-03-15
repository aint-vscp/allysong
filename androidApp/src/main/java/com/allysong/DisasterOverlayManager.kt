package com.allysong

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.allysong.domain.model.DisasterEvent

// ============================================================================
// DisasterOverlayManager.kt
// ============================================================================
// Manages a floating chat-head style overlay that appears over other apps
// when AllySong detects a disaster while the user is outside the app.
//
// Design inspired by Messenger chat heads:
//   1. A pulsing red alert bubble appears in the corner of the screen.
//   2. Tapping the bubble expands it into an alert card showing:
//      - Disaster type (e.g., "EARTHQUAKE DETECTED")
//      - Confidence percentage
//      - "Tap to Respond" action
//   3. Tapping the card opens the app to the HITL validation screen.
//   4. The overlay also triggers vibration for haptic urgency.
//
// Requires SYSTEM_ALERT_WINDOW permission (draw over other apps).
// ============================================================================

class DisasterOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var overlayView: View? = null
    private var isShowing = false

    fun showDisasterAlert(event: DisasterEvent) {
        if (isShowing) dismiss()

        handler.post {
            try {
                val view = createOverlayView(event)
                val params = createLayoutParams()

                windowManager.addView(view, params)
                overlayView = view
                isShowing = true

                // Animate entrance
                animateEntrance(view)

                // Vibrate for attention
                vibrateAlert()

                // Auto-dismiss after 30 seconds if user doesn't interact
                handler.postDelayed({ dismiss() }, 30_000)
            } catch (e: Exception) {
                // Permission not granted or window manager error — fall back silently
                // (the notification will still be posted by the service)
                android.util.Log.w("AllySong", "Overlay failed: ${e.message}")
            }
        }
    }

    fun dismiss() {
        handler.post {
            overlayView?.let { view ->
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) { }
                overlayView = null
                isShowing = false
            }
        }
    }

    // ── Overlay view construction ─────────────────────────────────────────

    private fun createOverlayView(event: DisasterEvent): View {
        val dp = { value: Int ->
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                context.resources.displayMetrics
            ).toInt()
        }

        // Root container — semi-transparent scrim so content behind is visible
        val root = FrameLayout(context).apply {
            setPadding(dp(16), dp(48), dp(16), dp(16))
            setBackgroundColor(Color.argb(0x66, 0, 0, 0)) // 40% black scrim
        }

        // Alert card — semi-transparent dark background
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))

            // Rounded corners + semi-transparent dark background with red border
            background = GradientDrawable().apply {
                setColor(Color.argb(0xCC, 0x1A, 0x0A, 0x0A)) // ~80% opacity
                cornerRadius = dp(16).toFloat()
                setStroke(dp(2), Color.parseColor("#FF1744"))
            }

            // Shadow / elevation
            elevation = dp(8).toFloat()
        }

        // ── Header row: icon + disaster type ──
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Alert icon (using built-in Android drawable)
        val alertIcon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_dialog_alert)
            setColorFilter(Color.parseColor("#FF1744"))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                marginEnd = dp(10)
            }
        }

        // Disaster type text
        val disasterTypeText = TextView(context).apply {
            text = "${formatDisasterType(event)} DETECTED"
            setTextColor(Color.parseColor("#FF1744"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        // Dismiss X button
        val dismissBtn = TextView(context).apply {
            text = "\u2715"
            setTextColor(Color.parseColor("#999999"))
            textSize = 18f
            setPadding(dp(8), 0, 0, 0)
            setOnClickListener { dismiss() }
        }

        headerRow.addView(alertIcon)
        headerRow.addView(disasterTypeText)
        headerRow.addView(dismissBtn)

        // ── Confidence bar ──
        val confidenceText = TextView(context).apply {
            text = "Confidence: ${(event.confidence * 100).toInt()}%"
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 13f
            setPadding(0, dp(6), 0, dp(4))
        }

        // ── Description ──
        val descText = TextView(context).apply {
            text = "AllySong detected potential danger nearby.\nTap to open the app and respond."
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 12f
            setPadding(0, 0, 0, dp(10))
        }

        // ── Action button ──
        val actionBtn = TextView(context).apply {
            text = "  \u26A0  TAP TO RESPOND  "
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D50000"))
                cornerRadius = dp(8).toFloat()
            }

            setOnClickListener {
                openApp()
                dismiss()
            }
        }

        // ── Dismiss button (prominent, easy to tap) ──
        val dismissSafeBtn = TextView(context).apply {
            text = "DISMISS \u2014 I\u2019M SAFE"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(8).toFloat()
                setStroke(dp(2), Color.WHITE)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
            setOnClickListener { dismiss() }
        }

        // Assemble card
        card.addView(headerRow)
        card.addView(confidenceText)
        card.addView(descText)
        card.addView(actionBtn)
        card.addView(dismissSafeBtn)

        root.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        })

        // Make the background area dismissible
        root.setOnClickListener { dismiss() }
        card.setOnClickListener { /* consume to prevent dismiss */ }

        return root
    }

    // ── Layout params ────────────────────────────────────────────────────

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
    }

    // ── Animations ────────────────────────────────────────────────────────

    private fun animateEntrance(view: View) {
        view.translationY = -300f
        view.alpha = 0f

        view.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(0.8f))
            .start()

        // Pulse animation on the card for urgency
        val card = (view as FrameLayout).getChildAt(0)
        val pulseX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.02f, 1f)
        val pulseY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.02f, 1f)
        ObjectAnimator.ofPropertyValuesHolder(card, pulseX, pulseY).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            startDelay = 500
            start()
        }
    }

    // ── Vibration ─────────────────────────────────────────────────────────

    private fun vibrateAlert() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 500), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 500), -1)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 300, 150, 300, 150, 500), -1)
                }
            }
        } catch (_: Exception) { }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun openApp() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "HITL_VALIDATION")
        }
        context.startActivity(intent)
    }

    private fun formatDisasterType(event: DisasterEvent): String {
        return event.type.name
            .replace("_", " ")
            .uppercase()
    }
}
