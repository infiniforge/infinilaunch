package com.infinilaunch

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
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
            val rows = (columns * height.toDouble() / width).roundToInt().coerceIn(1, MAX_ROWS)
            return WorkspaceGrid(columns, rows)
        }
    }
}

internal data class WorkspacePlacement(val column: Int, val row: Int, val width: Int, val height: Int) {
    init {
        require(column >= 0 && row >= 0 && width >= 1 && height >= 1)
    }

    fun fits(grid: WorkspaceGrid) =
        column < grid.columns && row < grid.rows && width <= grid.columns - column && height <= grid.rows - row

    fun overlaps(other: WorkspacePlacement) =
        column < other.column + other.width && other.column < column + width &&
            row < other.row + other.height && other.row < row + height

    fun move(column: Int, row: Int, grid: WorkspaceGrid) =
        copy(column = column.coerceIn(0, grid.columns - width), row = row.coerceIn(0, grid.rows - height))

    companion object {
        val DEFAULT = WorkspacePlacement(3, 3, 3, 2)
        val CLOCK = WorkspacePlacement(3, 0, 3, 1)
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

internal data class WorkspaceLayout(
    val programs: WorkspacePlacement? = WorkspacePlacement.DEFAULT,
    val clock: WidgetBinding? = null,
    val hosted: List<HostedWidget> = emptyList(),
    val grid: WorkspaceGrid = WorkspaceGrid(),
    val clockPlacement: WorkspacePlacement? = WorkspacePlacement.CLOCK,
    val launchers: Map<String, WorkspacePlacement> = emptyMap(),
) {
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
        val entries = current.entries.toList()
        for (first in entries.indices) for (second in first + 1 until entries.size) {
            val a = entries[first]
            val b = entries[second]
            if (a.value.overlaps(b.value)) {
                invalid.add(a.key)
                invalid.add(b.key)
            }
        }
        return invalid
    }

    fun isValid() = invalidItems().isEmpty()

    fun withPlacement(item: WorkspaceItem, position: WorkspacePlacement?): WorkspaceLayout = when (item) {
        WorkspaceItem.Programs -> copy(programs = position)
        WorkspaceItem.Clock -> copy(clock = clock.takeIf { position != null }, clockPlacement = position)
        is WorkspaceItem.App -> copy(hosted = hosted.mapNotNull {
            if (it.binding.id != item.id) it else position?.let { position -> it.copy(placement = position) }
        })
        is WorkspaceItem.Launcher -> copy(launchers = if (position == null) launchers - item.filename
            else launchers + (item.filename to position))
    }

    fun forDesktop(files: List<String>, width: Int, height: Int, columns: Int = grid.columns): WorkspaceLayout {
        val known = files.toSet()
        var visible = copy(launchers = launchers.filterKeys { it in known }).forArea(width, height, columns)
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
        val taken = mutableListOf<WorkspacePlacement>()
        val items = placements().entries.sortedByDescending { it.value.width * it.value.height }
        for ((item, old) in items) {
            val col = project(old.column, grid.columns, newGrid.columns)
            val row = project(old.row, grid.rows, newGrid.rows)
            val minWidth = item.minimumSpan
            val minHeight = minOf(item.minimumSpan, newGrid.rows)
            val fixedSize = compact || item is WorkspaceItem.Launcher
            val width = if (fixedSize) minWidth else
                (project(old.column + old.width, grid.columns, newGrid.columns) - col).coerceAtLeast(minWidth)
            val height = if (fixedSize) minHeight else
                (project(old.row + old.height, grid.rows, newGrid.rows) - row).coerceAtLeast(minHeight)
            var placement: WorkspacePlacement? = null
            var distance = Int.MAX_VALUE
            for (y in 0..newGrid.rows - height) {
                for (x in 0..newGrid.columns - width) {
                    val steps = abs(x - col) + abs(y - row)
                    if (steps >= distance) continue
                    val candidate = WorkspacePlacement(x, y, width, height)
                    if (taken.none(candidate::overlaps)) {
                        placement = candidate
                        distance = steps
                    }
                }
            }
            if (placement == null) return null
            taken.add(placement)
            resized = resized.withPlacement(item, placement)
        }
        return resized
    }

    fun encode(): String {
        require(isValid()) { "Workspace items must fit the grid without overlapping." }
        val widgets = JSONArray()
        programs?.let { widgets.put(JSONObject().put("type", "programs").setPlacement(it)) }
        clockPlacement?.let { position ->
            val value = JSONObject().put("type", "clock").setPlacement(position)
            clock?.let { value.put("binding", it.id).put("provider", it.provider) }
            widgets.put(value)
        }
        hosted.forEach { widget ->
            widgets.put(JSONObject().put("type", "app-widget").put("binding", widget.binding.id)
                .put("provider", widget.binding.provider).setPlacement(widget.placement))
        }
        val files = JSONArray()
        launchers.forEach { (filename, placement) -> files.put(JSONObject().put("file", filename).setPlacement(placement)) }
        return JSONObject().put("version", 1)
            .put("grid", JSONObject().put("columns", grid.columns).put("rows", grid.rows))
            .put("widgets", widgets).put("launchers", files).toString()
    }

    private fun JSONObject.setPlacement(value: WorkspacePlacement) =
        put("column", value.column).put("row", value.row).put("width", value.width).put("height", value.height)

    companion object {
        const val MAX_HOSTED = 12
        const val MAX_LAUNCHERS = 256

        private fun project(value: Int, old: Int, new: Int) = (value * new.toDouble() / old).roundToInt()

        private fun JSONObject.placement() =
            WorkspacePlacement(getInt("column"), getInt("row"), getInt("width"), getInt("height"))

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
                    "programs" -> programs = position(widget)
                    "clock" -> {
                        if (widget.has("binding")) clock = widget.binding().also { require(bindings.add(it.id)) }
                        if (!legacy) clockPosition = position(widget)
                    }
                    "app-widget" -> {
                        val binding = widget.binding()
                        require(bindings.add(binding.id))
                        hosted.add(HostedWidget(binding, position(widget)))
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
                    }
                }
            }
            val layout = WorkspaceLayout(programs, clock, hosted, grid, clockPosition, launchers)
            val occupied = mutableListOf<WorkspacePlacement>()
            for ((item, place) in layout.placements()) {
                require(place.fits(grid) && place.width >= item.minimumSpan &&
                    place.height >= minOf(item.minimumSpan, grid.rows))
                require(occupied.none(place::overlaps))
                occupied.add(place)
            }
            return layout
        }
    }
}
