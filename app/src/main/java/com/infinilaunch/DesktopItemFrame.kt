package com.infinilaunch

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/** Keeps the source touch active while its desktop preview follows the pointer. */
internal class DesktopItemFrame(
    context: Context,
    private val workspace: WorkspaceView,
    private val item: Programs.DesktopEntry,
    private val preview: () -> View,
    open: () -> Unit,
    menu: () -> Unit,
    private val lockedLongPress: () -> Unit,
    private val drop: (Programs.DesktopEntry, Float, Float) -> Boolean,
) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var moved = false
    private var held = false
    private val arm = Runnable {
        if (workspace.isLayoutLocked) {
            held = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            lockedLongPress()
        } else {
            held = workspace.armDesktopMove(this, startX, startY, item.isLink, preview)
            if (held) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    init {
        contentDescription = item.label
        setOnClickListener { open() }
        setOnLongClickListener {
            menu()
            true
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                moved = false
                held = false
                isPressed = true
                postDelayed(arm, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                trackMovement(event)
                if (held && moved) workspace.updateDesktopMove(this, event.rawX, event.rawY)
            }
            MotionEvent.ACTION_UP -> {
                trackMovement(event)
                release()
                val position = workspace.finishDesktopMove(this, event.rawX, event.rawY)
                when {
                    position != null && moved -> drop(item, position.x, position.y)
                    position != null -> performLongClick()
                    !held && !moved -> performClick()
                }
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                moved = true
                release()
                workspace.cancelDesktopMove(this)
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        release()
        workspace.cancelDesktopMove(this)
        super.onDetachedFromWindow()
    }

    private fun trackMovement(event: MotionEvent) {
        if (!moved && (abs(event.rawX - startX) > slop || abs(event.rawY - startY) > slop)) {
            moved = true
            release()
        }
    }

    private fun release() {
        removeCallbacks(arm)
        isPressed = false
    }
}
