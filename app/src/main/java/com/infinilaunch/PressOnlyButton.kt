package com.infinilaunch

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.Button
import kotlin.math.abs

/** A button whose action is a tap; moving a pressed pointer never activates it. */
internal class PressOnlyButton(context: Context) : Button(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragged = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragged = false
                return super.onTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragged && (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop)) {
                    dragged = true
                    isPressed = false
                    cancelLongPress()
                }
                if (dragged) return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                dragged = true
                isPressed = false
                cancelLongPress()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragged) {
                    dragged = false
                    isPressed = false
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
