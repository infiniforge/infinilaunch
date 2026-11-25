package com.infinilaunch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout

/** Passes touch to the source tile while drawing above embedded folder surfaces. */
internal class DesktopMoveOverlay(
    context: Context,
    private val lines: Path,
    private val source: View,
    private val previewFactory: () -> View,
) : FrameLayout(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88ECF8FF.toInt()
        strokeWidth = resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 9f * resources.displayMetrics.scaledDensity
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private var preview: View? = null
    private var showLinkBadge = false

    init {
        setWillNotDraw(false)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    fun move(dx: Float, dy: Float, asLink: Boolean) {
        val tile = preview ?: previewFactory().also {
            addView(it, LayoutParams(source.width, source.height, Gravity.TOP or Gravity.LEFT))
            preview = it
        }
        tile.translationX = source.left + dx
        tile.translationY = source.top + dy
        showLinkBadge = asLink
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(lines, paint)
    }

    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)
        val tile = preview?.takeIf { showLinkBadge } ?: return
        val density = resources.displayMetrics.density
        val width = 38f * density
        val height = 17f * density
        val right = tile.translationX + tile.width - 2f * density
        val bottom = tile.translationY + tile.height - 2f * density
        val badge = RectF(right - width, bottom - height, right, bottom)
        badgePaint.color = 0xFF287D75.toInt()
        canvas.drawRoundRect(badge, 8f * density, 8f * density, badgePaint)
        canvas.drawText("LINK", badge.centerX(), badge.centerY() - (badgeTextPaint.ascent() + badgeTextPaint.descent()) / 2f,
            badgeTextPaint)
    }
}
