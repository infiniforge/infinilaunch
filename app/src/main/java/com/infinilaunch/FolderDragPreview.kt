package com.infinilaunch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import kotlin.math.roundToInt

/** Displays the dragged file artwork while Folder continues to own the touch. */
internal class FolderDragPreview(private val context: Context) {
    private val diameter = (52 * context.resources.displayMetrics.density).roundToInt()
    private var popup: PopupWindow? = null
    private var linkBadge: TextView? = null
    private var sourceIsLink = true

    fun open(anchor: View, bitmap: Bitmap?, point: PointF, isLink: Boolean) {
        close()
        sourceIsLink = isLink
        val image = ImageView(context).apply {
            if (bitmap != null) setImageBitmap(bitmap) else setImageResource(android.R.drawable.ic_menu_save)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val badge = TextView(context).apply {
            text = "LINK"
            textSize = 9f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(5), 0, dp(5), 0)
            background = GradientDrawable().apply {
                setColor(0xFF287D75.toInt())
                cornerRadius = dp(8).toFloat()
            }
            visibility = View.GONE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val frame = FrameLayout(context).apply {
            addView(image, FrameLayout.LayoutParams(-1, -1))
            addView(badge, FrameLayout.LayoutParams(-2, dp(17), Gravity.BOTTOM or Gravity.RIGHT).apply {
                rightMargin = dp(1)
                bottomMargin = dp(1)
            })
        }
        linkBadge = badge
        val shadow = PopupWindow(frame, diameter, diameter, false).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isTouchable = false
            isClippingEnabled = false
            isAttachedInDecor = false
            setIsLaidOutInScreen(true)
            animationStyle = 0
        }
        try {
            shadow.showAtLocation(anchor, Gravity.TOP or Gravity.LEFT,
                point.x.roundToInt() - diameter / 2, point.y.roundToInt() - diameter / 2)
            popup = shadow
        } catch (_: WindowManager.BadTokenException) {
            shadow.dismiss()
            linkBadge = null
        }
    }

    fun move(point: PointF, showAsLink: Boolean) {
        popup?.update(point.x.roundToInt() - diameter / 2, point.y.roundToInt() - diameter / 2, -1, -1)
        setLinkMode(showAsLink && !sourceIsLink)
    }

    private fun setLinkMode(value: Boolean) {
        linkBadge?.visibility = if (value) View.VISIBLE else View.GONE
    }

    fun close() {
        popup?.dismiss()
        popup = null
        linkBadge = null
        sourceIsLink = true
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
