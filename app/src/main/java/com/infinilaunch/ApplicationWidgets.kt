package com.infinilaunch

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetProviderInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.Collator

internal class ApplicationWidgets(
    private val activity: Activity,
    private val workspace: WorkspaceView,
    private val addWidget: (HostedWidget) -> Unit,
    private val editWidget: (Int, String) -> Boolean,
) {
    private data class Visible(val frame: View, val binding: WidgetBinding)
    internal data class BuiltIn(val label: String, val add: () -> Unit)
    private data class Choice(val label: String, val icon: () -> Drawable?, val select: () -> Unit)

    private val displayed = mutableMapOf<Int, Visible>()
    private val session = AppWidgetSession(activity, 0x494D, 51, ::onBound)
    private var pendingPlacement: WorkspacePlacement? = null

    fun start() = session.start()
    fun stop() = session.stop()
    fun release(id: Int) = session.release(id)
    fun onResult(request: Int, result: Int) = session.onResult(request, result)

    fun restore(state: Bundle?) {
        session.restore(state)
        val position = state?.getIntArray("applicationWidgetPosition")
        if (position?.size == 4) {
            pendingPlacement = runCatching {
                WorkspacePlacement(position[0], position[1], position[2], position[3])
            }.getOrNull()
        }
    }

    fun save(state: Bundle) {
        session.save(state)
        pendingPlacement?.let {
            state.putIntArray("applicationWidgetPosition", intArrayOf(it.column, it.row, it.width, it.height))
        }
    }

    fun reconcile(hosted: List<HostedWidget>, pending: HostedWidget?) {
        session.reconcile(hosted.map { it.binding.id }.toSet() + setOfNotNull(pending?.binding?.id))
    }

    fun pick(builtIns: List<BuiltIn>) {
        if (session.isBinding) return
        val providers = runCatching { session.providers() }.getOrDefault(emptyList()).distinctBy { it.provider }
        val groups = providers.groupBy { it.provider.packageName }.map { (pkg, widgets) ->
            val name = appName(pkg)
            pkg to Choice(name, { appIcon(pkg) }) {
                val items = widgets.map { info ->
                    Choice(widgetName(info), { widgetIcon(info) }) { requestWidget(info) }
                }
                showChoices(name, sorted(items), back = { pick(builtIns) })
            }
        }.toMutableList()
        if (builtIns.isNotEmpty()) {
            val pkg = activity.packageName
            val name = appName(pkg)
            groups.add(pkg to Choice(name, { appIcon(pkg) }) {
                showChoices(name, builtIns.map { Choice(it.label, { appIcon(pkg) }, it.add) }, back = { pick(builtIns) })
            })
        }
        if (groups.isEmpty()) {
            toast(R.string.no_application_widgets)
            return
        }
        showChoices(activity.getString(R.string.add_widget), sorted(groups.map { it.second }))
    }

    private fun sorted(choices: List<Choice>): List<Choice> {
        val collation = Collator.getInstance()
        return choices.sortedWith { first, second -> collation.compare(first.label, second.label) }
    }

    private fun showChoices(title: String, choices: List<Choice>, back: (() -> Unit)? = null) {
        val builder = AlertDialog.Builder(activity)
            .setTitle(title)
            .setAdapter(ChoiceAdapter(choices)) { _, index -> choices[index].select() }
            .setNegativeButton(android.R.string.cancel, null)
        if (back != null) builder.setNeutralButton(R.string.widget_chooser_back) { _, _ -> back() }
        builder.create().apply {
            if (back != null) {
                setCanceledOnTouchOutside(false)
                setOnCancelListener { back() }
            }
            show()
        }
    }

    private inner class ChoiceAdapter(private val choices: List<Choice>) : BaseAdapter() {
        override fun getCount() = choices.size
        override fun getItem(position: Int) = choices[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView as? LinearLayout ?: LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(64)
                setPadding(dp(20), dp(6), dp(20), dp(6))
                addView(ImageView(activity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(42), dp(42)))
                addView(TextView(activity).apply {
                    textSize = 16f
                    setPadding(dp(14), 0, 0, 0)
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            val item = choices[position]
            (row.getChildAt(0) as ImageView).setImageDrawable(item.icon())
            (row.getChildAt(1) as TextView).text = item.label
            return row
        }
    }

    private fun requestWidget(info: AppWidgetProviderInfo) {
        val position = findSpace(info)
        if (position == null) toast(R.string.widget_no_space)
        else {
            pendingPlacement = position
            session.bind(info, interactive = true)
        }
    }

    private fun findSpace(info: AppWidgetProviderInfo): WorkspacePlacement? =
        workspace.findSpace(maxOf(1, info.minWidth), maxOf(1, info.minHeight))

    private fun onBound(binding: WidgetBinding?) {
        val intended = pendingPlacement
        pendingPlacement = null
        if (binding == null) return
        val info = session.provider(binding)
        val position = intended?.takeIf { workspace.width == 0 || workspace.canPlace(it) }
            ?: info?.let(::findSpace)
        if (position == null) {
            release(binding.id)
            toast(R.string.widget_no_space)
        } else addWidget(HostedWidget(binding, position))
    }

    fun render(widgets: List<HostedWidget>) {
        val ids = widgets.map { it.binding.id }.toSet()
        (displayed.keys - ids).forEach { id ->
            workspace.removeWidget(WorkspaceItem.App(id))
            displayed.remove(id)
        }
        for (widget in widgets) {
            val id = widget.binding.id
            val view = displayed[id]?.takeIf { it.binding == widget.binding }
                ?: makeWidget(widget).also { displayed[id] = it }
            workspace.putWidget(WorkspaceItem.App(id), view.frame)
        }
    }

    private fun makeWidget(widget: HostedWidget): Visible {
        val label = session.provider(widget.binding)?.let(::widgetName) ?: activity.getString(R.string.application_widget)
        val frame = WidgetHoldFrame(activity) { editWidget(widget.binding.id, label) }
        val content = session.view(widget.binding) ?: TextView(activity).apply {
            setText(R.string.application_widget_unavailable)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(0x8818282D.toInt())
        }
        frame.addView(content, FrameLayout.LayoutParams(-1, -1))
        return Visible(frame, widget.binding)
    }

    private fun widgetName(info: AppWidgetProviderInfo): String =
        runCatching { info.loadLabel(activity.packageManager) }.getOrNull()?.takeUnless(String::isBlank)
            ?: info.provider.className.substringAfterLast('.')

    private fun widgetIcon(info: AppWidgetProviderInfo): Drawable? {
        val density = activity.resources.displayMetrics.densityDpi
        return runCatching { info.loadPreviewImage(activity, density) }.getOrNull()
            ?: runCatching { info.loadIcon(activity, density) }.getOrNull()
            ?: appIcon(info.provider.packageName)
    }

    @Suppress("DEPRECATION")
    private fun appName(pkg: String): String {
        val pm = activity.packageManager
        return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }

    private fun appIcon(pkg: String): Drawable? =
        runCatching { activity.packageManager.getApplicationIcon(pkg) }.getOrNull()

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density + 0.5f).toInt()

    private fun toast(message: Int) = Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
}
