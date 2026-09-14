package com.farematrix.clicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

class AutoClickService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AutoClickService? = null
            private set

        /**
         * Check whether our service is actually enabled in system settings,
         * not just whether the static instance is alive.
         */
        fun isEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false
            val enabled = am.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            for (info in enabled) {
                val name = info.resolveInfo.serviceInfo.name ?: continue
                if (name == AutoClickService::class.java.name) return true
            }
            return false
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * Fire a single tap at absolute screen coordinates.
     * The [onDone] callback is invoked ONLY after Android confirms the
     * gesture completed or was cancelled — this is what lets us chain taps
     * without the system cancelling the previous one.
     */
    fun tapAt(x: Float, y: Float, onDone: () -> Unit) {
        // Tap duration must be >= ViewConfiguration.getTapTimeout() (115ms default)
        // or Android drops the gesture as a "non-event".
        val tapDuration = ViewConfiguration.getTapTimeout().toLong().coerceAtLeast(115L)

        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, tapDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        var callbackFired = false

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (callbackFired) return
                callbackFired = true
                mainHandler.post { onDone() }
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (callbackFired) return
                callbackFired = true
                mainHandler.post { onDone() }
            }
        }, mainHandler)

        // Safety net — if the system refuses to dispatch at all, don't hang the loop.
        if (!dispatched && !callbackFired) {
            callbackFired = true
            mainHandler.postDelayed({ onDone() }, tapDuration + 50L)
        }
    }

    /**
     * Convenience overload for fire-and-forget single taps (used by manual
     * tests or individual taps from the UI). Uses a no-op completion handler.
     */
    fun tapOnce(x: Float, y: Float) {
        tapAt(x, y) { /* nothing */ }
    }
}
