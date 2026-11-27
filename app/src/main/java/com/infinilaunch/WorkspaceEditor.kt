package com.infinilaunch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.DashPathEffect
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
    private val layerOrder = original.itemsBackToFront().toMutableList()
    private val margins = original.placements().keys.associateWith(original::insetsFor).toMutableMap()
    private val labels = mutableMapOf(initialItem to initialTitle)
    private val previews = mutableMapOf<WorkspaceItem, Bitmap>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private lateinit var gridLines: Path
    private lateinit var halfGridLines: Path
    private val halfGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x667E9AA2
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(4f)), 0f)
    }
    private val handleRadius = dp(6f)
    private val hitRadius = dp(23f)
    private var drag: Drag? = null
    private lateinit var geometry: WorkspaceGeometry
    var onDraftChanged: (() -> Unit)? = null
    val placement: WorkspacePlacement? get() = item?.let(positions::get)
    val selectedInsets: WorkspaceInsets get() = item?.let { margins[it] ?: WorkspaceInsets() } ?: WorkspaceInsets()
    val selectedLabel: String get() = item?.let(::labelFor) ?: appContext.getString(R.string.no_desktop_items)
    val selectedIndex: Int get() = item?.let { positions.keys.indexOf(it) + 1 } ?: 0
    val itemCount: Int get() = positions.size
    val canRaiseSelected: Boolean get() = item?.let { layerOrder.indexOf(it) in 0 until layerOrder.lastIndex } ?: false
    val canLowerSelected: Boolean get() = item?.let { layerOrder.indexOf(it) > 0 } ?: false
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
        val startLayerOrder: List<WorkspaceItem>,
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

    fun draftLayout(): WorkspaceLayout {
        val placed = positions.entries.fold(original) { current, (key, value) ->
            current.withPlacement(key, value)
        }
        val reduced = (original.placements().keys - positions.keys).fold(placed) { current, removed ->
            current.withPlacement(removed, null)
        }
        val layered = reduced.withLayerOrder(layerOrder)
        return margins.entries.filter { it.key in positions }.fold(layered) { current, (key, value) ->
            current.withInsets(key, value)
        }
    }

    fun setSelectedInsets(horizontalDp: Int, verticalDp: Int) {
        val selected = item?.takeIf(positions::containsKey) ?: return
        margins[selected] = WorkspaceInsets(horizontalDp, verticalDp)
        notifyDraftChanged()
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

    fun raiseSelected() = shiftSelectedLayer(1)

    fun lowerSelected() = shiftSelectedLayer(-1)

    private fun shiftSelectedLayer(direction: Int) {
        val selected = item ?: return
        val current = layerOrder.indexOf(selected)
        val target = current + direction
        if (current !in layerOrder.indices || target !in layerOrder.indices) return
        layerOrder.removeAt(current)
        layerOrder.add(target, selected)
        notifyDraftChanged()
    }

    fun removeSelected(): WorkspaceItem? {
        val selected = item ?: return null
        positions.remove(selected)
        margins.remove(selected)
        layerOrder.remove(selected)
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
        halfGridLines = geometry.halfGridLines()
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
        for (key in layerOrder) {
            val position = positions[key] ?: continue
            drawItem(canvas, key, position, invalid = key in invalid)
        }
        // Keep the half-cell snap guides visible over opaque widget previews.
        canvas.drawPath(halfGridLines, halfGridPaint)
        if (active != null) {
            positions[active]?.let { drawSelectionOverlay(canvas, it, active in invalid) }
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
        invalid: Boolean,
    ) {
        val bounds = rect(position, key)
        val hasPreview = drawPreview(canvas, key, bounds)
        paint.style = Paint.Style.FILL
        paint.color = if (invalid) 0x38FF594F else 0x248CDDD7
        canvas.drawRoundRect(bounds, dp(7f), dp(7f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.5f)
        paint.color = if (invalid) 0xFFFF8D80.toInt() else 0xA6DDE5E7.toInt()
        canvas.drawRoundRect(bounds, dp(7f), dp(7f), paint)
        paint.style = Paint.Style.FILL

        if (!hasPreview) {
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dp(12f)
            canvas.save()
            canvas.clipRect(bounds)
            canvas.drawText(labelFor(key), bounds.centerX(), bounds.centerY() - (paint.ascent() + paint.descent()) / 2f, paint)
            canvas.restore()
        }
    }

    private fun drawSelectionOverlay(canvas: Canvas, position: WorkspacePlacement, invalid: Boolean) {
        val selected = item ?: return
        val bounds = rect(position, selected)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.color = if (invalid) 0xFFFF8D80.toInt() else 0xFFBDF8EF.toInt()
        canvas.drawRoundRect(bounds, dp(7f), dp(7f), paint)
        paint.style = Paint.Style.FILL
        if (selected !is WorkspaceItem.Launcher) drawResizeHandles(canvas, bounds, invalid)
    }

    private fun drawResizeHandles(canvas: Canvas, bounds: RectF, invalid: Boolean) {
        paint.color = if (invalid) 0xFFFF8D80.toInt() else 0xFFBDF8EF.toInt()
        for (x in listOf(bounds.left, bounds.right)) {
            for (y in listOf(bounds.top, bounds.bottom)) canvas.drawCircle(x, y, handleRadius, paint)
        }
        val long = dp(8f)
        val short = dp(3f)
        for (y in listOf(bounds.top, bounds.bottom)) {
            canvas.drawRoundRect(
                RectF(bounds.centerX() - long, y - short, bounds.centerX() + long, y + short),
                short, short, paint,
            )
        }
        for (x in listOf(bounds.left, bounds.right)) {
            canvas.drawRoundRect(
                RectF(x - short, bounds.centerY() - long, x + short, bounds.centerY() + long),
                short, short, paint,
            )
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
                    val canResize = hit !is WorkspaceItem.Launcher
                    drag = Drag(start = start, item = hit, x = event.x, y = event.y,
                        horizontalEdge = if (canResize) horizontal else 0,
                        verticalEdge = if (canResize) vertical else 0,
                        startLayerOrder = layerOrder.toList())
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
                drag?.let {
                    positions[it.item] = it.start
                    layerOrder.clear()
                    layerOrder.addAll(it.startLayerOrder)
                }
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
        return layerOrder.asReversed().firstOrNull { key ->
            if (key !in positions) return@firstOrNull false
            val bounds = rect(positions.getValue(key), key)
            bounds.contains(x, y) || (key !is WorkspaceItem.Launcher && nearResizeHandle(bounds, x, y))
        }
    }

    private fun nearResizeHandle(bounds: RectF, x: Float, y: Float): Boolean {
        val withinX = x in (bounds.left - hitRadius)..(bounds.right + hitRadius)
        val withinY = y in (bounds.top - hitRadius)..(bounds.bottom + hitRadius)
        val nearHorizontalEdge = abs(y - bounds.top) <= hitRadius || abs(y - bounds.bottom) <= hitRadius
        val nearVerticalEdge = abs(x - bounds.left) <= hitRadius || abs(x - bounds.right) <= hitRadius
        return withinX && withinY && (nearHorizontalEdge || nearVerticalEdge)
    }

    private fun updateDrag(event: MotionEvent) {
        val state = drag ?: return
        val dxHalf = ((event.x - state.x) / (geometry.columnStride / 2f)).roundToInt()
        val dyHalf = ((event.y - state.y) / (geometry.rowStride / 2f)).roundToInt()
        val target = candidate(state, dxHalf, dyHalf)
        if (positions[state.item] != target) {
            positions[state.item] = target
            layerOrder.remove(state.item)
            layerOrder.add(state.item)
            notifyDraftChanged()
        }
    }

    private fun candidate(drag: Drag, dxHalf: Int, dyHalf: Int): WorkspacePlacement {
        val start = drag.start
        val grid = original.grid
        if (drag.horizontalEdge == 0 && drag.verticalEdge == 0) {
            return start.moveByHalf(dxHalf, dyHalf, grid)
        }
        var left = start.columnUnits
        var right = start.columnUnits + start.width * 2
        var top = start.rowUnits
        var bottom = start.rowUnits + start.height * 2
        val minWidth = drag.item.minimumSpan
        val minHeight = minOf(drag.item.minimumSpan, grid.rows)
        val dx = (dxHalf / 2f).roundToInt() * 2
        val dy = (dyHalf / 2f).roundToInt() * 2
        when {
            drag.horizontalEdge < 0 -> left = (left + dx).coerceIn(0, right - minWidth * 2)
            drag.horizontalEdge > 0 -> right = (right + dx).coerceIn(left + minWidth * 2, grid.columns * 2)
        }
        when {
            drag.verticalEdge < 0 -> top = (top + dy).coerceIn(0, bottom - minHeight * 2)
            drag.verticalEdge > 0 -> bottom = (bottom + dy).coerceIn(top + minHeight * 2, grid.rows * 2)
        }
        return WorkspacePlacement.fromHalfUnits(left, top, (right - left) / 2, (bottom - top) / 2)
    }

    private fun rect(placement: WorkspacePlacement, item: WorkspaceItem): RectF {
        val inset = margins[item] ?: original.insetsFor(item)
        return RectF(geometry.bounds(
            placement,
            dp(inset.horizontalDp.toFloat()).roundToInt(),
            dp(inset.verticalDp.toFloat()).roundToInt(),
        ))
    }

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
