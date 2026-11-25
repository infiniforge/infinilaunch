package com.infinilaunch

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout

/** Observes holds across the whole hosted view without intercepting ordinary widget taps. */
internal class WidgetHoldFrame(context: Context, edit: () -> Boolean) : FrameLayout(context) {
    private var held = false
    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent) = true

        override fun onLongPress(event: MotionEvent) {
            if (performLongClick()) {
                held = true
                cancelChildTouch(event)
            }
        }
    })

    init {
        isClickable = true
        setOnLongClickListener { edit() }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) held = false
        detector.onTouchEvent(event)
        return held || super.dispatchTouchEvent(event)
    }

    private fun cancelChildTouch(event: MotionEvent) {
        val cancel = MotionEvent.obtain(event)
        try {
            cancel.action = MotionEvent.ACTION_CANCEL
            super.dispatchTouchEvent(cancel)
        } finally {
            cancel.recycle()
        }
    }
}
