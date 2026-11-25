package com.infinilaunch

import android.app.Activity
import android.content.pm.LauncherApps
import android.os.Bundle

/** Android's pin request entry point. The application worker handles storage. */
class PinShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (intent.action != LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT) return
            val launcher = getSystemService(LauncherApps::class.java)
            val request = runCatching { launcher.getPinItemRequest(intent) }.getOrNull() ?: return
            if (request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
                (application as LaunchApplication).pinShortcut(request, resources.displayMetrics.densityDpi)
            }
        } finally {
            // Theme.NoDisplay requires finishing before this activity resumes.
            finish()
        }
    }
}
