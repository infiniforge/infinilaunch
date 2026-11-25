package com.infinilaunch

import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextClock
import android.widget.Toast
import java.util.Locale

/** Default system Clock slot; shows the Android time while widget binding is unavailable. */
internal class SystemClockWidget(
    private val activity: Activity,
    saveBinding: (WidgetBinding) -> Unit,
) : FrameLayout(activity) {
    private val widgets = AppWidgetSession(activity, 0x494C, 41) { it?.let(saveBinding) }
    private var displayed: WidgetBinding? = null
    private var triedAutomaticBind = false

    init {
        isClickable = true
        show(null)
    }

    fun start() = widgets.start()
    fun stop() = widgets.stop()
    fun savePending(state: Bundle) = widgets.save(state)
    fun restorePending(state: Bundle?) = widgets.restore(state)
    fun onResult(request: Int, result: Int) = widgets.onResult(request, result)
    fun release(id: Int) = widgets.release(id)
    fun reconcile(binding: WidgetBinding?) = widgets.reconcile(setOfNotNull(binding?.id))

    fun show(binding: WidgetBinding?) {
        val available = binding?.takeIf { widgets.provider(it) != null }
        if (childCount > 0 && displayed == available) return
        removeAllViews()
        displayed = null
        val hosted = available?.let(widgets::view)
        if (hosted != null) {
            displayed = available
            addView(hosted, LayoutParams(-1, -1))
            return
        }
        addView(TextClock(activity).apply {
            format12Hour = DateFormat.getBestDateTimePattern(Locale.getDefault(), "hma")
            format24Hour = DateFormat.getBestDateTimePattern(Locale.getDefault(), "Hm")
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
            setOnClickListener { bind(interactive = true) }
            tooltipText = context.getString(R.string.add_system_clock)
        }, LayoutParams(-1, -1))
    }

    fun bindAutomatically() {
        if (triedAutomaticBind) return
        triedAutomaticBind = true
        bind(interactive = false)
    }

    private fun bind(interactive: Boolean) {
        if (displayed != null || widgets.isBinding) return
        val provider = findClock()
        if (provider != null) widgets.bind(provider, interactive)
        else if (interactive) Toast.makeText(activity, R.string.clock_unavailable, Toast.LENGTH_SHORT).show()
    }

    @Suppress("DEPRECATION")
    private fun findClock(): AppWidgetProviderInfo? {
        val pm = context.packageManager
        val alarmPackage = pm.resolveActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        return widgets.providers().filter { info ->
            val name = info.provider.packageName
            val system = runCatching {
                pm.getApplicationInfo(name, 0).flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            }.getOrDefault(false)
            system && (name == alarmPackage || name in CLOCK_PACKAGES)
        }.minWithOrNull(compareBy<AppWidgetProviderInfo>(
            { it.provider.packageName != alarmPackage },
            { !it.provider.className.contains("digital", ignoreCase = true) },
            { it.provider.className },
        ))
    }

    companion object {
        private val CLOCK_PACKAGES = setOf("com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage")
    }
}
