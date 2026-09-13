package com.farematrix.clicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class AutoClickService : AccessibilityService() {
    companion object { var instance: AutoClickService? = null }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; super.onDestroy() }

    fun tapAt(x: Float, y: Float, onResult: ((Boolean) -> Unit)? = null): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val callback = if (onResult == null) null else object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onResult(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                onResult(false)
            }
        }
        return dispatchGesture(gesture, callback, null)
    }
}
