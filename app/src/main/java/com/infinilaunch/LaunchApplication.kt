package com.infinilaunch

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class LaunchApplication : Application() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = ThreadPoolExecutor(0, 1, 15L, TimeUnit.SECONDS, LinkedBlockingQueue())
    private val pinWorker = ThreadPoolExecutor(0, 1, 15L, TimeUnit.SECONDS, LinkedBlockingQueue())
    private val listeners = linkedSetOf<(SyncState) -> Unit>()
    private val pinListeners = linkedSetOf<() -> Unit>()
    private var syncState: SyncState? = null
    private var startupSyncRequested = false
    private var running = false
    private var again = false

    private val runPass = Runnable { runSync() }
    private val packages = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            if (user == Process.myUserHandle()) requestSync()
        }

        override fun onPackageRemoved(packageName: String, user: UserHandle) = Unit
        override fun onPackageChanged(packageName: String, user: UserHandle) = Unit
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = Unit
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(LauncherApps::class.java).registerCallback(packages, main)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.install_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    internal fun addSyncListener(listener: (SyncState) -> Unit) {
        listeners.add(listener)
        syncState?.let(listener)
    }

    internal fun removeSyncListener(listener: (SyncState) -> Unit) {
        listeners.remove(listener)
    }

    internal fun addPinListener(listener: () -> Unit) {
        pinListeners.add(listener)
    }

    internal fun removePinListener(listener: () -> Unit) {
        pinListeners.remove(listener)
    }

    internal fun pinShortcut(request: LauncherApps.PinItemRequest, densityDpi: Int) {
        pinWorker.execute {
            val launcher = getSystemService(LauncherApps::class.java)
            val result = runCatching {
                if (!request.isValid || !launcher.hasShortcutHostPermission()) return@execute
                val shortcut = request.shortcutInfo?.takeIf { it.userHandle == Process.myUserHandle() } ?: return@execute
                val label = shortcut.shortLabel?.toString()?.take(200)?.takeUnless(String::isBlank)
                    ?: shortcut.id.take(200)
                val programs = Programs(this)
                val icon = shortcutIcon(launcher, shortcut, densityDpi)
                val pin = programs.pinShortcut(shortcut.getPackage(), shortcut.id, label, icon)
                var accepted = false
                try {
                    accepted = request.accept()
                    check(accepted) { getString(R.string.pin_shortcut_failed) }
                } finally {
                    if (!accepted) programs.removePin(pin)
                }
            }
            main.post {
                result.onSuccess {
                    pinListeners.toList().forEach { it() }
                    Toast.makeText(this, R.string.pin_shortcut_done, Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this, it.message ?: getString(R.string.pin_shortcut_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shortcutIcon(launcher: LauncherApps, shortcut: ShortcutInfo, densityDpi: Int): String? = runCatching {
        val drawable = launcher.getShortcutIconDrawable(shortcut, densityDpi) ?: return null
        val size = 72
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        try {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(bitmap))
            val bytes = ByteArrayOutputStream()
            if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)) {
                Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
            } else null
        } finally {
            bitmap.recycle()
        }
    }.getOrNull()

    internal fun requestStartupSync() {
        if (startupSyncRequested) return
        startupSyncRequested = true
        requestSync()
    }

    internal fun requestSync() {
        if (running) {
            again = true
            return
        }
        main.removeCallbacks(runPass)
        main.postDelayed(runPass, 400L)
    }

    private fun runSync() {
        running = true
        publishSync(SyncState.Checking)
        worker.execute {
            val result = runCatching {
                val programs = Programs(this)
                programs.sync()?.let { notice ->
                    postNotice(notice)
                    programs.acknowledge(notice.id)
                }
            }.map { Unit }
            result.exceptionOrNull()?.let { Log.w(TAG, "Programs sync failed", it) }
            main.post {
                running = false
                publishSync(SyncState.Finished(result))
                if (again) {
                    again = false
                    requestSync()
                }
            }
        }
    }

    private fun publishSync(state: SyncState) {
        syncState = state
        listeners.toList().forEach { it(state) }
    }

    internal sealed interface SyncState {
        data object Checking : SyncState
        data class Finished(val result: Result<Unit>) : SyncState
    }

    private fun postNotice(notice: Programs.Notice) {
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val places = notice.labels.zip(notice.folders) { app, folder -> "$app → $folder" }
        val title = if (places.size == 1) notice.labels.single() else getString(R.string.install_summary, places.size)
        val intent = Intent(this, HomeActivity::class.java).apply {
            action = OPEN_PROGRAMS
            putExtra(EXTRA_CATEGORY, notice.category)
            data = android.net.Uri.fromParts("infinilaunch-notice", notice.id, null)
        }
        val open = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val summary = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_install_notice)
            .setContentTitle(title)
            .setContentText(if (places.size == 1) notice.folders.single() else places.first())
            .setStyle(Notification.BigTextStyle().bigText(places.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            manager.notify(notice.id, 1, summary)
        } catch (denied: SecurityException) {
            if (manager.areNotificationsEnabled()) throw denied
        }
    }

    companion object {
        internal const val PREFERENCES = "infinilaunch_preferences"
        internal const val PREF_LAYOUT_LOCKED = "layout_locked"
        internal const val OPEN_PROGRAMS = "com.infinilaunch.OPEN_PROGRAMS"
        internal const val EXTRA_CATEGORY = "category"
        private const val CHANNEL = "new_applications"
        private const val TAG = "InfiniLaunch"
    }
}
