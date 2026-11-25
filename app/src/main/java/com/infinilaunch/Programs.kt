package com.infinilaunch

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import io.github.gretchycat.infiniselect.FileSelectionContract
import io.github.gretchycat.infiniselect.FolderStorage

/** Client for InfiniFolder's app-specific Programs provider contract. */
internal class Programs(private val context: Context) {
    data class Directory(val uri: Uri, val category: String?)
    data class DockSettings(
        val heightDp: Int,
        val backgroundAlpha: Int,
        val backgroundColor: String?,
        val borderAlpha: Int,
        val borderWidthDp: Int,
        val borderColor: String,
    )
    data class Dock(val uri: Uri, val configXml: String?, val settings: DockSettings, val supportsSettings: Boolean)
    data class Notice(val id: String, val labels: List<String>, val folders: List<String>, val category: String?)
    data class DesktopEntry(val filename: String, val label: String, val mime: String,
        val packageName: String?, val shortcutId: String?, val folder: Boolean, val isLink: Boolean = false)
    data class DesktopAppearance(val label: String, val mime: String, val folder: Boolean,
        val fallback: String, val icon: Bitmap?)
    data class Desktop(val name: String, val layout: String?, val workspaces: List<String>, val files: List<DesktopEntry>)
    data class DesktopPin(val workspace: String, val reference: String)

    fun directory(category: String?): Directory {
        val reply = call("directory") { putString("category", category) }
        return Directory(Uri.parse(requireNotNull(reply.getString("uri"))), reply.getString("category"))
    }

    fun dock(): Dock = decodeDock(call("dock"))

    fun saveDockSettings(expected: String, settings: DockSettings): Dock = decodeDock(
        call("save-dock-settings") {
            putString("expected", expected)
            putInt("heightDp", settings.heightDp)
            putInt("backgroundAlpha", settings.backgroundAlpha)
            putString("backgroundColor", settings.backgroundColor)
            putInt("borderAlpha", settings.borderAlpha)
            putInt("borderWidthDp", settings.borderWidthDp)
            putString("borderColor", settings.borderColor)
        }
    )

    private fun decodeDock(reply: Bundle): Dock {
        val configXml = reply.getString("configXml")
        return Dock(
            Uri.parse(requireNotNull(reply.getString("uri"))),
            configXml,
            DockSettings(
                reply.getInt("heightDp", 100),
                reply.getInt("backgroundAlpha", 30),
                reply.getString("backgroundColor"),
                reply.getInt("borderAlpha", 50),
                reply.getInt("borderWidthDp", 1),
                reply.getString("borderColor") ?: "#FFBAD8E4",
            ),
            supportsSettings = configXml != null,
        )
    }

    fun removeDockEntry(uri: Uri) {
        call("remove-dock-entry") { putString("uri", uri.toString()) }
    }

    fun moveDockEntry(uri: Uri, direction: Int): Boolean {
        require(direction == -1 || direction == 1)
        return call("move-dock-entry") {
            putString("uri", uri.toString())
            putInt("direction", direction)
        }.getBoolean("changed")
    }

    fun desktop(name: String? = null, create: Boolean = false): Desktop {
        val operation = when {
            create -> "create-desktop"
            name != null -> "select-desktop"
            else -> "desktop"
        }
        val reply = call(operation) { putString("name", name) }
        val entries = if (Build.VERSION.SDK_INT >= 33) reply.getParcelableArrayList("launchers", Bundle::class.java)
        else @Suppress("DEPRECATION") reply.getParcelableArrayList<Bundle>("launchers")
        return Desktop(requireNotNull(reply.getString("name")), reply.getString("layout"),
            reply.getStringArrayList("workspaces").orEmpty(), entries.orEmpty().map {
                DesktopEntry(requireNotNull(it.getString("name")), requireNotNull(it.getString("label")),
                    it.getString("mime").orEmpty(), it.getString("package"), it.getString("shortcutId"),
                    it.getBoolean("folder"), it.getBoolean("isLink"))
            })
    }

    fun desktopAppearance(workspace: String, entry: String, iconSize: Int): DesktopAppearance {
        val reply = call("desktop-entry-appearance") {
            putString("name", workspace)
            putString("entry", entry)
            putInt("iconSize", iconSize)
        }
        val icon = if (Build.VERSION.SDK_INT >= 33) reply.getParcelable("icon", Bitmap::class.java)
        else @Suppress("DEPRECATION") reply.getParcelable("icon")
        return DesktopAppearance(requireNotNull(reply.getString("label")), reply.getString("mime").orEmpty(),
            reply.getBoolean("folder"), reply.getString("fallback").orEmpty(), icon)
    }

    fun workspacePreview(directory: Uri): Bitmap? {
        val reply = call("workspace-preview") { putString("directory", directory.toString()) }
        return if (Build.VERSION.SDK_INT >= 33) reply.getParcelable("preview", Bitmap::class.java)
        else @Suppress("DEPRECATION") reply.getParcelable("preview")
    }

    fun saveWorkspace(name: String, expected: String?, layout: String) {
        call("save-desktop") {
            putString("name", name)
            putString("expected", expected)
            putString("layout", layout)
        }
    }

    fun openSelection(selection: Uri): OpenTarget =
        openTarget(call("open-selection") { putString("selection", selection.toString()) })

    fun openDesktopEntry(workspace: String, filename: String?): OpenTarget =
        openTarget(call("open-desktop-entry") {
            putString("name", workspace)
            putString("entry", filename)
        })

    fun createDesktopFolder(workspace: String, folder: String) {
        call("create-desktop-folder") {
            putString("name", workspace)
            putString("folder", folder)
        }
    }

    fun transferDesktopEntry(
        workspace: String,
        filename: String,
        destination: String?,
        move: Boolean,
        changed: IBinder,
    ): OpenTarget = openTarget(call("transfer-desktop-entry") {
            putString("name", workspace)
            putString("entry", filename)
            putString("destination", destination)
            putBoolean("move", move)
            putBinder("home-callback", changed)
        })

    fun updateDockDropPreview(screen: PointF, dock: BrowserPoint?) {
        call("dock-drop-preview") {
            putBoolean("active", dock != null)
            putFloat("screenX", screen.x)
            putFloat("screenY", screen.y)
            dock?.let {
                putFloat("localX", it.point.x)
                putFloat("localY", it.point.y)
            }
        }
    }

    fun registerDragHost(lifetime: IBinder, callback: IBinder?) {
        call("register-drag-host") {
            putBinder("lifetime", lifetime)
            putBinder("callback", callback)
        }
    }

    data class BrowserPoint(val surface: String, val point: PointF)

    fun routeDrop(
        workspace: String,
        entry: String?,
        token: String?,
        screen: PointF,
        browser: BrowserPoint?,
        desktopFolder: String?,
        occupied: Boolean,
        expected: String?,
        layout: WorkspaceLayout,
        cell: WorkspacePlacement?,
        changed: IBinder,
        createDockShortcutForNonLinks: Boolean = false,
    ): OpenTarget = openTarget(call("route-drop") {
        putString("name", workspace)
        putString("entry", entry)
        putString("drag", token)
        putFloat("screenX", screen.x)
        putFloat("screenY", screen.y)
        browser?.let {
            putString("surface", it.surface)
            putFloat("localX", it.point.x)
            putFloat("localY", it.point.y)
        }
        putString("destination", desktopFolder)
        putBoolean("occupied", occupied)
        putBoolean("createDockShortcutForNonLinks", createDockShortcutForNonLinks)
        cell?.let {
            putString("expected", expected)
            putString("layout", layout.encode())
            putInt("column", it.column)
            putInt("row", it.row)
        }
        putBinder("home-callback", changed)
    })

    fun removeDesktopEntry(workspace: String, filename: String) {
        call("remove-desktop-entry") {
            putString("name", workspace)
            putString("entry", filename)
        }
    }

    private fun openTarget(reply: Bundle): OpenTarget {
        if (reply.getBoolean("ignored")) return OpenTarget.Ignored
        if (reply.getBoolean("desktopChanged")) return OpenTarget.DesktopChanged
        reply.getString("package")?.let { return OpenTarget.App(it) }
        reply.getString("shortcutPackage")?.let {
            return OpenTarget.Pin(it, requireNotNull(reply.getString("shortcutId")))
        }
        val intent = if (Build.VERSION.SDK_INT >= 33) {
            reply.getParcelable("launch", PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            reply.getParcelable<PendingIntent>("launch")
        }
        return OpenTarget.Launch(requireNotNull(intent))
    }

    fun pinShortcut(packageName: String, id: String, label: String, icon: String?): DesktopPin {
        val reply = call("pin-desktop-shortcut") {
            putString("package", packageName)
            putString("shortcutId", id)
            putString("label", label)
            putString("icon", icon)
        }
        return DesktopPin(requireNotNull(reply.getString("workspace")), requireNotNull(reply.getString("pin")))
    }

    fun removePin(pin: DesktopPin) {
        call("remove-desktop-pin") {
            putString("name", pin.workspace)
            putString("pin", pin.reference)
        }
    }

    sealed interface OpenTarget {
        data object Ignored : OpenTarget
        data object DesktopChanged : OpenTarget
        data class App(val packageName: String) : OpenTarget
        data class Pin(val packageName: String, val id: String) : OpenTarget
        data class Launch(val pending: PendingIntent) : OpenTarget
    }

    fun sync(): Notice? {
        val reply = call("sync")
        val id = reply.getString("noticeId") ?: return null
        val labels = requireNotNull(reply.getStringArrayList("labels"))
        val folders = requireNotNull(reply.getStringArrayList("folders"))
        require(labels.isNotEmpty() && labels.size == folders.size)
        return Notice(id, labels, folders, reply.getString("category"))
    }

    fun acknowledge(id: String) {
        call("acknowledge") { putString("noticeId", id) }
    }

    private fun call(operation: String, arguments: Bundle.() -> Unit = {}): Bundle {
        check(FolderStorage.available(context)) { context.getString(R.string.folder_missing) }
        val args = Bundle().apply {
            putInt("version", 1)
            putString("operation", operation)
            arguments()
        }
        val reply = try {
            context.contentResolver.call(AUTHORITY, METHOD, null, args)
        } catch (unsupported: UnsupportedOperationException) {
            throw IllegalStateException(context.getString(R.string.folder_update_needed), unsupported)
        }
        check(reply?.getInt("version") == 1) { context.getString(R.string.folder_update_needed) }
        val result = requireNotNull(reply)
        result.getString("error")?.let { error(it) }
        return result
    }

    companion object {
        private val AUTHORITY = Uri.parse("content://" + FileSelectionContract.INFINIFOLDER_SELECTION_AUTHORITY)
        private const val METHOD = "com.infinifolder.launch.PROGRAMS"
    }
}
