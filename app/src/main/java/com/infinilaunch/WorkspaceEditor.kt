package com.infinilaunch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.roundToInt

/** Edits every workspace placement as one draft, with transient overlap allowed. */
internal class WorkspaceEditor(
    context: Context,
    private val original: WorkspaceLayout,
    initialItem: WorkspaceItem,
    initialTitle: String,
) : View(context) {
    private val appContext = context
    val originalItem = initialItem
    var item: WorkspaceItem? = initialItem
        private set
    private val positions = original.placements().toMutableMap()
    private val labels = mutableMapOf(initialItem to initialTitle)
    private val previews = mutableMapOf<WorkspaceItem, Bitmap>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private lateinit var gridLines: Path
    private val handleRadius = dp(6f)
    private val hitRadius = dp(23f)
    private var drag: Drag? = null
    private lateinit var geometry: WorkspaceGeometry
    var onDraftChanged: (() -> Unit)? = null
    val placement: WorkspacePlacement? get() = item?.let(positions::get)
    val selectedLabel: String get() = item?.let(::labelFor) ?: appContext.getString(R.string.no_desktop_items)
    val selectedIndex: Int get() = item?.let { positions.keys.indexOf(it) + 1 } ?: 0
    val itemCount: Int get() = positions.size
    val invalidItems: Set<WorkspaceItem> get() = draftLayout().invalidItems()
    val isValid: Boolean get() = invalidItems.isEmpty()
    val wasProgramsRemoved: Boolean get() = originalItem == WorkspaceItem.Programs &&
        WorkspaceItem.Programs !in positions

    private data class Drag(
        val item: WorkspaceItem,
        val start: WorkspacePlacement,
        val x: Float,
        val y: Float,
        val horizontalEdge: Int,
        val verticalEdge: Int,
    )

    init {
        contentDescription = context.getString(R.string.desktop_reorganization_hint)
        isClickable = true
    }

    fun setLabels(value: Map<WorkspaceItem, String>) {
        labels.putAll(value)
        invalidate()
    }

    fun setPreview(item: WorkspaceItem, image: Bitmap) {
        previews[item] = image
        invalidate()
    }

    fun draftLayout(): WorkspaceLayout = positions.entries.fold(original) { current, (key, value) ->
        current.withPlacement(key, value)
    }.let { current ->
        (original.placements().keys - positions.keys).fold(current) { reduced, removed ->
            reduced.withPlacement(removed, null)
        }
    }

    fun selectPrevious() = selectBy(-1)
    fun selectNext() = selectBy(1)

    private fun selectBy(step: Int) {
        val keys = positions.keys.toList()
        if (keys.isEmpty()) {
            item = null
        } else {
            val index = item?.let(keys::indexOf)?.takeIf { it >= 0 } ?: 0
            item = keys[(index + step + keys.size) % keys.size]
        }
        drag = null
        notifyDraftChanged()
    }

    fun removeSelected(): WorkspaceItem? {
        val selected = item ?: return null
        positions.remove(selected)
        val next = positions.keys.firstOrNull()
        item = next
        drag = null
        notifyDraftChanged()
        return selected
    }

    override fun onDetachedFromWindow() {
        previews.clear()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        geometry = WorkspaceGeometry(original.grid, w, h)
        gridLines = geometry.gridLines()
        drag = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0x470A1821)
        paint.color = 0x88ECF8FF.toInt()
        paint.strokeWidth = dp(1f)
        paint.style = Paint.Style.STROKE
        canvas.drawPath(gridLines, paint)

        val invalid = invalidItems
        val active = item
        for ((key, position) in positions) {
            if (key != active) drawItem(canvas, key, position, selected = false, invalid = key in invalid)
        }
        if (active != null) {
            positions[active]?.let { drawItem(canvas, active, it, selected = true, invalid = active in invalid) }
        } else {
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dp(16f)
            canvas.drawText(selectedLabel, width / 2f, height / 2f, paint)
        }
    }

    private fun drawItem(
        canvas: Canvas,
        key: WorkspaceItem,
        position: WorkspacePlacement,
        selected: Boolean,
        invalid: Boolean,
    ) {
        val bounds = rect(position, key)
        val hasPreview = drawPreview(canvas, key, bounds)
        paint.style = Paint.Style.FILL
        paint.color = when {
            selected -> if (invalid) 0x5CFF594F else 0x4C8CDDD7
            invalid -> 0x38FF594F
            else -> 0x248CDDD7
        }
        canvas.drawRoundRect(bounds, dp(7f), dp(7f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (selected) dp(2f) else dp(1.5f)
        paint.color = when {
            invalid -> 0xFFFF8D80.toInt()
            selected -> 0xFFBDF8EF.toInt()
            else -> 0xA6DDE5E7.toInt()
        }
        canvas.drawRoundRect(bounds, dp(7f), dp(7f), paint)
        paint.style = Paint.Style.FILL

        if (selected && key !is WorkspaceItem.Launcher) {
            for (x in listOf(bounds.left, bounds.right)) {
                for (y in listOf(bounds.top, bounds.bottom)) {
                    paint.color = if (invalid) 0xFFFF8D80.toInt() else 0xFFBDF8EF.toInt()
                    canvas.drawCircle(x, y, handleRadius, paint)
                }
            }
        }
        if (!hasPreview) {
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dp(if (selected) 14f else 12f)
            canvas.save()
            canvas.clipRect(bounds)
            canvas.drawText(labelFor(key), bounds.centerX(), bounds.centerY() - (paint.ascent() + paint.descent()) / 2f, paint)
            canvas.restore()
        }
    }

    private fun labelFor(key: WorkspaceItem) = labels[key] ?: when (key) {
        WorkspaceItem.Programs -> appContext.getString(R.string.programs)
        WorkspaceItem.Clock -> appContext.getString(R.string.clock_widget)
        is WorkspaceItem.App -> appContext.getString(R.string.application_widget)
        is WorkspaceItem.Launcher -> key.filename.substringBeforeLast('.', key.filename)
    }

    private fun drawPreview(canvas: Canvas, item: WorkspaceItem, bounds: RectF): Boolean {
        val image = previews[item] ?: return false
        val scale = minOf(bounds.width() / image.width, bounds.height() / image.height)
        val halfWidth = image.width * scale / 2
        val halfHeight = image.height * scale / 2
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        canvas.drawBitmap(
            image,
            null,
            RectF(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight),
            bitmapPaint,
        )
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || width == 0 || height == 0) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = hitTest(event.x, event.y)
                if (hit != null) {
                    item = hit
                    val start = positions.getValue(hit)
                    val box = rect(start, hit)
                    val horizontal = edge(event.x, box.left, box.right)
                    val vertical = edge(event.y, box.top, box.bottom)
                    val corner = hit !is WorkspaceItem.Launcher && horizontal != 0 && vertical != 0
                    drag = Drag(start = start, item = hit, x = event.x, y = event.y,
                        horizontalEdge = if (corner) horizontal else 0,
                        verticalEdge = if (corner) vertical else 0)
                    notifyDraftChanged()
                } else {
                    drag = null
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateDrag(event)
                return true
            }
            MotionEvent.ACTION_UP -> {
                updateDrag(event)
                drag = null
                invalidate()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                drag?.let { positions[it.item] = it.start }
                drag = null
                notifyDraftChanged()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun hitTest(x: Float, y: Float): WorkspaceItem? {
        val order = positions.keys.filter { it != item } + listOfNotNull(item?.takeIf(positions::containsKey))
        return order.asReversed().firstOrNull { key -> rect(positions.getValue(key), key).contains(x, y) }
    }

    private fun updateDrag(event: MotionEvent) {
        val state = drag ?: return
        val dx = ((event.x - state.x) / geometry.columnStride).roundToInt()
        val dy = ((event.y - state.y) / geometry.rowStride).roundToInt()
        val target = candidate(state, dx, dy)
        if (positions[state.item] != target) {
            positions[state.item] = target
            notifyDraftChanged()
        }
    }

    private fun candidate(drag: Drag, dx: Int, dy: Int): WorkspacePlacement {
        val start = drag.start
        val grid = original.grid
        if (drag.horizontalEdge == 0) return start.move(start.column + dx, start.row + dy, grid)
        var left = start.column
        var right = start.column + start.width
        var top = start.row
        var bottom = start.row + start.height
        val minWidth = drag.item.minimumSpan
        val minHeight = minOf(drag.item.minimumSpan, grid.rows)
        if (drag.horizontalEdge < 0) left = (left + dx).coerceIn(0, right - minWidth)
        else right = (right + dx).coerceIn(left + minWidth, grid.columns)
        if (drag.verticalEdge < 0) top = (top + dy).coerceIn(0, bottom - minHeight)
        else bottom = (bottom + dy).coerceIn(top + minHeight, grid.rows)
        return WorkspacePlacement(left, top, right - left, bottom - top)
    }

    private fun rect(placement: WorkspacePlacement, item: WorkspaceItem) =
        RectF(geometry.bounds(placement, dp(item.insetDp.toFloat()).roundToInt()))

    private fun edge(value: Float, low: Float, high: Float): Int {
        val start = abs(value - low)
        val end = abs(value - high)
        val radius = minOf(hitRadius, (high - low) * 0.33f)
        return when {
            minOf(start, end) > radius -> 0
            start <= end -> -1
            else -> 1
        }
    }

    private fun notifyDraftChanged() {
        invalidate()
        onDraftChanged?.invoke()
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
