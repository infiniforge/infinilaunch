package com.infinilaunch

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.DragEvent
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import io.github.gretchycat.infiniselect.EmbeddedFileBrowser
import io.github.gretchycat.infiniselect.SelectionAction
import java.util.concurrent.Executors
import kotlin.math.abs

class HomeActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val selectionWorker = Executors.newSingleThreadExecutor()
    private val dockDropPreviewHandler = Handler(Looper.getMainLooper())
    private data class DockDropPreviewRequest(val screen: PointF, val dock: Programs.BrowserPoint?)
    private var pendingDockDropPreview: DockDropPreviewRequest? = null
    private var dockDropPreviewScheduled = false
    private var dockDropPreviewActive = false
    private val dispatchDockDropPreview = Runnable {
        dockDropPreviewScheduled = false
        val request = pendingDockDropPreview ?: return@Runnable
        pendingDockDropPreview = null
        val active = request.dock != null
        if (!active && !dockDropPreviewActive) return@Runnable
        dockDropPreviewActive = active
        if (!selectionWorker.isShutdown) selectionWorker.execute {
            runCatching { Programs(applicationContext).updateDockDropPreview(request.screen, request.dock) }
        }
    }
    private val previewWorker = Executors.newSingleThreadExecutor()
    private val appearanceWorker = Executors.newSingleThreadExecutor()
    private val launcher get() = application as LaunchApplication
    private lateinit var workspace: WorkspaceView
    private val dragHostLifetime = Binder()
    private val folderDragPreview by lazy { FolderDragPreview(this) }
    private val homeCallbacks: IBinder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                reply?.writeString(CALLBACK_DESCRIPTOR)
                return true
            }
            if (code != CALLBACK_DRAG && code != CALLBACK_REFRESH) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(CALLBACK_DESCRIPTOR)
            if (data.readInt() != CALLBACK_VERSION) return false
            if (code == CALLBACK_DRAG) {
                val action = data.readInt()
                val payload = Bundle().apply {
                    putString("surface", data.readString())
                    putFloat("x", data.readFloat())
                    putFloat("y", data.readFloat())
                    putString("drag", data.readString())
                    putParcelable("icon", readDraggedBitmap(data))
                    putBoolean("isLink", data.dataAvail() < Int.SIZE_BYTES || data.readInt() != 0)
                }
                runOnUiThread { if (started) receiveFolderDrag(action, payload) }
            } else {
                runOnUiThread {
                    if (!isDestroyed) {
                        refreshDesktop()
                        browser?.refresh()
                        dock?.refresh()
                    }
                }
            }
            return true
        }
    }
    private lateinit var clock: SystemClockWidget
    private lateinit var applicationWidgets: ApplicationWidgets
    private lateinit var desktopLaunchers: DesktopLaunchers
    private lateinit var programsSlot: FrameLayout
    private lateinit var dockSlot: FrameLayout
    private lateinit var dockHost: FrameLayout
    private lateinit var editControls: LinearLayout
    private lateinit var editStatus: TextView
    private lateinit var editPreviousButton: Button
    private lateinit var editNextButton: Button
    private lateinit var cancelEditButton: Button
    private lateinit var saveButton: Button
    private lateinit var removeButton: Button
    private lateinit var syncIndicator: View
    private lateinit var browserBackdrops: BrowserBackdrops
    private lateinit var status: TextView
    private var browser: EmbeddedFileBrowser? = null
    private var browserRoot: android.net.Uri? = null
    private var dock: EmbeddedFileBrowser? = null
    private var dockDetails: Programs.Dock? = null

    private val workspaceTransition = WorkspaceTransition.Immediate
    private var desktop: Programs.Desktop? = null
    private var loadingWorkspace = false
    private var desktopRefreshPending = false
    private var storedLayout = WorkspaceLayout()
    private var layout = storedLayout
    private var savedLayout: String? = null
    private var layoutLoaded = false
    private var saving = false
    private var started = false
    private var generation = 0
    private var directoryRequest = 0
    private var loadingPrograms = false
    private var loadingCategory: String? = null
    private var loadingDock = false
    private var requestedCategory: String? = null
    private var pendingPinnedShortcut: android.net.Uri? = null
    private var openFromNotice = false
    private var editor: WorkspaceEditor? = null
    private var displayedLayout = WorkspaceLayout()
    private var pendingHostedWidget: HostedWidget? = null

    private fun refreshDesktop() {
        desktopRefreshPending = true
        loadWorkspace()
    }

    private fun onSyncState(state: LaunchApplication.SyncState) {
        syncIndicator.visibility = if (state is LaunchApplication.SyncState.Checking) View.VISIBLE else View.INVISIBLE
        if (state !is LaunchApplication.SyncState.Finished) return
        state.result.onSuccess {
            if (layoutLoaded) status.visibility = View.GONE
            if (layoutLoaded && editor == null) {
                if (browser != null) browser?.refresh()
                else if (displayedLayout.programs != null && !loadingPrograms) openPrograms(requestedCategory)
            }
            if (dock != null) dock?.refresh() else if (!loadingDock) openDock()
        }.onFailure {
            showStatus(getString(R.string.sync_delayed, it.message ?: getString(R.string.programs_unavailable)))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
        }
        readDestination(intent)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setOnApplyWindowInsetsListener { view, insets ->
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
                insets
            }
        }
        syncIndicator = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = panel(0xDF18282D.toInt())
            setPadding(dp(8), 0, dp(10), 0)
            visibility = View.INVISIBLE
            addView(ProgressBar(this@HomeActivity).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(16), dp(16)))
            addView(TextView(this@HomeActivity).apply {
                setText(R.string.checking_programs)
                setTextColor(Color.WHITE)
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        }
        root.addView(syncIndicator, LinearLayout.LayoutParams(-2, dp(32)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        root.setOnDragListener { view, event -> handleDesktopDrop(view, event) }
        workspace = WorkspaceView(this).apply {
            setOnLongClickListener {
                showHomeOptions()
                true
            }
            dockHitTest = { x, y -> browserPoint(PointF(x, y), dock, "dock") != null }
            onDesktopDragPointer = { x, y, active -> requestDockDropPreview(PointF(x, y), active) }
        }
        installWorkspaceGesture()
        desktopLaunchers = DesktopLaunchers(this, workspace, ::openDesktopEntry, ::showDesktopItemMenu,
            ::dropDesktopItem, ::loadDesktopAppearance)
        applicationWidgets = ApplicationWidgets(this, workspace, ::addHostedWidget) { id, label ->
            beginEditing(WorkspaceItem.App(id), label)
        }
        applicationWidgets.restore(savedInstanceState)
        root.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))
        dockSlot = FrameLayout(this)
        editPreviousButton = Button(this).apply {
            text = "‹"
            contentDescription = getString(R.string.previous_desktop_item)
            setOnClickListener { editor?.selectPrevious() }
        }
        editNextButton = Button(this).apply {
            text = "›"
            contentDescription = getString(R.string.next_desktop_item)
            setOnClickListener { editor?.selectNext() }
        }
        editStatus = TextView(this).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 12f
            maxLines = 2
            includeFontPadding = false
        }
        cancelEditButton = Button(this).apply {
            setText(android.R.string.cancel)
            setOnClickListener { closeEditor() }
        }
        saveButton = Button(this).apply {
            setText(R.string.save_layout)
            setOnClickListener { finishEditing(remove = false) }
        }
        removeButton = Button(this).apply {
            setText(R.string.remove_widget)
            setOnClickListener { finishEditing(remove = true) }
        }
        val selectionRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(editPreviousButton, LinearLayout.LayoutParams(dp(48), -1))
            addView(editStatus, LinearLayout.LayoutParams(0, -1, 1f))
            addView(editNextButton, LinearLayout.LayoutParams(dp(48), -1))
        }
        val actionRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(cancelEditButton, LinearLayout.LayoutParams(0, -1, 1f))
            addView(removeButton, LinearLayout.LayoutParams(0, -1, 1f))
            addView(saveButton, LinearLayout.LayoutParams(0, -1, 1f))
        }
        editControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = panel(0xD718282D.toInt())
            visibility = View.GONE
            addView(selectionRow, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(actionRow, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        dockHost = FrameLayout(this).apply {
            addView(dockSlot, FrameLayout.LayoutParams(-1, -1))
            addView(editControls, FrameLayout.LayoutParams(-1, -1))
        }
        root.addView(dockHost, LinearLayout.LayoutParams(-1, dp(100)))

        clock = SystemClockWidget(this, ::saveClockBinding).apply { restorePending(savedInstanceState) }
        workspace.putWidget(WorkspaceItem.Clock, WidgetHoldFrame(this) {
            beginEditing(WorkspaceItem.Clock, getString(R.string.clock_widget))
        }.apply { addView(clock, FrameLayout.LayoutParams(-1, -1)) })
        programsSlot = WidgetHoldFrame(this) {
            beginEditing(WorkspaceItem.Programs, getString(R.string.programs))
        }
        workspace.putWidget(WorkspaceItem.Programs, programsSlot)

        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = panel(0xAD18282D.toInt())
            maxWidth = dp(190)
            visibility = View.GONE
        }
        workspace.addView(status, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.LEFT).apply {
            leftMargin = dp(8)
            topMargin = dp(8)
        })

        browserBackdrops = BrowserBackdrops(this, root, programsSlot, dockSlot)
        workspace.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val width = right - left
            val height = bottom - top
            if (started && !saving && (width != oldRight - oldLeft || height != oldBottom - oldTop)) {
                val projected = projectedLayout()
                if (projected != layout) {
                    closeEditor()
                    applyWorkspace(projected)
                }
            }
        }
        setContentView(root)
        root.requestApplyInsets()
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                ::navigateBack,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        started = true
        registerDragHost(homeCallbacks)
        pendingPinnedShortcut?.also { uri ->
            pendingPinnedShortcut = null
            openPinnedShortcut(uri)
        }
        clock.start()
        applicationWidgets.start()
        if (dock == null) openDock() else dock?.refresh()
        browserBackdrops.start()
        launcher.addSyncListener(::onSyncState)
        launcher.addPinListener(::refreshDesktop)
        launcher.requestStartupSync()
    }

    override fun onResume() {
        super.onResume()
        loadWorkspace()
    }

    override fun onStop() {
        dockConfigurationDialog?.dismiss()
        workspace.cancelDesktopMove()
        workspace.showDropGrid(false)
        folderDragPreview.close()
        started = false
        registerDragHost(null)
        loadingWorkspace = false
        ++generation
        ++directoryRequest
        loadingPrograms = false
        loadingDock = false
        closeEditor()
        browserBackdrops.stop()
        launcher.removeSyncListener(::onSyncState)
        launcher.removePinListener(::refreshDesktop)
        clock.stop()
        applicationWidgets.stop()
        super.onStop()
    }

    override fun onDestroy() {
        worker.shutdown()
        clearDockDropPreview()
        selectionWorker.shutdown()
        previewWorker.shutdown()
        appearanceWorker.shutdown()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        clock.savePending(outState)
        applicationWidgets.save(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!clock.onResult(requestCode, resultCode) && !applicationWidgets.onResult(requestCode, resultCode)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_PINNED_SHORTCUT) {
            closeEditor()
            val target = intent.data
            if (started && target != null) openPinnedShortcut(target) else pendingPinnedShortcut = target
            return
        }
        if (readDestination(intent) && started) {
            closeEditor()
            applyWorkspace()
        }
    }

    private fun readDestination(intent: Intent): Boolean {
        if (intent.action == ACTION_OPEN_PINNED_SHORTCUT) {
            pendingPinnedShortcut = intent.data
            return true
        }
        if (intent.action != LaunchApplication.OPEN_PROGRAMS) return false
        openFromNotice = true
        requestedCategory = intent.getStringExtra(LaunchApplication.EXTRA_CATEGORY)
        return true
    }

    private fun openPinnedShortcut(uri: android.net.Uri) {
        if (uri.scheme != "infinilaunch-shortcut" || uri.authority != "shortcut" ||
            uri.pathSegments.size != 2 || uri.getQueryParameter("pin").isNullOrBlank()
        ) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show()
            return
        }
        selectionWorker.execute {
            val result = runCatching { Programs(applicationContext).openSelection(uri) }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                result.mapCatching(::openFromHome).onFailure {
                    Toast.makeText(this, it.message ?: getString(R.string.open_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun readDraggedBitmap(data: Parcel): Bitmap? = if (Build.VERSION.SDK_INT >= 33) {
        data.readParcelable(Bitmap::class.java.classLoader, Bitmap::class.java)
    } else {
        data.readParcelable(Bitmap::class.java.classLoader)
    }

    @Deprecated("Required for Android 11 and 12")
    override fun onBackPressed() = navigateBack()

    private fun navigateBack() {
        if (editor != null) {
            if (!saving) closeEditor()
            return
        }
        if (browser?.goBack() == true) return
        if (requestedCategory != null) openPrograms(null)
        else if (!getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)) finish()
    }

    private fun loadWorkspace(name: String? = null, create: Boolean = false, direction: Int = 0) {
        if (!started || saving || editor != null || loadingWorkspace) return
        val version = generation
        loadingWorkspace = true
        desktopRefreshPending = false
        worker.execute {
            val outcome = runCatching {
                val selected = Programs(applicationContext).desktop(name, create)
                selected to WorkspaceLayout.decode(selected.layout)
            }
            runOnUiThread {
                if (!started || version != generation) return@runOnUiThread
                outcome.onSuccess { (selected, loaded) ->
                    val commit = {
                        if (started && version == generation) {
                            loadingWorkspace = false
                            if (desktop != null && desktop?.name != selected.name) {
                                requestedCategory = null
                                openFromNotice = false
                            }
                            desktopLaunchers.invalidateAppearances()
                            desktop = selected
                            savedLayout = selected.layout
                            storedLayout = loaded
                            layoutLoaded = true
                            applyWorkspace()
                            val pending = pendingHostedWidget
                            pendingHostedWidget = null
                            if (pending != null) addHostedWidget(pending)
                            else if (layout.clockPlacement != null) clock.bindAutomatically()
                            if (desktopRefreshPending) loadWorkspace()
                        }
                    }
                    val previous = desktop?.name
                    if (previous != null && previous != selected.name) {
                        workspaceTransition.switch(WorkspaceChange(previous, selected.name, direction), commit)
                    } else commit()
                }.onFailure {
                    loadingWorkspace = false
                    if (!layoutLoaded) applyWorkspace()
                    showStatus(it.message ?: getString(R.string.layout_unavailable))
                    if (desktopRefreshPending) loadWorkspace()
                }
            }
        }
    }

    private fun loadDesktopAppearance(
        workspaceName: String,
        entry: Programs.DesktopEntry,
        iconSize: Int,
        complete: (Programs.DesktopAppearance?) -> Unit,
    ) {
        appearanceWorker.execute {
            val appearance = runCatching {
                Programs(applicationContext).desktopAppearance(workspaceName, entry.filename, iconSize)
            }.getOrNull()
            runOnUiThread { if (!isDestroyed) complete(appearance) }
        }
    }

    private fun projectedLayout(columns: Int = storedLayout.grid.columns) =
        storedLayout.forDesktop(desktop?.files.orEmpty().map { it.filename }, workspace.width, workspace.height, columns)

    private fun installWorkspaceGesture() {
        var startX = 0f
        var startY = 0f
        var held = false
        val touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent) = true

            override fun onLongPress(event: MotionEvent) {
                held = true
                workspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        })
        workspace.setOnTouchListener { _, event ->
            if (editor != null) return@setOnTouchListener false
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                startX = event.x
                startY = event.y
                held = false
            }
            detector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val dx = event.x - startX
                val dy = event.y - startY
                if (held && abs(dx) <= touchSlop && abs(dy) <= touchSlop) {
                    workspace.performLongClick()
                } else if (!held && abs(dx) >= dp(52) && abs(dx) > abs(dy) * 1.4f) {
                    cycleWorkspace(if (dx < 0) 1 else -1)
                }
            }
            true
        }
    }

    private fun cycleWorkspace(direction: Int) {
        val current = desktop ?: return
        if (current.workspaces.size < 2) return
        val index = current.workspaces.indexOf(current.name)
        if (index < 0) return
        val next = (index + direction + current.workspaces.size) % current.workspaces.size
        loadWorkspace(current.workspaces[next], direction = direction)
    }

    private fun applyWorkspace(projected: WorkspaceLayout = projectedLayout()) {
        layout = projected
        displayedLayout = layout
        workspace.setWorkspace(layout)
        if (openFromNotice && layout.programs == null) {
            workspace.findSpace(1, 1, minSpan = 2)?.let { position ->
                displayedLayout = layout.copy(programs = position)
                workspace.setWorkspace(displayedLayout)
            }
        }
        clock.show(layout.clock)
        applicationWidgets.render(layout.hosted)
        desktop?.let { desktopLaunchers.show(it.name, it.files) }
            ?: desktopLaunchers.show("", emptyList())
        if (displayedLayout.programs != null) openPrograms(requestedCategory)
        else {
            ++directoryRequest
            loadingPrograms = false
            browser = null
            browserRoot = null
            programsSlot.removeAllViews()
        }
    }

    private fun openPrograms(target: String?, reset: Boolean = false) {
        if (displayedLayout.programs == null || (loadingPrograms && target == loadingCategory && !reset)) return
        requestedCategory = target
        loadingCategory = target
        loadingPrograms = true
        val version = ++directoryRequest
        if (browser == null) showSlotMessage(programsSlot, getString(R.string.opening_programs))
        worker.execute {
            val result = runCatching { Programs(applicationContext).directory(target) }
            runOnUiThread {
                if (!started || version != directoryRequest) return@runOnUiThread
                loadingPrograms = false
                result.onSuccess { directory ->
                    requestedCategory = directory.category
                    val label = directory.category?.replace("/", " › ") ?: getString(R.string.programs)
                    val old = browser
                    if (old != null && browserRoot == directory.uri && !reset) old.refresh()
                    else {
                        val view = createBrowser(programsSlot, directory.uri, label) { openPrograms(requestedCategory) }
                        browser = view
                        browserRoot = directory.uri
                    }
                }.onFailure {
                    browser = null
                    showSlotMessage(programsSlot, it.message ?: getString(R.string.programs_unavailable)) {
                        retry()
                    }
                }
            }
        }
    }

    private fun openDock() {
        if (loadingDock) return
        val version = generation
        loadingDock = true
        worker.execute {
            val result = runCatching { Programs(applicationContext).dock() }
            runOnUiThread {
                if (!started || version != generation) return@runOnUiThread
                loadingDock = false
                result.onSuccess { details ->
                    dockDetails = details
                    applyDockHeight(details.settings.heightDp)
                    dock = createBrowser(dockSlot, details.uri, getString(R.string.dock)) { openDock() }
                }.onFailure { error ->
                    dock = null
                    val reason = error.message?.takeIf(String::isNotBlank)
                    val message = if (reason == null) getString(R.string.dock_unavailable)
                        else "${getString(R.string.dock_unavailable)}\n$reason"
                    showSlotMessage(dockSlot, message) { retry() }
                }
            }
        }
    }

    private fun createBrowser(slot: FrameLayout, uri: android.net.Uri, label: String, reopen: () -> Unit): EmbeddedFileBrowser {
        val view = EmbeddedFileBrowser(this).apply {
            onRequestOpen = reopen
            onSelected = { openSelectedEntry(it.contentUri) }
            if (slot === programsSlot) {
                fileActions = listOf(SelectionAction(WORKSPACE_EDIT_ACTION, getString(R.string.edit_widget)))
                onFileAction = { action, _ ->
                    if (action == WORKSPACE_EDIT_ACTION && parent === programsSlot) {
                        beginEditing(WorkspaceItem.Programs, getString(R.string.programs))
                    }
                }
            } else if (slot === dockSlot) {
                fileActions = listOf(SelectionAction(DOCK_CONFIGURATION_ACTION, "Dock settings"))
                onFileAction = { action, item ->
                    if (action == DOCK_CONFIGURATION_ACTION) showDockConfiguration(item.authoritativeUri, item.name)
                }
            }
            onError = { message ->
                if (parent === slot) {
                    if (slot === programsSlot) browser = null else dock = null
                    showSlotMessage(slot, message, reopen)
                }
            }
            open(uri, label, lockToDirectory = true)
        }
        slot.removeAllViews()
        slot.addView(view, FrameLayout.LayoutParams(-1, -1))
        return view
    }

    private fun openSelectedEntry(uri: android.net.Uri) = resolveAndOpen { it.openSelection(uri) }

    private fun calculatedDockIcons(heightDp: Int): Int {
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val widthPx = if (::dockSlot.isInitialized && dockSlot.width > 0) dockSlot.width
            else resources.displayMetrics.widthPixels
        val widthDp = (widthPx / density).toInt().coerceAtLeast(1)
        val iconDp = (heightDp - 32).coerceIn(24, 128)
        return (widthDp / (iconDp + 12)).coerceAtLeast(1)
    }

    private fun applyDockHeight(heightDp: Int) {
        if (!::dockHost.isInitialized) return
        val params = dockHost.layoutParams as? LinearLayout.LayoutParams ?: return
        params.height = dp(heightDp.coerceIn(56, 200))
        dockHost.layoutParams = params
    }

    private fun showDockConfiguration(uri: android.net.Uri, itemName: String) {
        val details = dockDetails ?: return
        if (!details.supportsSettings || details.configXml == null) {
            Toast.makeText(this, R.string.folder_update_needed, Toast.LENGTH_LONG).show()
            return
        }
        val initial = details.settings
        var expectedConfig = requireNotNull(details.configXml)
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), dp(8))
        }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))

        fun addLabel(text: String, top: Int = 10): TextView = TextView(this).apply {
            this.text = text
            textSize = 14f
            setPadding(0, dp(top), 0, dp(2))
            content.addView(this, LinearLayout.LayoutParams(-1, -2))
        }

        addLabel("Selected: $itemName")
        val itemActions = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(Button(this@HomeActivity).apply {
                text = "Earlier"
                setOnClickListener {
                    dockConfigurationDialog?.dismiss()
                    runDockAction(DOCK_MOVE_EARLIER_ACTION, uri)
                }
            }, LinearLayout.LayoutParams(0, dp(46), 1f))
            addView(Button(this@HomeActivity).apply {
                text = "Later"
                setOnClickListener {
                    dockConfigurationDialog?.dismiss()
                    runDockAction(DOCK_MOVE_LATER_ACTION, uri)
                }
            }, LinearLayout.LayoutParams(0, dp(46), 1f))
            addView(Button(this@HomeActivity).apply {
                text = "Remove"
                setOnClickListener {
                    dockConfigurationDialog?.dismiss()
                    runDockAction(DOCK_REMOVE_ACTION, uri)
                }
            }, LinearLayout.LayoutParams(0, dp(46), 1f))
        }
        content.addView(itemActions, LinearLayout.LayoutParams(-1, dp(48)))
        content.addView(View(this), LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(8)
            bottomMargin = dp(2)
        })
        addLabel("Dock options", 2)

        val heightLabel = addLabel("Height: ${initial.heightDp} dp", 2)
        val iconsLabel = addLabel("Icons before scrolling: ${calculatedDockIcons(initial.heightDp)} (automatic)", 2)
        val heightSeek = SeekBar(this).apply {
            max = 144
            progress = initial.heightDp - 56
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val height = progress + 56
                    heightLabel.text = "Height: $height dp"
                    iconsLabel.text = "Icons before scrolling: ${calculatedDockIcons(height)} (automatic)"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        content.addView(heightSeek, LinearLayout.LayoutParams(-1, dp(34)))

        val colorOptions = mutableListOf(
            "Wallpaper theme" to null,
            "Charcoal" to "#FF18282D",
            "Black" to "#FF000000",
            "White" to "#FFFFFFFF",
            "Navy" to "#FF18304A",
            "Forest" to "#FF193D32",
            "Plum" to "#FF42243D",
        )
        if (initial.backgroundColor != null && colorOptions.none { it.second == initial.backgroundColor }) {
            colorOptions.add("Current color" to initial.backgroundColor)
        }
        addLabel("Background color")
        val colorSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@HomeActivity, android.R.layout.simple_spinner_item,
                colorOptions.map { it.first }).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(colorOptions.indexOfFirst { it.second == initial.backgroundColor }.coerceAtLeast(0))
        }
        content.addView(colorSpinner, LinearLayout.LayoutParams(-1, dp(44)))

        val alphaLabel = addLabel("Background opacity: ${initial.backgroundAlpha}%")
        val alphaSeek = SeekBar(this).apply {
            max = 100
            progress = initial.backgroundAlpha.coerceIn(0, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    alphaLabel.text = "Background opacity: $progress%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        content.addView(alphaSeek, LinearLayout.LayoutParams(-1, dp(34)))

        val borderColorOptions = mutableListOf(
            "Pale blue" to "#FFBAD8E4",
            "White" to "#FFFFFFFF",
            "Black" to "#FF000000",
            "Charcoal" to "#FF18282D",
            "Plum" to "#FF42243D",
        )
        if (borderColorOptions.none { it.second == initial.borderColor }) {
            borderColorOptions.add("Current color" to initial.borderColor)
        }
        addLabel("Border color")
        val borderColorSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@HomeActivity, android.R.layout.simple_spinner_item,
                borderColorOptions.map { it.first }).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(borderColorOptions.indexOfFirst { it.second == initial.borderColor }.coerceAtLeast(0))
        }
        content.addView(borderColorSpinner, LinearLayout.LayoutParams(-1, dp(44)))

        val borderAlphaLabel = addLabel("Border opacity: ${initial.borderAlpha}%")
        val borderAlphaSeek = SeekBar(this).apply {
            max = 100
            progress = initial.borderAlpha.coerceIn(0, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    borderAlphaLabel.text = "Border opacity: $progress%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        content.addView(borderAlphaSeek, LinearLayout.LayoutParams(-1, dp(34)))

        val borderWidthLabel = addLabel("Border width: ${initial.borderWidthDp} dp")
        val borderWidthSeek = SeekBar(this).apply {
            max = 8
            progress = initial.borderWidthDp.coerceIn(0, 8)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    borderWidthLabel.text = "Border width: $progress dp"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        content.addView(borderWidthSeek, LinearLayout.LayoutParams(-1, dp(34)))


        val dialog = AlertDialog.Builder(this)
            .setTitle("Dock settings")
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton("Apply", null)
            .setPositiveButton("Save", null)
            .create()
        dockConfigurationDialog = dialog
        dialog.setOnDismissListener { if (dockConfigurationDialog === dialog) dockConfigurationDialog = null }
        dialog.setOnShowListener {
            dialog.window?.let { window ->
                window.setGravity(Gravity.CENTER)
                val visible = Rect()
                window.decorView.getWindowVisibleDisplayFrame(visible)
                val maxContentHeight = minOf(dp(420), (visible.height() - dp(152)).coerceAtLeast(dp(64)))
                val contentWidth = (resources.displayMetrics.widthPixels - dp(64)).coerceAtLeast(dp(240))
                content.measure(
                    View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(maxContentHeight, View.MeasureSpec.AT_MOST),
                )
                scroll.layoutParams = scroll.layoutParams.apply {
                    height = content.measuredHeight.coerceAtMost(maxContentHeight)
                }
                window.setLayout(minOf(resources.displayMetrics.widthPixels - dp(32), dp(420)), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val settingsButtons = listOf(
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL),
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            )
            fun bindSettingsButton(buttonId: Int, dismissAfterApply: Boolean) {
                dialog.getButton(buttonId).setOnClickListener { button ->
                    settingsButtons.forEach { it.isEnabled = false }
                    val newSettings = Programs.DockSettings(
                        heightDp = 56 + heightSeek.progress,
                        backgroundAlpha = alphaSeek.progress,
                        backgroundColor = colorOptions[colorSpinner.selectedItemPosition].second,
                        borderAlpha = borderAlphaSeek.progress,
                        borderWidthDp = borderWidthSeek.progress,
                        borderColor = borderColorOptions[borderColorSpinner.selectedItemPosition].second,
                    )
                    val expected = expectedConfig
                    val version = generation
                    selectionWorker.execute {
                        val result = runCatching {
                            Programs(applicationContext).saveDockSettings(expected, newSettings)
                        }
                        runOnUiThread {
                            if (!started || version != generation) return@runOnUiThread
                            result.onSuccess { saved ->
                                expectedConfig = saved.configXml ?: expected
                                dockDetails = saved
                                applyDockHeight(saved.settings.heightDp)
                                dock?.refresh()
                                if (dismissAfterApply) dialog.dismiss()
                                else settingsButtons.forEach { it.isEnabled = true }
                            }.onFailure { error ->
                                settingsButtons.forEach { it.isEnabled = true }
                                Toast.makeText(this, error.message ?: "Could not save dock settings", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
            bindSettingsButton(AlertDialog.BUTTON_NEUTRAL, dismissAfterApply = false)
            bindSettingsButton(AlertDialog.BUTTON_POSITIVE, dismissAfterApply = true)
        }
        dialog.show()
    }

    private var dockConfigurationDialog: AlertDialog? = null

    private fun runDockAction(action: String, uri: android.net.Uri) {
        if (!started) return
        val version = generation
        selectionWorker.execute {
            val result = runCatching {
                val programs = Programs(applicationContext)
                when (action) {
                    DOCK_REMOVE_ACTION -> {
                        programs.removeDockEntry(uri)
                        true
                    }
                    DOCK_MOVE_EARLIER_ACTION -> programs.moveDockEntry(uri, -1)
                    DOCK_MOVE_LATER_ACTION -> programs.moveDockEntry(uri, 1)
                    else -> false
                }
            }
            runOnUiThread {
                if (!started || version != generation) return@runOnUiThread
                result.onSuccess { changed ->
                    if (changed) dock?.refresh()
                }.onFailure {
                    Toast.makeText(this, it.message ?: getString(R.string.open_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openDesktopEntry(filename: String?) {
        val selected = desktop ?: return
        val entry = filename?.let { name -> selected.files.firstOrNull { it.filename == name } }
        if (entry != null && launchDesktopTarget(entry)) return
        resolveAndOpen { it.openDesktopEntry(selected.name, filename) }
    }

    /** App links and pinned Android shortcuts start from Home without opening Folder's UI. */
    private fun launchDesktopTarget(entry: Programs.DesktopEntry): Boolean {
        val packageName = entry.packageName ?: return false
        return try {
            val shortcutId = entry.shortcutId
            if (shortcutId != null) {
                val launcherApps = getSystemService(LauncherApps::class.java)
                check(launcherApps.hasShortcutHostPermission()) { getString(R.string.pin_home_required) }
                launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
            } else {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    ?: error(getString(R.string.open_failed))
                startActivity(launchIntent)
            }
            true
        } catch (error: Exception) {
            Toast.makeText(this, error.message ?: getString(R.string.open_failed), Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun transferDesktopEntry(entry: Programs.DesktopEntry, destination: String? = null, move: Boolean) {
        val selected = desktop?.name ?: return
        val changed = desktopChangedReceiver()
        resolveAndOpen { it.transferDesktopEntry(selected, entry.filename, destination, move, changed) }
    }

    private fun desktopChangedReceiver(): IBinder = homeCallbacks

    private fun registerDragHost(callback: IBinder?) {
        worker.execute {
            runCatching { Programs(applicationContext).registerDragHost(dragHostLifetime, callback) }
        }
    }

    private fun receiveFolderDrag(action: Int, data: Bundle) {
        if (action == DRAG_CANCEL) {
            clearDockDropPreview()
            folderDragPreview.close()
            workspace.showDropGrid(false)
            return
        }
        val point = PointF(data.getFloat("x"), data.getFloat("y"))
        val source = when (data.getString("surface")) {
            "programs" -> browser
            "dock" -> dock
            "floating" -> null
            else -> return
        }
        if (source != null) point.offsetFrom(source)
        else if (data.getString("surface") != "floating") return
        when (action) {
            DRAG_BEGIN, DRAG_MOVE -> {
                if (editor != null) {
                    clearDockDropPreview()
                    return
                }
                val isLink = data.getBoolean("isLink", true)
                if (action == DRAG_BEGIN) {
                    val icon = if (Build.VERSION.SDK_INT >= 33) data.getParcelable("icon", Bitmap::class.java)
                        else @Suppress("DEPRECATION") data.getParcelable<Bitmap>("icon")
                    folderDragPreview.open(workspace, icon, point, isLink)
                }
                val linkPreview = !isLink && browserPoint(point, dock, "dock") != null
                folderDragPreview.move(point, linkPreview)
                requestDockDropPreview(point, dragging = true)
                showFolderDropGrid(point)
            }
            DRAG_DROP -> {
                clearDockDropPreview()
                folderDragPreview.close()
                workspace.showDropGrid(false)
                data.getString("drag")?.let { routeDrop(point, token = it) }
            }
        }
    }

    private fun requestDockDropPreview(screen: PointF, dragging: Boolean) {
        val dockPoint = if (dragging) browserPoint(screen, dock, "dock") else null
        if (dockPoint == null && !dockDropPreviewActive && pendingDockDropPreview?.dock == null) return
        pendingDockDropPreview = DockDropPreviewRequest(PointF(screen.x, screen.y), dockPoint)
        dockDropPreviewHandler.removeCallbacks(dispatchDockDropPreview)
        if (dockPoint == null) {
            dockDropPreviewScheduled = false
            dispatchDockDropPreview.run()
        } else if (!dockDropPreviewScheduled) {
            dockDropPreviewScheduled = true
            dockDropPreviewHandler.postDelayed(dispatchDockDropPreview, 24L)
        }
    }

    private fun clearDockDropPreview() {
        pendingDockDropPreview = DockDropPreviewRequest(PointF(0f, 0f), null)
        dockDropPreviewHandler.removeCallbacks(dispatchDockDropPreview)
        dockDropPreviewScheduled = false
        dispatchDockDropPreview.run()
    }

    private fun handleDesktopDrop(view: View, event: DragEvent): Boolean = when (event.action) {
        DragEvent.ACTION_DRAG_STARTED ->
            event.clipDescription?.hasMimeType(DESKTOP_TRANSFER_MIME) == true && editor == null
        DragEvent.ACTION_DRAG_ENTERED, DragEvent.ACTION_DRAG_LOCATION -> {
            showFolderDropGrid(PointF(event.x, event.y).offsetFrom(view))
            true
        }
        DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
            workspace.showDropGrid(false)
            true
        }
        DragEvent.ACTION_DROP -> {
            workspace.showDropGrid(false)
            val token = event.clipData?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.text?.toString()
            token != null && routeDrop(PointF(event.x, event.y).offsetFrom(view), token = token)
        }
        else -> false
    }

    private fun PointF.offsetFrom(view: View): PointF = apply {
        val origin = IntArray(2)
        view.getLocationOnScreen(origin)
        offset(origin[0].toFloat(), origin[1].toFloat())
    }

    private fun PointF.relativeTo(view: View): PointF {
        val origin = IntArray(2)
        view.getLocationOnScreen(origin)
        return PointF(x - origin[0], y - origin[1])
    }

    private fun showFolderDropGrid(screen: PointF) {
        val point = screen.relativeTo(workspace)
        val cell = workspace.cellAt(point.x, point.y)
        workspace.showDropGrid(cell != null, cell?.takeIf { workspace.canPlace(it) })
    }

    private fun routeDrop(screen: PointF, entry: String? = null, token: String? = null): Boolean {
        if (!started || !layoutLoaded || loadingWorkspace || saving || editor != null) return false
        val selected = desktop ?: return false
        val folder = browserPoint(screen, browser, "programs") ?: browserPoint(screen, dock, "dock")
        val point = screen.relativeTo(workspace)
        val cell = workspace.cellAt(point.x, point.y)
        val target = selected.files.firstOrNull { it.folder && it.filename != entry &&
            cell != null && layout.launchers[it.filename] == cell }
        val free = cell?.takeIf { workspace.canPlace(it, entry?.let { name -> WorkspaceItem.Launcher(name) }) }
        val occupied = cell != null && free == null && target == null && folder == null
        val expected = savedLayout
        val snapshot = layout
        val changed = desktopChangedReceiver()
        resolveAndOpen {
            it.routeDrop(selected.name, entry, token, screen, folder, target?.filename, occupied,
                expected, snapshot, free, changed, createDockShortcutForNonLinks = true)
        }
        return true
    }

    private fun browserPoint(screen: PointF, view: View?, surface: String): Programs.BrowserPoint? {
        if (view?.isShown != true) return null
        val point = screen.relativeTo(view)
        if (point.x < 0 || point.y < 0 || point.x >= view.width || point.y >= view.height) return null
        return Programs.BrowserPoint(surface, point)
    }

    private fun removeDesktopEntry(workspace: String, entry: Programs.DesktopEntry) {
        if (!started) return
        val version = generation
        worker.execute {
            val result = runCatching { Programs(applicationContext).removeDesktopEntry(workspace, entry.filename) }
            runOnUiThread {
                if (!started || version != generation) return@runOnUiThread
                result.onSuccess {
                    refreshDesktop()
                    Toast.makeText(this, R.string.desktop_item_removed, Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(this, getString(R.string.desktop_remove_failed, it.message.orEmpty()), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun dropDesktopItem(source: Programs.DesktopEntry, x: Float, y: Float): Boolean =
        routeDrop(PointF(x, y).offsetFrom(workspace), entry = source.filename)

    private fun showDesktopItemMenu(entry: Programs.DesktopEntry) {
        if (editor != null || loadingWorkspace || saving) return
        val selected = desktop?.name ?: return
        val actions = listOf<Pair<Int, () -> Unit>>(
            R.string.open_desktop_item to { openDesktopEntry(entry.filename) },
            R.string.move_on_desktop to { beginEditing(WorkspaceItem.Launcher(entry.filename), entry.label) },
            R.string.move_desktop_item_to to { transferDesktopEntry(entry, move = true) },
            R.string.copy_desktop_item_to to { transferDesktopEntry(entry, move = false) },
            R.string.remove_desktop_item to { removeDesktopEntry(selected, entry) },
        )
        AlertDialog.Builder(this).setTitle(entry.label)
            .setItems(actions.map { getString(it.first) }.toTypedArray()) { _, index -> actions[index].second() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun resolveAndOpen(resolve: (Programs) -> Programs.OpenTarget) {
        if (!started) return
        val version = generation
        selectionWorker.execute {
            val result = runCatching { resolve(Programs(applicationContext)) }
            runOnUiThread {
                if (!started || version != generation) return@runOnUiThread
                result.mapCatching { openFromHome(it) }.onFailure {
                    Toast.makeText(this, it.message ?: getString(R.string.open_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openFromHome(target: Programs.OpenTarget) {
        when (target) {
            Programs.OpenTarget.Ignored -> Unit
            Programs.OpenTarget.DesktopChanged -> {
                refreshDesktop()
                dock?.refresh()
            }
            is Programs.OpenTarget.App -> {
                val intent = packageManager.getLaunchIntentForPackage(target.packageName)
                    ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", target.packageName, null))
                startActivity(intent)
            }
            is Programs.OpenTarget.Pin -> {
                val launcherApps = getSystemService(LauncherApps::class.java)
                check(launcherApps.hasShortcutHostPermission()) { getString(R.string.pin_home_required) }
                launcherApps.startShortcut(target.packageName, target.id, null, null, Process.myUserHandle())
            }
            is Programs.OpenTarget.Launch -> {
                val options = ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT >= 36) {
                    options.pendingIntentBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                } else if (Build.VERSION.SDK_INT >= 34) {
                    @Suppress("DEPRECATION")
                    options.pendingIntentBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
                startIntentSenderForResult(target.pending.intentSender, -1, null, 0, 0, 0, options.toBundle())
            }
        }
    }

    private fun showSlotMessage(slot: FrameLayout, message: String, click: (() -> Unit)? = null) {
        slot.removeAllViews()
        slot.addView(TextView(this@HomeActivity).apply {
            text = message
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = panel(0x3318282D)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            if (click != null) {
                setOnClickListener { click() }
                tooltipText = getString(R.string.retry)
            }
        }, FrameLayout.LayoutParams(-1, -1))
    }

    private fun showHomeOptions() {
        if (editor != null || loadingWorkspace) return
        val actions = buildList<Pair<Int, () -> Unit>> {
            add(R.string.workspaces to ::showWorkspaces)
            if (desktop != null) {
                add(R.string.browse_workspace to { openDesktopEntry(null) })
                add(R.string.new_desktop_folder to ::showNewDesktopFolder)
            }
            add(R.string.add_widget to ::chooseApplicationWidget)
            add(R.string.workspace_grid to ::showGridSize)
            add(R.string.home_settings to ::openHomeSettings)
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(R.string.enable_install_notices to {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                })
            }
            add(R.string.retry to ::retry)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.home_options)
            .setItems(actions.map { getString(it.first) }.toTypedArray()) { _, index -> actions[index].second() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showNewDesktopFolder() {
        val selected = desktop?.name ?: return
        promptName(R.string.new_desktop_folder, R.string.desktop_folder_name) { name ->
            val version = generation
            worker.execute {
                val result = runCatching { Programs(applicationContext).createDesktopFolder(selected, name) }
                runOnUiThread {
                    if (!started || version != generation) return@runOnUiThread
                    result.onSuccess { loadWorkspace() }.onFailure {
                        Toast.makeText(this, it.message ?: getString(R.string.desktop_folder_unavailable), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun promptName(title: Int, hint: Int, accept: (String) -> Unit) {
        val input = EditText(this).apply {
            setHint(hint)
            isSingleLine = true
        }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty() || name.startsWith('.') || '/' in name || name.any { it.isISOControl() } || name.length > 120) {
                    input.error = getString(R.string.invalid_desktop_name)
                } else {
                    dialog.dismiss()
                    accept(name)
                }
            }
        }
        dialog.show()
    }

    private fun showWorkspaces() {
        val current = desktop ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.workspaces)
            .setSingleChoiceItems(current.workspaces.toTypedArray(), current.workspaces.indexOf(current.name)) { dialog, index ->
                dialog.dismiss()
                if (current.workspaces[index] != current.name) loadWorkspace(current.workspaces[index])
            }
            .setNeutralButton(R.string.new_workspace) { _, _ ->
                promptName(R.string.new_workspace, R.string.workspace_name) { name ->
                    loadWorkspace(name, create = true)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showGridSize() {
        if (!layoutLoaded || saving || workspace.width <= 0 || workspace.height <= 0) {
            Toast.makeText(this, R.string.layout_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val columns = NumberPicker(this).apply {
            minValue = WorkspaceGrid.MIN_COLUMNS
            maxValue = WorkspaceGrid.MAX_COLUMNS
            value = layout.grid.columns
            wrapSelectorWheel = false
            descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            contentDescription = getString(R.string.workspace_grid)
        }
        val form = FrameLayout(this).apply {
            setPadding(dp(18), dp(8), dp(18), 0)
            addView(columns, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.workspace_grid)
            .setView(form)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save_layout) { _, _ ->
                if (columns.value != layout.grid.columns) {
                    saveWorkspace(projectedLayout(columns.value))
                }
            }
            .show()
    }

    private fun beginEditing(item: WorkspaceItem, label: String): Boolean {
        if (!started || !layoutLoaded || loadingWorkspace || saving || editor != null || item !in displayedLayout.placements()) return false
        val overlay = WorkspaceEditor(this, displayedLayout, item, label)
        overlay.setLabels(buildMap {
            desktop?.files.orEmpty().forEach { put(WorkspaceItem.Launcher(it.filename), it.label) }
        })
        overlay.onDraftChanged = { if (editor === overlay) refreshEditorControls() }
        editor = overlay
        workspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        workspace.post {
            if (editor !== overlay) return@post
            val directory = browser?.location()
            val open = Runnable { showEditor(overlay) }
            var captures = if (directory == null) 1 else 2
            fun complete() {
                if (--captures == 0) {
                    workspace.removeCallbacks(open)
                    showEditor(overlay)
                }
            }
            // Capture while the live widget views and the Folder surface are still attached.
            workspace.postDelayed(open, 450L)
            workspace.capturePreviews(
                window, WorkspaceItem.Programs.takeIf { browser != null },
                onPreview = { key, image ->
                    if (editor === overlay) overlay.setPreview(key, image) else image.recycle()
                },
                onComplete = ::complete,
            )
            if (directory != null) previewWorker.execute {
                val preview = runCatching { Programs(applicationContext).workspacePreview(directory) }.getOrNull()
                runOnUiThread {
                    if (preview != null) {
                        if (editor === overlay) overlay.setPreview(WorkspaceItem.Programs, preview) else preview.recycle()
                    }
                    complete()
                }
            }
        }
        return true
    }

    private fun showEditor(overlay: WorkspaceEditor) {
        if (editor !== overlay || overlay.parent != null) return
        workspace.setEditing(true)
        workspace.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        dockSlot.visibility = View.INVISIBLE
        editControls.visibility = View.VISIBLE
        refreshEditorControls()
    }

    private fun finishEditing(remove: Boolean) {
        val draft = editor ?: return
        if (saving) return
        if (remove) {
            if (draft.item !is WorkspaceItem.Launcher) draft.removeSelected()
            refreshEditorControls()
            return
        }
        val updated = draft.draftLayout()
        if (!draft.isValid) {
            Toast.makeText(this, R.string.desktop_layout_invalid, Toast.LENGTH_SHORT).show()
            refreshEditorControls()
            return
        }
        if (updated == layout) {
            if (draft.originalItem == WorkspaceItem.Programs) openFromNotice = false
            closeEditor()
            applyWorkspace()
        } else {
            saveWorkspace(updated, editingDraft = draft)
        }
    }

    private fun closeEditor() {
        val overlay = editor ?: return
        editor = null
        workspace.removeView(overlay)
        workspace.setEditing(false)
        editControls.visibility = View.GONE
        dockSlot.visibility = View.VISIBLE
        if (desktopRefreshPending) loadWorkspace()
    }

    private fun showEditorSaving(draft: WorkspaceEditor, active: Boolean) {
        draft.isEnabled = !active
        cancelEditButton.isEnabled = !active
        refreshEditorControls()
        saveButton.setText(if (active) R.string.saving_layout else R.string.save_layout)
    }

    private fun refreshEditorControls() {
        val draft = editor ?: return
        val canInteract = !saving
        val count = draft.itemCount
        editPreviousButton.isEnabled = canInteract && count > 1
        editNextButton.isEnabled = canInteract && count > 1
        cancelEditButton.isEnabled = canInteract
        removeButton.visibility = if (draft.item == null || draft.item is WorkspaceItem.Launcher) View.GONE else View.VISIBLE
        removeButton.isEnabled = canInteract && draft.item != null
        val selected = if (count == 0) draft.selectedLabel
            else getString(R.string.desktop_selection, draft.selectedLabel, draft.selectedIndex, count)
        val state = getString(if (draft.isValid) R.string.desktop_layout_valid else R.string.desktop_layout_invalid)
        editStatus.text = "$selected\n$state"
        saveButton.isEnabled = canInteract && draft.isValid
        saveButton.setText(if (saving) R.string.saving_layout else R.string.save_layout)
    }

    private fun chooseApplicationWidget() {
        if (!layoutLoaded || saving) {
            Toast.makeText(this, R.string.layout_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val builtIns = buildList {
            if (layout.programs == null) add(ApplicationWidgets.BuiltIn(getString(R.string.programs)) {
                val position = workspace.findSpace(1, 1, minSpan = 2)
                if (position != null) saveWorkspace(layout.copy(programs = position))
                else Toast.makeText(this@HomeActivity, R.string.widget_no_space, Toast.LENGTH_SHORT).show()
            })
            if (layout.clockPlacement == null) add(ApplicationWidgets.BuiltIn(getString(R.string.clock_widget)) {
                val desired = WorkspacePlacement(layout.grid.columns - 3, 0, 3, 1)
                val position = desired.takeIf { workspace.canPlace(it) } ?: workspace.findSpace(1, 1)
                if (position != null) saveWorkspace(layout.copy(clockPlacement = position))
                else Toast.makeText(this@HomeActivity, R.string.widget_no_space, Toast.LENGTH_SHORT).show()
            })
        }
        applicationWidgets.pick(builtIns)
    }

    private fun addHostedWidget(widget: HostedWidget) {
        if (!layoutLoaded) {
            pendingHostedWidget = widget
            return
        }
        if (layout.hosted.size >= WorkspaceLayout.MAX_HOSTED || !workspace.canPlace(widget.placement)) {
            applicationWidgets.release(widget.binding.id)
            Toast.makeText(this, R.string.widget_no_space, Toast.LENGTH_SHORT).show()
            return
        }
        saveWorkspace(layout.copy(hosted = layout.hosted + widget), newWidgetId = widget.binding.id)
    }

    private fun saveClockBinding(binding: WidgetBinding) {
        if (layout.clockPlacement != null) saveWorkspace(layout.copy(clock = binding), binding.id)
        else clock.release(binding.id)
    }

    private fun saveWorkspace(
        update: WorkspaceLayout,
        newClockId: Int? = null,
        newWidgetId: Int? = null,
        editingDraft: WorkspaceEditor? = null,
    ) {
        val selected = desktop?.name
        if (!layoutLoaded || selected == null || saving || loadingWorkspace) {
            newClockId?.let(clock::release)
            newWidgetId?.let(applicationWidgets::release)
            Toast.makeText(this, R.string.layout_unavailable, Toast.LENGTH_SHORT).show()
            workspace.setWorkspace(displayedLayout)
            return
        }
        if (!update.isValid()) {
            newClockId?.let(clock::release)
            newWidgetId?.let(applicationWidgets::release)
            Toast.makeText(this, R.string.desktop_layout_invalid, Toast.LENGTH_SHORT).show()
            if (editingDraft != null) refreshEditorControls()
            return
        }
        if (update == layout) return
        val before = savedLayout
        val raw = update.encode()
        saving = true
        editingDraft?.let { showEditorSaving(it, true) }
        worker.execute {
            val result = runCatching { Programs(applicationContext).saveWorkspace(selected, before, raw) }
            runOnUiThread {
                saving = false
                editingDraft?.let { showEditorSaving(it, false) }
                result.onSuccess {
                    val oldClockId = layout.clock?.id
                    if (oldClockId != null && oldClockId != update.clock?.id) clock.release(oldClockId)
                    val removed = layout.hosted.map { it.binding.id }.toSet() - update.hosted.map { it.binding.id }.toSet()
                    removed.forEach(applicationWidgets::release)
                    savedLayout = raw
                    storedLayout = update
                    layout = projectedLayout()
                    if (editingDraft != null) {
                        if (editingDraft.originalItem == WorkspaceItem.Programs) openFromNotice = false
                        if (editor === editingDraft) closeEditor()
                    }
                }.onFailure {
                    newClockId?.let(clock::release)
                    newWidgetId?.let(applicationWidgets::release)
                    Toast.makeText(this, it.message ?: getString(R.string.layout_unavailable), Toast.LENGTH_LONG).show()
                }
                if (!isDestroyed && editor == null) {
                    if (started) {
                        applyWorkspace()
                        if (desktopRefreshPending) loadWorkspace()
                    } else workspace.setWorkspace(layout)
                }
            }
        }
    }

    private fun retry() {
        loadWorkspace()
        if (displayedLayout.programs != null) openPrograms(requestedCategory)
        if (dock == null) openDock() else dock?.refresh()
        launcher.requestSync()
    }

    private fun showStatus(message: String) {
        status.text = message
        status.visibility = View.VISIBLE
        status.setOnClickListener { retry() }
    }

    private fun openHomeSettings() {
        val roles = getSystemService(RoleManager::class.java)
        try {
            if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 2)
            } else startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun panel(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(10).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val WORKSPACE_EDIT_ACTION = "workspace_edit"
        private const val DOCK_CONFIGURATION_ACTION = "dock_configure"
        private const val DOCK_REMOVE_ACTION = "remove_dock_entry"
        private const val DOCK_MOVE_EARLIER_ACTION = "dock_move_earlier"
        private const val DOCK_MOVE_LATER_ACTION = "dock_move_later"
        private const val DRAG_BEGIN = 1
        private const val DRAG_MOVE = 2
        private const val DRAG_DROP = 3
        private const val DRAG_CANCEL = 4
        private const val DESKTOP_TRANSFER_MIME = "application/vnd.infinifolder.desktop-transfer"
        private const val CALLBACK_DESCRIPTOR = "com.infiniforge.launch.HomeCallbacks"
        private const val CALLBACK_VERSION = 1
        private const val CALLBACK_DRAG = IBinder.FIRST_CALL_TRANSACTION
        private const val CALLBACK_REFRESH = IBinder.FIRST_CALL_TRANSACTION + 1
        const val ACTION_OPEN_PINNED_SHORTCUT = "com.infinilaunch.action.OPEN_PINNED_SHORTCUT"
    }
}
