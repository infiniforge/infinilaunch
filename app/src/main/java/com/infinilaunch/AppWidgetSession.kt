package com.infinilaunch

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.os.Process

/** Android binding, configuration and view lifecycle shared by Clock and workspace widgets. */
internal class AppWidgetSession(
    private val activity: Activity,
    hostId: Int,
    private val bindRequest: Int,
    private val onBound: (WidgetBinding?) -> Unit,
) {
    private val manager = AppWidgetManager.getInstance(activity)
    private val host = AppWidgetHost(activity, hostId)
    private val pendingKey = "widgetRequest$hostId"
    private var pending: Int? = null
    val isBinding get() = pending != null

    fun start() = host.startListening()
    fun stop() = host.stopListening()

    fun restore(state: Bundle?) {
        pending = state?.getInt(pendingKey, 0)?.takeIf { it > 0 }
    }

    fun save(state: Bundle) {
        pending?.let { state.putInt(pendingKey, it) }
    }

    fun providers(): List<AppWidgetProviderInfo> =
        manager.getInstalledProvidersForProfile(Process.myUserHandle())
            .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }

    fun provider(binding: WidgetBinding): AppWidgetProviderInfo? =
        runCatching { manager.getAppWidgetInfo(binding.id) }.getOrNull()
            ?.takeIf { it.provider.flattenToString() == binding.provider }

    fun view(binding: WidgetBinding): AppWidgetHostView? {
        val info = provider(binding) ?: return null
        val view = runCatching { host.createView(activity, binding.id, info) }.getOrNull() ?: return null
        view.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (r - l != oldR - oldL || b - t != oldB - oldT) {
                val density = activity.resources.displayMetrics.density
                val width = ((r - l) / density).toInt()
                val height = ((b - t) / density).toInt()
                if (width > 0 && height > 0) {
                    @Suppress("DEPRECATION")
                    view.updateAppWidgetSize(Bundle(), width, height, width, height)
                }
            }
        }
        return view
    }

    fun bind(info: AppWidgetProviderInfo, interactive: Boolean) {
        if (pending != null || (!interactive && info.configure != null)) return
        val id = host.allocateAppWidgetId()
        pending = id
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        }
        val bound = runCatching { manager.bindAppWidgetIdIfAllowed(id, info.provider, options) }.getOrDefault(false)
        when {
            bound -> configureOrComplete(id)
            interactive -> {
                val launched = runCatching {
                    @Suppress("DEPRECATION")
                    activity.startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
                    }, bindRequest)
                }.isSuccess
                if (!launched) complete(null)
            }
            else -> complete(null)
        }
    }

    fun onResult(request: Int, result: Int): Boolean {
        if (request != bindRequest && request != bindRequest + 1) return false
        val id = pending ?: return true
        when {
            result != Activity.RESULT_OK -> complete(null)
            request == bindRequest -> configureOrComplete(id)
            else -> complete(manager.getAppWidgetInfo(id))
        }
        return true
    }

    private fun configureOrComplete(id: Int) {
        val info = manager.getAppWidgetInfo(id)
        if (info == null || info.configure == null) {
            complete(info)
            return
        }
        val launched = runCatching {
            host.startAppWidgetConfigureActivityForResult(activity, id, 0, bindRequest + 1, null)
        }.isSuccess
        if (!launched) complete(null)
    }

    private fun complete(info: AppWidgetProviderInfo?) {
        val id = pending ?: return
        pending = null
        if (info == null) release(id)
        onBound(info?.let { WidgetBinding(id, it.provider.flattenToString()) })
    }

    fun reconcile(kept: Set<Int>) {
        host.appWidgetIds.filter { it != pending && it !in kept }.forEach(::release)
    }

    fun release(id: Int) {
        runCatching { host.deleteAppWidgetId(id) }
    }
}
