package com.infinilaunch

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import io.github.gretchycat.infiniselect.EmbeddedFileBrowser
import java.util.function.Consumer
import kotlin.math.roundToInt

/** Places a compositor blur behind each visible embedded browser. */
internal class BrowserBackdrops(
    private val activity: Activity,
    private val root: View,
    vararg slots: FrameLayout,
) {
    private val panes = slots.map(::Pane)
    private val location = IntArray(2)
    private var blurEnabled = false
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        panes.forEach { it.update() }
        true
    }
    private val blurListener = Consumer<Boolean> { enabled ->
        blurEnabled = enabled
        root.invalidate()
    }

    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        activity.windowManager.addCrossWindowBlurEnabledListener(blurListener)
        root.viewTreeObserver.addOnPreDrawListener(preDraw)
    }

    fun stop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        root.viewTreeObserver.removeOnPreDrawListener(preDraw)
        activity.windowManager.removeCrossWindowBlurEnabledListener(blurListener)
        blurEnabled = false
        panes.forEach { it.hide() }
    }

    private inner class Pane(private val slot: FrameLayout) {
        private var backdrop: Dialog? = null
        private var attachFailed = false

        fun update() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                !blurEnabled || !slot.isShown || slot.windowVisibility != View.VISIBLE ||
                slot.getChildAt(0) !is EmbeddedFileBrowser || slot.width <= 0 || slot.height <= 0
            ) {
                hide()
                return
            }
            val token = slot.windowToken ?: return
            if (attachFailed) return
            slot.getLocationInWindow(location)
            val existing = backdrop?.window
            if (existing != null) {
                val params = existing.attributes
                if (params.x != location[0] || params.y != location[1] ||
                    params.width != slot.width || params.height != slot.height
                ) {
                    existing.attributes = place(params)
                }
                return
            }
            val dialog = Dialog(activity, R.style.Theme_InfiniLaunch_Backdrop)
            val window = requireNotNull(dialog.window)
            dialog.setContentView(View(activity).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_MEDIA)
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            window.attributes = place(window.attributes).apply {
                this.token = token
                gravity = Gravity.TOP or Gravity.LEFT
                setFitInsetsTypes(0)
            }
            window.setBackgroundBlurRadius((24f * root.resources.displayMetrics.density).roundToInt())
            try {
                dialog.show()
                backdrop = dialog
            } catch (unavailable: WindowManager.BadTokenException) {
                attachFailed = true
                Log.w(TAG, "Browser backdrop window is unavailable", unavailable)
            }
        }

        private fun place(params: WindowManager.LayoutParams) = params.apply {
            x = location[0]
            y = location[1]
            width = slot.width
            height = slot.height
        }

        fun hide() {
            backdrop?.dismiss()
            backdrop = null
            attachFailed = false
        }
    }

    private companion object {
        const val TAG = "InfiniLaunch"
    }
}
