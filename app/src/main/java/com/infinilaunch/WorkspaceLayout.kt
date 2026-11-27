package com.infinilaunch

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

internal data class WorkspaceGrid(val columns: Int = 6, val rows: Int = 5) {
    init {
        require(columns in MIN_COLUMNS..MAX_COLUMNS && rows in 1..MAX_ROWS)
    }

    companion object {
        const val MIN_COLUMNS = 3
        const val MAX_COLUMNS = 12
        const val MAX_ROWS = 128

        fun forArea(columns: Int, width: Int, height: Int): WorkspaceGrid {
            require(width > 0 && height > 0)
            // Fit whole square cells first. WorkspaceGeometry then stretches
            // those rows evenly across the full available height.
            val rows = (columns * height.toDouble() / width).toInt().coerceIn(1, MAX_ROWS)
            return WorkspaceGrid(columns, rows)
        }
    }
}

internal data class WorkspacePlacement(
    val column: Int,
    val row: Int,
    val width: Int,
    val height: Int,
    val columnOffsetHalf: Int = 0,
    val rowOffsetHalf: Int = 0,
) {
    init {
        require(column >= 0 && row >= 0 && width >= 1 && height >= 1)
        require(columnOffsetHalf in 0..1 && rowOffsetHalf in 0..1)
    }

    val columnUnits: Int get() = column * 2 + columnOffsetHalf
    val rowUnits: Int get() = row * 2 + rowOffsetHalf

    fun fits(grid: WorkspaceGrid) =
        columnUnits < grid.columns * 2 && rowUnits < grid.rows * 2 &&
            width * 2 <= grid.columns * 2 - columnUnits && height * 2 <= grid.rows * 2 - rowUnits

    fun overlaps(other: WorkspacePlacement) =
        columnUnits < other.columnUnits + other.width * 2 && other.columnUnits < columnUnits + width * 2 &&
            rowUnits < other.rowUnits + other.height * 2 && other.rowUnits < rowUnits + height * 2

    fun move(column: Int, row: Int, grid: WorkspaceGrid) = fromHalfUnits(
        (column * 2).coerceIn(0, grid.columns * 2 - width * 2),
        (row * 2).coerceIn(0, grid.rows * 2 - height * 2),
        width,
        height,
    )

    fun moveByHalf(columnDelta: Int, rowDelta: Int, grid: WorkspaceGrid) = fromHalfUnits(
        (columnUnits + columnDelta).coerceIn(0, grid.columns * 2 - width * 2),
        (rowUnits + rowDelta).coerceIn(0, grid.rows * 2 - height * 2),
        width,
        height,
    )

    companion object {
        val DEFAULT = WorkspacePlacement(3, 3, 3, 2)
        val CLOCK = WorkspacePlacement(3, 0, 3, 1)

        fun fromHalfUnits(columnUnits: Int, rowUnits: Int, width: Int, height: Int) =
            WorkspacePlacement(columnUnits / 2, rowUnits / 2, width, height, columnUnits % 2, rowUnits % 2)
    }
}

internal sealed interface WorkspaceItem {
    data object Programs : WorkspaceItem
    data object Clock : WorkspaceItem
    data class App(val id: Int) : WorkspaceItem
    data class Launcher(val filename: String) : WorkspaceItem

    val minimumSpan: Int get() = if (this == Programs) 2 else 1
}

internal data class WidgetBinding(val id: Int, val provider: String) {
    init {
        require(id > 0 && provider.length in 1..256 && '/' in provider)
    }
}

internal data class HostedWidget(val binding: WidgetBinding, val placement: WorkspacePlacement)

internal data class WorkspaceInsets(val horizontalDp: Int = 0, val verticalDp: Int = 0) {
    init {
        require(horizontalDp in -MAX_DP..MAX_DP && verticalDp in -MAX_DP..MAX_DP)
    }

    companion object {
        const val MAX_DP = 512
    }
}

internal data class WorkspaceLayout(
    val programs: WorkspacePlacement? = WorkspacePlacement.DEFAULT,
    val clock: WidgetBinding? = null,
    val hosted: List<HostedWidget> = emptyList(),
    val grid: WorkspaceGrid = WorkspaceGrid(),
    val clockPlacement: WorkspacePlacement? = WorkspacePlacement.CLOCK,
    val launchers: Map<String, WorkspacePlacement> = emptyMap(),
    val itemInsets: Map<WorkspaceItem, WorkspaceInsets> = emptyMap(),
    /** Item order from back to front. */
    val layerOrder: List<WorkspaceItem> = emptyList(),
) {
    fun insetsFor(item: WorkspaceItem): WorkspaceInsets = itemInsets[item] ?: WorkspaceInsets()

    fun itemsBackToFront(): List<WorkspaceItem> {
        val placed = placements().keys
        val ordered = layerOrder.filter { it in placed }.distinct()
        return ordered + placed.filterNot(ordered.toSet()::contains)
    }

    fun withLayerOrder(order: List<WorkspaceItem>): WorkspaceLayout {
        val placed = placements().keys
        val ordered = order.filter { it in placed }.distinct()
        return copy(layerOrder = ordered + placed.filterNot(ordered.toSet()::contains))
    }

    fun bringToFront(item: WorkspaceItem): WorkspaceLayout =
        if (item !in placements()) this else withLayerOrder(itemsBackToFront().filterNot { it == item } + item)

    fun withInsets(item: WorkspaceItem, value: WorkspaceInsets): WorkspaceLayout {
        if (item !in placements()) return this
        val updated = if (value == WorkspaceInsets()) itemInsets - item else itemInsets + (item to value)
        return copy(itemInsets = updated)
    }

    fun placements(): Map<WorkspaceItem, WorkspacePlacement> = buildMap {
        programs?.let { put(WorkspaceItem.Programs, it) }
        clockPlacement?.let { put(WorkspaceItem.Clock, it) }
        hosted.forEach { put(WorkspaceItem.App(it.binding.id), it.placement) }
        launchers.forEach { (filename, placement) -> put(WorkspaceItem.Launcher(filename), placement) }
    }

    fun invalidItems(): Set<WorkspaceItem> {
        val current = placements()
        val invalid = mutableSetOf<WorkspaceItem>()
        for ((item, position) in current) {
            if (!position.fits(grid) || position.width < item.minimumSpan ||
                position.height < minOf(item.minimumSpan, grid.rows)) {
                invalid.add(item)
            }
        }
        return invalid
    }

    fun isValid() = invalidItems().isEmpty()

    fun withPlacement(item: WorkspaceItem, position: WorkspacePlacement?): WorkspaceLayout = when (item) {
        WorkspaceItem.Programs -> copy(programs = position,
            itemInsets = if (position == null) itemInsets - item else itemInsets,
            layerOrder = if (position == null) layerOrder - item else layerOrder)
        WorkspaceItem.Clock -> copy(clock = clock.takeIf { position != null }, clockPlacement = position,
            itemInsets = if (position == null) itemInsets - item else itemInsets,
            layerOrder = if (position == null) layerOrder - item else layerOrder)
        is WorkspaceItem.App -> copy(hosted = hosted.mapNotNull {
            if (it.binding.id != item.id) it else position?.let { position -> it.copy(placement = position) }
        }, itemInsets = if (position == null) itemInsets - item else itemInsets,
            layerOrder = if (position == null) layerOrder - item else layerOrder)
        is WorkspaceItem.Launcher -> copy(launchers = if (position == null) launchers - item.filename
            else launchers + (item.filename to position),
            itemInsets = if (position == null) itemInsets - item else itemInsets,
            layerOrder = if (position == null) layerOrder - item else layerOrder)
    }

    fun forDesktop(files: List<String>, width: Int, height: Int, columns: Int = grid.columns): WorkspaceLayout {
        val known = files.toSet()
        var visible = copy(
            launchers = launchers.filterKeys { it in known },
            itemInsets = itemInsets.filterKeys { it !is WorkspaceItem.Launcher || it.filename in known },
            layerOrder = layerOrder.filter { it !is WorkspaceItem.Launcher || it.filename in known },
        ).forArea(width, height, columns)
        val missing = files.filterNot(visible.launchers::containsKey)
        val cellsNeeded = visible.placements().values.sumOf { it.width * it.height } + missing.size
        if (visible.grid.rows == 1 && visible.programs != null && cellsNeeded > visible.grid.columns) {
            val expanded = visible.grid.copy(rows = 2)
            visible = visible.withGrid(expanded, compact = false)
                ?: requireNotNull(visible.withGrid(expanded, compact = true))
        }
        val capacity = visible.grid.columns * WorkspaceGrid.MAX_ROWS
        if (visible.placements().values.sumOf { it.width * it.height } + missing.size > capacity) {
            visible = requireNotNull(visible.withGrid(WorkspaceGrid(columns, WorkspaceGrid.MAX_ROWS), compact = true))
        }
        val placements = visible.launchers.toMutableMap()
        val occupied = visible.placements().values.toList()
        val used = occupied.sumOf { it.width * it.height }
        val rows = maxOf(visible.grid.rows, (used + missing.size + columns - 1) / columns)
        val cells = sequence {
            for (row in 0 until rows) for (column in 0 until columns) {
                val cell = WorkspacePlacement(column, row, 1, 1)
                if (occupied.none(cell::overlaps)) yield(cell)
            }
        }.iterator()
        for (file in missing) placements[file] = cells.next()
        return visible.copy(grid = WorkspaceGrid(columns, rows), launchers = placements)
            .withLayerOrder(visible.itemsBackToFront() + missing.map { WorkspaceItem.Launcher(it) })
    }

    fun forArea(width: Int, height: Int, columns: Int = grid.columns): WorkspaceLayout {
        if (width <= 0 || height <= 0) return this
        val gridForArea = WorkspaceGrid.forArea(columns, width, height)
        withGrid(gridForArea, compact = false)?.let { return it }
        // Keep every widget visible in a small viewport. Only add rows if the
        // preferred grid cannot fit the widgets at their minimum spans.
        return (gridForArea.rows..WorkspaceGrid.MAX_ROWS).firstNotNullOf { rows ->
            withGrid(gridForArea.copy(rows = rows), compact = true)
        }
    }

    private fun withGrid(newGrid: WorkspaceGrid, compact: Boolean): WorkspaceLayout? {
        if (newGrid == grid && !compact) return this
        var resized = copy(grid = newGrid)
        for ((item, old) in placements()) {
            val minWidth = item.minimumSpan
            val minHeight = minOf(item.minimumSpan, newGrid.rows)
            val colUnits = project(old.columnUnits, grid.columns * 2, newGrid.columns * 2)
                .coerceIn(0, newGrid.columns * 2 - minWidth * 2)
            val rowUnits = project(old.rowUnits, grid.rows * 2, newGrid.rows * 2)
                .coerceIn(0, newGrid.rows * 2 - minHeight * 2)
            val fixedSize = compact || item is WorkspaceItem.Launcher
            val projectedRight = project(old.columnUnits + old.width * 2, grid.columns * 2, newGrid.columns * 2)
                .coerceIn(0, newGrid.columns * 2)
            val projectedBottom = project(old.rowUnits + old.height * 2, grid.rows * 2, newGrid.rows * 2)
                .coerceIn(0, newGrid.rows * 2)
            val maxWidth = (newGrid.columns * 2 - colUnits) / 2
            val maxHeight = (newGrid.rows * 2 - rowUnits) / 2
            val width = if (fixedSize) minWidth else
                ((projectedRight - colUnits) / 2.0).roundToInt().coerceIn(minWidth, maxWidth)
            val height = if (fixedSize) minHeight else
                ((projectedBottom - rowUnits) / 2.0).roundToInt().coerceIn(minHeight, maxHeight)
            resized = resized.withPlacement(item,
                WorkspacePlacement.fromHalfUnits(colUnits, rowUnits, width, height))
        }
        return resized
    }

    fun encode(): String {
        require(isValid()) { "Workspace items must fit the grid." }
        val widgets = JSONArray()
        programs?.let { position ->
            widgets.put(JSONObject().put("type", "programs").setPlacement(position)
                .putInsets(insetsFor(WorkspaceItem.Programs)))
        }
        clockPlacement?.let { position ->
            val value = JSONObject().put("type", "clock").setPlacement(position)
                .putInsets(insetsFor(WorkspaceItem.Clock))
            clock?.let { value.put("binding", it.id).put("provider", it.provider) }
            widgets.put(value)
        }
        hosted.forEach { widget ->
            widgets.put(JSONObject().put("type", "app-widget").put("binding", widget.binding.id)
                .put("provider", widget.binding.provider).setPlacement(widget.placement)
                .putInsets(insetsFor(WorkspaceItem.App(widget.binding.id))))
        }
        val files = JSONArray()
        launchers.forEach { (filename, placement) ->
            files.put(JSONObject().put("file", filename).setPlacement(placement)
                .putInsets(insetsFor(WorkspaceItem.Launcher(filename))))
        }
        val layers = JSONArray()
        itemsBackToFront().forEach { layers.put(it.layerRecord()) }
        return JSONObject().put("version", 1)
            .put("grid", JSONObject().put("columns", grid.columns).put("rows", grid.rows))
            .put("widgets", widgets).put("launchers", files).put("layerOrder", layers).toString()
    }

    private fun JSONObject.setPlacement(value: WorkspacePlacement) = apply {
        put("column", value.column).put("row", value.row).put("width", value.width).put("height", value.height)
        if (value.columnOffsetHalf != 0) put("columnOffsetHalf", value.columnOffsetHalf)
        if (value.rowOffsetHalf != 0) put("rowOffsetHalf", value.rowOffsetHalf)
    }

    private fun JSONObject.putInsets(value: WorkspaceInsets) = apply {
        if (value.horizontalDp != 0) put("marginHorizontalDp", value.horizontalDp)
        if (value.verticalDp != 0) put("marginVerticalDp", value.verticalDp)
    }

    private fun WorkspaceItem.layerRecord(): JSONObject = when (this) {
        WorkspaceItem.Programs -> JSONObject().put("type", "programs")
        WorkspaceItem.Clock -> JSONObject().put("type", "clock")
        is WorkspaceItem.App -> JSONObject().put("type", "app-widget").put("binding", id)
        is WorkspaceItem.Launcher -> JSONObject().put("type", "launcher").put("file", filename)
    }

    companion object {
        const val MAX_HOSTED = 12
        const val MAX_LAUNCHERS = 256

        private fun project(value: Int, old: Int, new: Int) = (value * new.toDouble() / old).roundToInt()

        private fun JSONObject.placement() = WorkspacePlacement(
            getInt("column"), getInt("row"), getInt("width"), getInt("height"),
            optInt("columnOffsetHalf", 0), optInt("rowOffsetHalf", 0),
        )

        private fun JSONObject.binding() = WidgetBinding(getInt("binding"), getString("provider"))

        fun decode(raw: String?): WorkspaceLayout {
            if (raw == null) return WorkspaceLayout()
            val document = JSONObject(raw)
            require(document.getInt("version") == 1)
            val size = document.optJSONObject("grid")
            require(size != null || !document.has("grid"))
            val legacy = size == null
            val grid = size?.let { WorkspaceGrid(it.getInt("columns"), it.getInt("rows")) } ?: WorkspaceGrid()
            val widgets = document.getJSONArray("widgets")
            require(widgets.length() <= MAX_HOSTED + 2)
            val seen = mutableSetOf<String>()
            val bindings = mutableSetOf<Int>()
            val hosted = mutableListOf<HostedWidget>()
            var programs: WorkspacePlacement? = null
            var clock: WidgetBinding? = null
            var clockPosition: WorkspacePlacement? = if (legacy) WorkspacePlacement.CLOCK else null
            val itemInsets = mutableMapOf<WorkspaceItem, WorkspaceInsets>()

            fun readInsets(item: WorkspaceItem, source: JSONObject) {
                val insets = WorkspaceInsets(
                    source.optInt("marginHorizontalDp", 0),
                    source.optInt("marginVerticalDp", 0),
                )
                if (insets != WorkspaceInsets()) itemInsets[item] = insets
            }

            fun position(item: JSONObject): WorkspacePlacement {
                val saved = item.placement()
                if (!legacy) return saved
                require(saved.fits(WorkspaceGrid(6, 4)))
                // Older grids reserved a separate band above their four rows for Clock.
                return saved.copy(row = saved.row + 1)
            }

            for (index in 0 until widgets.length()) {
                val widget = widgets.getJSONObject(index)
                val type = widget.getString("type")
                if (type == "programs" || type == "clock") require(seen.add(type))
                when (type) {
                    "programs" -> {
                        programs = position(widget)
                        readInsets(WorkspaceItem.Programs, widget)
                    }
                    "clock" -> {
                        if (widget.has("binding")) clock = widget.binding().also { require(bindings.add(it.id)) }
                        if (!legacy) clockPosition = position(widget)
                        readInsets(WorkspaceItem.Clock, widget)
                    }
                    "app-widget" -> {
                        val binding = widget.binding()
                        require(bindings.add(binding.id))
                        hosted.add(HostedWidget(binding, position(widget)))
                        readInsets(WorkspaceItem.App(binding.id), widget)
                    }
                    else -> error("Unsupported workspace item type")
                }
            }
            require(hosted.size <= MAX_HOSTED)
            val files = document.optJSONArray("launchers")
            require(!document.has("launchers") || files != null)
            val launchers = buildMap {
                if (files != null) {
                    require(!legacy && files.length() <= MAX_LAUNCHERS)
                    for (index in 0 until files.length()) {
                        val entry = files.getJSONObject(index)
                        val filename = entry.getString("file")
                        val placement = entry.placement()
                        require(filename.isNotEmpty() && filename != "." && filename != ".." &&
                            '/' !in filename && filename !in this && placement.width == 1 && placement.height == 1)
                        put(filename, placement)
                        readInsets(WorkspaceItem.Launcher(filename), entry)
                    }
                }
            }
            val baseLayout = WorkspaceLayout(programs, clock, hosted, grid, clockPosition, launchers, itemInsets)
            val layers = document.optJSONArray("layerOrder")
            require(!document.has("layerOrder") || layers != null)
            val layerOrder = buildList {
                val seenLayers = mutableSetOf<WorkspaceItem>()
                if (layers != null) for (index in 0 until layers.length()) {
                    val entry = layers.getJSONObject(index)
                    val item = when (entry.getString("type")) {
                        "programs" -> WorkspaceItem.Programs
                        "clock" -> WorkspaceItem.Clock
                        "app-widget" -> WorkspaceItem.App(entry.getInt("binding"))
                        "launcher" -> WorkspaceItem.Launcher(entry.getString("file"))
                        else -> error("Unsupported workspace layer")
                    }
                    require(item in baseLayout.placements() && seenLayers.add(item))
                    add(item)
                }
            }
            val layout = baseLayout.withLayerOrder(layerOrder)
            for ((item, place) in layout.placements()) {
                require(place.fits(grid) && place.width >= item.minimumSpan &&
                    place.height >= minOf(item.minimumSpan, grid.rows))
            }
            return layout
        }
    }
}
