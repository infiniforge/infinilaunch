package com.infinilaunch

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** Presents the files already listed by Folder as one-cell desktop launchers. */
internal class DesktopLaunchers(
    private val context: Context,
    private val workspace: WorkspaceView,
    private val open: (String) -> Unit,
    private val menu: (Programs.DesktopEntry) -> Unit,
    private val lockedLongPress: (Programs.DesktopEntry) -> Unit,
    private val drop: (Programs.DesktopEntry, Float, Float) -> Boolean,
    private val loadAppearance: (String, Programs.DesktopEntry, Int, (Programs.DesktopAppearance?) -> Unit) -> Unit,
) {
    private data class Tile(
        val source: Programs.DesktopEntry,
        val appearance: Programs.DesktopAppearance?,
        val view: View,
    )
    private data class EntryKey(val workspace: String, val filename: String)
    private data class CachedAppearance(val source: Programs.DesktopEntry, val appearance: Programs.DesktopAppearance)

    private val tiles = mutableMapOf<WorkspaceItem.Launcher, Tile>()
    private val appearances = object : android.util.LruCache<EntryKey, CachedAppearance>(16 * 1024) {
        override fun sizeOf(key: EntryKey, value: CachedAppearance): Int =
            (value.appearance.icon?.allocationByteCount?.div(1024) ?: 1).coerceAtLeast(1)
    }
    private val requested = mutableSetOf<Programs.DesktopEntry>()
    private val refreshNeeded = mutableSetOf<Programs.DesktopEntry>()
    private val attempts = mutableMapOf<Programs.DesktopEntry, Int>()
    private var workspaceName: String? = null
    private var workspaceGeneration = 0
    private var currentEntries: List<Programs.DesktopEntry> = emptyList()

    fun invalidateAppearances() {
        workspaceGeneration++
        requested.clear()
        attempts.clear()
        refreshNeeded.addAll(currentEntries)
    }

    fun show(name: String, entries: List<Programs.DesktopEntry>) {
        if (workspaceName != name) {
            workspaceName = name
            workspaceGeneration++
            requested.clear()
            refreshNeeded.clear()
            attempts.clear()
        }
        currentEntries = entries
        entries.forEach { source ->
            val key = EntryKey(name, source.filename)
            if (appearances.get(key)?.source?.let { it != source } == true) appearances.remove(key)
        }
        requested.removeAll { it !in entries }
        refreshNeeded.removeAll { it !in entries }
        attempts.keys.removeAll { it !in entries }

        val active = entries.map { WorkspaceItem.Launcher(it.filename) }.toSet()
        (tiles.keys - active).forEach {
            workspace.removeWidget(it)
            tiles.remove(it)
        }
        for (source in entries) {
            val appearance = appearances.get(EntryKey(name, source.filename))
                ?.takeIf { it.source == source }?.appearance
            val item = WorkspaceItem.Launcher(source.filename)
            if (tiles[item]?.let { it.source == source && it.appearance == appearance } == true) {
                if (appearance == null || source in refreshNeeded) requestAppearance(source)
                continue
            }

            val displayed = appearance?.let {
                source.copy(label = it.label, mime = it.mime, folder = it.folder)
            } ?: source
            val view = DesktopItemFrame(
                context, workspace, displayed, { content(displayed, appearance) },
                { open(source.filename) }, { menu(displayed) }, { lockedLongPress(displayed) }, drop,
            )
            view.addView(content(displayed, appearance), FrameLayout.LayoutParams(-1, -1))
            tiles[item] = Tile(source, appearance, view)
            workspace.putWidget(item, view)
            if (appearance == null || source in refreshNeeded) requestAppearance(source)
        }
    }

    private fun requestAppearance(source: Programs.DesktopEntry) {
        if (!requested.add(source)) return
        val generation = workspaceGeneration
        val requestedWorkspace = requireNotNull(workspaceName)
        val attempt = (attempts[source] ?: 0) + 1
        attempts[source] = attempt
        loadAppearance(requestedWorkspace, source, dp(48)) { appearance ->
            if (generation != workspaceGeneration || source !in currentEntries) return@loadAppearance
            if (appearance != null) {
                appearances.put(EntryKey(requestedWorkspace, source.filename), CachedAppearance(source, appearance))
                refreshNeeded.remove(source)
                attempts.remove(source)
            } else if (attempt < MAX_APPEARANCE_ATTEMPTS) {
                workspace.postDelayed({
                    if (generation == workspaceGeneration && source in currentEntries) {
                        requested.remove(source)
                        requestAppearance(source)
                    }
                }, APPEARANCE_RETRY_DELAY_MS)
            } else {
                refreshNeeded.remove(source)
            }
            show(requireNotNull(workspaceName), currentEntries)
        }
    }

    private fun content(entry: Programs.DesktopEntry, appearance: Programs.DesktopAppearance?): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(1), 0, dp(1), 0)
            weightSum = 4f
            val iconView = appearance?.icon?.let { bitmap ->
                ImageView(context).apply {
                    setImageBitmap(bitmap)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            } ?: TextView(context).apply {
                text = appearance?.fallback?.takeIf(String::isNotBlank) ?: fallback(entry)
                gravity = Gravity.CENTER
                setAutoSizeTextTypeUniformWithConfiguration(6, 96, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            }
            iconView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(iconView, LinearLayout.LayoutParams(-1, 0, 3f))
            addView(TextView(context).apply {
                text = entry.label
                gravity = Gravity.CENTER
                includeFontPadding = false
                setAutoSizeTextTypeUniformWithConfiguration(6, 64, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
                setTextColor(Color.WHITE)
                setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), Color.BLACK)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        }

    private fun fallback(entry: Programs.DesktopEntry): String = when {
        entry.folder -> "📁"
        entry.mime.startsWith("image/") -> "▧"
        entry.mime.startsWith("audio/") -> "♫"
        entry.mime.startsWith("video/") -> "▶"
        else -> "▤"
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val MAX_APPEARANCE_ATTEMPTS = 3
        const val APPEARANCE_RETRY_DELAY_MS = 600L
    }
}
