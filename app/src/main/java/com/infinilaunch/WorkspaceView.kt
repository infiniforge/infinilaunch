package com.infinilaunch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.PixelCopy
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.PopupWindow
import kotlin.math.roundToInt

/** Displays the shared workspace and measures all placements in the configured grid. */
internal class WorkspaceView(context: Context) : FrameLayout(context) {
    private val widgets = linkedMapOf<WorkspaceItem, View>()
    private var layout = WorkspaceLayout()
    private var editing = false
    private val margin = (6 * resources.displayMetrics.density).roundToInt()
    private var desktopMove: DesktopMove? = null
    var dockHitTest: ((Float, Float) -> Boolean)? = null
    var onDesktopDragPointer: ((Float, Float, Boolean) -> Unit)? = null
    private var acceptingDrop = false
    private var dropCell: WorkspacePlacement? = null
    private val dropGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x77FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }
    private val dropCellPaint = Paint().apply { color = 0x6658C4ED }

    private data class DesktopMove(
        val source: View,
        val alpha: Float,
        val startX: Float,
        val startY: Float,
        val screenX: Int,
        val screenY: Int,
        val isLink: Boolean,
        val overlay: DesktopMoveOverlay,
        val popup: PopupWindow?,
        var lastRawX: Float,
        var lastRawY: Float,
    )

    fun setWorkspace(value: WorkspaceLayout) {
        if (value != layout) cancelDesktopMove()
        layout = value
        updateVisibility()
        requestLayout()
    }

    fun setEditing(value: Boolean) {
        if (value) cancelDesktopMove()
        editing = value
        updateVisibility()
    }

    fun putWidget(item: WorkspaceItem, view: View) {
        val old = widgets.put(item, view)
        if (old !== view) {
            old?.let(::removeView)
            addView(view, LayoutParams(0, 0, Gravity.TOP or Gravity.LEFT))
        }
        updateVisibility()
    }

    fun removeWidget(item: WorkspaceItem) {
        widgets.remove(item)?.let(::removeView)
    }

    private fun updateVisibility() {
        val placed = layout.placements()
        for ((item, view) in widgets) {
            val visibility = when {
                item !in placed -> GONE
                editing -> INVISIBLE
                else -> VISIBLE
            }
            if (view.visibility != visibility) view.visibility = visibility
        }
    }

    fun armDesktopMove(source: View, rawX: Float, rawY: Float, isLink: Boolean, preview: () -> View): Boolean {
        if (editing || desktopMove != null || !source.isShown || width <= 0 || height <= 0) return false
        val location = IntArray(2)
        getLocationOnScreen(location)
        val lines = WorkspaceGeometry(layout.grid, width, height).gridLines()
        val overlay = DesktopMoveOverlay(context, lines, source, preview)
        val popup = PopupWindow(overlay, width, maxOf(height, rootView.height), false).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isTouchable = false
            isAttachedInDecor = false
            setIsLaidOutInScreen(true)
            isClippingEnabled = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            animationStyle = 0
        }
        val displayed = try {
            popup.showAtLocation(this, Gravity.TOP or Gravity.LEFT, location[0], location[1])
            popup
        } catch (_: WindowManager.BadTokenException) {
            popup.dismiss()
            addView(overlay, LayoutParams(-1, -1))
            null
        }
        desktopMove = DesktopMove(source, source.alpha, rawX, rawY, location[0], location[1], isLink,
            overlay, displayed, rawX, rawY)
        return true
    }

    fun updateDesktopMove(source: View, rawX: Float, rawY: Float) {
        val move = desktopMove?.takeIf { it.source === source } ?: return
        move.lastRawX = rawX
        move.lastRawY = rawY
        val overDock = dockHitTest?.invoke(rawX, rawY) == true
        move.overlay.move(rawX - move.startX, rawY - move.startY, asLink = !move.isLink && overDock)
        onDesktopDragPointer?.invoke(rawX, rawY, true)
        source.alpha = 0f
    }

    fun finishDesktopMove(source: View, rawX: Float, rawY: Float): PointF? {
        val move = desktopMove?.takeIf { it.source === source } ?: return null
        cancelDesktopMove(source)
        return PointF(rawX - move.screenX, rawY - move.screenY)
    }

    fun cancelDesktopMove(source: View? = null) {
        val move = desktopMove ?: return
        if (source != null && source !== move.source) return
        desktopMove = null
        move.source.alpha = move.alpha
        onDesktopDragPointer?.invoke(move.lastRawX, move.lastRawY, false)
        if (move.popup != null) move.popup.dismiss() else removeView(move.overlay)
    }

    fun showDropGrid(visible: Boolean, cell: WorkspacePlacement? = null) {
        if (acceptingDrop == visible && dropCell == cell) return
        acceptingDrop = visible
        dropCell = cell
        invalidate()
    }

    override fun onDrawForeground(canvas: Canvas) {
        super.onDrawForeground(canvas)
        if (!acceptingDrop) return
        val geometry = WorkspaceGeometry(layout.grid, width, height)
        canvas.drawPath(geometry.gridLines(), dropGridPaint)
        dropCell?.let { canvas.drawRect(geometry.bounds(it, 0), dropCellPaint) }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cancelDesktopMove()
    }

    override fun onDetachedFromWindow() {
        cancelDesktopMove()
        super.onDetachedFromWindow()
    }

    fun capturePreviews(
        window: Window,
        skip: WorkspaceItem?,
        onPreview: (WorkspaceItem, Bitmap) -> Unit,
        onComplete: () -> Unit,
    ) {
        var pending = 1
        fun complete() {
            if (--pending == 0) onComplete()
        }
        for ((item, view) in widgets) {
            if (item == skip || !view.isShown || view.width <= 0 || view.height <= 0) continue
            val ratio = minOf(1f, 768f / maxOf(view.width, view.height))
            val width = (view.width * ratio).roundToInt().coerceAtLeast(1)
            val height = (view.height * ratio).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val drawn = runCatching {
                view.draw(Canvas(bitmap).apply {
                    scale(width.toFloat() / view.width, height.toFloat() / view.height)
                })
            }.isSuccess
            if (drawn) {
                onPreview(item, bitmap)
                continue
            }
            // Some widget images can only be drawn by Android's hardware renderer.
            val location = IntArray(2)
            view.getLocationInWindow(location)
            val source = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
            pending++
            try {
                PixelCopy.request(window, source, bitmap, { result ->
                    if (result == PixelCopy.SUCCESS) onPreview(item, bitmap) else bitmap.recycle()
                    complete()
                }, Handler(Looper.getMainLooper()))
            } catch (_: IllegalArgumentException) {
                bitmap.recycle()
                complete()
            }
        }
        complete()
    }

    fun cellAt(x: Float, y: Float): WorkspacePlacement? {
        if (width <= 0 || height <= 0 || x < 0f || x >= width || y < 0f || y >= height) return null
        val grid = WorkspaceGeometry(layout.grid, width, height)
        val column = (x / grid.columnStride).toInt().coerceAtMost(layout.grid.columns - 1)
        val row = (y / grid.rowStride).toInt().coerceAtMost(layout.grid.rows - 1)
        return WorkspacePlacement(column, row, 1, 1)
    }

    fun canPlace(cell: WorkspacePlacement, replace: WorkspaceItem? = null): Boolean =
        cell.fits(layout.grid) && layout.placements().none { (item, existing) ->
            item != replace && cell.overlaps(existing)
        }

    fun findSpace(minWidth: Int, minHeight: Int, minSpan: Int = 1): WorkspacePlacement? {
        if (width == 0 || height == 0) return null
        val grid = layout.grid
        val geometry = WorkspaceGeometry(grid, width, height)
        val minRows = minOf(minSpan, grid.rows)
        if (minSpan >= 2) {
            val preferred = WorkspacePlacement(grid.columns - 3, grid.rows - minRows, 3, minRows)
            if (canPlace(preferred)) return preferred
        }
        for (rows in minRows..grid.rows) {
            for (cols in minSpan..grid.columns) {
                for (row in 0..grid.rows - rows) {
                    for (column in 0..grid.columns - cols) {
                        val candidate = WorkspacePlacement(column, row, cols, rows)
                        val rect = geometry.bounds(candidate, margin)
                        if (rect.width() >= minWidth && rect.height() >= minHeight && canPlace(candidate)) {
                            return candidate
                        }
                    }
                }
            }
        }
        return null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val geometry = WorkspaceGeometry(layout.grid, width, height)
        for ((item, position) in layout.placements()) {
            val view = widgets[item] ?: continue
            val inset = (item.insetDp * resources.displayMetrics.density).roundToInt()
            val rect = geometry.bounds(position, inset)
            val params = view.layoutParams as LayoutParams
            params.width = rect.width()
            params.height = rect.height()
            params.leftMargin = rect.left
            params.topMargin = rect.top
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

internal val WorkspaceItem.insetDp: Int get() = if (this is WorkspaceItem.Launcher) 1 else 6

/** Maps the grid to the entire workspace and shares its edges with the editor. */
internal class WorkspaceGeometry(
    private val grid: WorkspaceGrid,
    private val width: Int,
    private val height: Int,
) {
    val columnStride = width.toFloat() / grid.columns
    val rowStride = height.toFloat() / grid.rows
    private val maxInset = minOf(width / grid.columns, height / grid.rows) / 4

    fun x(column: Int) = width * column / grid.columns
    fun y(row: Int) = height * row / grid.rows

    fun gridLines(): Path = Path().apply {
        addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        for (column in 1 until grid.columns) {
            val px = x(column).toFloat()
            moveTo(px, 0f)
            lineTo(px, height.toFloat())
        }
        for (row in 1 until grid.rows) {
            val py = y(row).toFloat()
            moveTo(0f, py)
            lineTo(width.toFloat(), py)
        }
    }

    fun bounds(cell: WorkspacePlacement, margin: Int): Rect {
        val inset = minOf(margin, maxInset)
        return Rect(
            x(cell.column) + inset,
            y(cell.row) + inset,
            x(cell.column + cell.width) - inset,
            y(cell.row + cell.height) - inset,
        )
    }
}
