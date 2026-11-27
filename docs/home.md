# Home, workspaces, widgets, and wallpaper

## Desktop workspaces

Each visible directory under `Internal storage/infini/launch/desktop/` is a
workspace. **Workspaces** in Home options opens the list and can create
another directory. Swipe left or right on open desktop space to cycle
through the list. The first and last workspace wrap around. Switches have
an immediate presentation hook for future InfiniFX effects.

Each workspace uses `.desktop.json` for grid settings, item bounds, margins,
layer order, Android widget IDs, and saved file-launcher placements. File launchers start in
one-cell slots. Home imports the previous widget layout from `infini/launch/workspaces/layout.json` when it first needs
its settings. Files, folders, and shortcuts directly inside a workspace
start in free one-cell positions. Folder resolves each tile's icon and label
with the same resource icon resolver used in its file browser. Icons and labels
scale to the cell. A loaded icon stays visible while Home checks it for updates.
Hold an item to show the grid, then
move your finger to drag the icon. Dropping it in a free cell snaps the
icon to that position. Existing links move between the Programs tree, dock,
and desktop when dragged through Home, including drops onto desktop folders or
embedded Folder widgets. Dropping an ordinary file or folder onto the dock
creates a dock `.iflink` and leaves the original where it is. Discovery counts app links anywhere
in the workspace tree, so moving one out of Programs does not create a
replacement in its old category. Dragging an item from ordinary storage onto
the desktop creates an `.iflink` there and leaves the source in place.
Dragging a desktop item to another desktop cell repositions it. Drops to other Folder destinations keep Folder's transfer
workflow. Hold and release a desktop item to open its menu, which includes
**Move to…**, **Copy to…**, and **Remove**.
**Remove** moves the item to `infini/launch/removed-desktop/<workspace>/`.
Open that directory in InfiniFolder and move the item back to restore it.
Removing a shortcut moves the link itself.

Tapping a desktop file launches its selected app directly with read access to
that file. Supported InfiniPlay game formats open in Play; books and other
registered companion formats open in their viewer. Folder-owned viewers open
the item directly without first showing the file browser. Folder shortcuts to
directories still open the floating file manager.

Long-press empty desktop space for Home options. **Browse** opens the
current workspace in InfiniFolder. Tapping a desktop folder or directory
shortcut opens InfiniFolder's floating browser. InfiniFolder uses its Android
display-over-other-apps permission for this window. **New folder** creates a
real directory and shows it on the workspace. Home rereads the directory when you return.
InfiniFolder lists desktop workspaces and their direct folders in its destination picker's
**Common locations**. Each workspace supports up to 256 desktop items.

## Current workspace

Home is transparent over the system wallpaper. The default workspace shows
the **Programs** widget at the lower right and the **Clock** at the top right
of the workspace grid. The grid fills the available widget area inside
Android's visible system bars, above the dock. Long-press anywhere on a
widget and release to enter desktop reorganization, which shows frozen previews
of every widget and launcher. Tap an item or use the previous/next controls to
select it, then drag its preview to move it. Dragging moves the item above
overlapping items. **Raise** and **Lower** change the selected item's layer by
one step. Widget edge handles resize horizontally or vertically. Light dashed
lines mark half-cell positions, and dragging snaps item positions to those
half-cell marks. **Margins** opens horizontal and vertical sliders
for the selected item, including launchers. They range from −20 to 20 dp and
update the editor preview live.
Zero is flush, positive values inset the item, and negative values extend it.
Margins default to zero; press **Save** to apply changes. InfiniLaunch also removes Android's
default widget-host padding, so widgets can reach their slot edges at zero
margin. Overlapping items are saved with their stacking order: the top item
draws above the others and receives touch interaction. Use the previous/next
controls to select items, including a fully covered item. Out-of-grid placements
remain highlighted. **Save** commits the complete
arrangement through InfiniFolder in one step. The
editor keeps the draft visible during the save and allows a retry if saving
fails. **Remove widget** removes the selected widget from the draft, and **Save**
commits that removal with the other changes. **Cancel** or Back discards the
entire draft. The dock keeps its separate configuration.

Long-press empty wallpaper for **Home options**. **Lock layout** prevents
InfiniLaunch from entering edit mode, changing the grid, adding widgets, or
dragging desktop items. The setting applies across workspaces. A locked item's
long-press asks before unlocking and entering edit mode. Normal taps and
launches continue to work. **Grid columns** lets you
choose 3 through 12 columns, with six by default. InfiniLaunch uses square
slots only to count rows: columns times available height divided by available
width, rounded down. For example, an area 2.3 times as high as it is wide gets
13 rows with six columns. After counting, slots stretch across the full
workspace width and height, sharing their edges with the editor grid. A small
area can compact widgets and use extra rows to keep every widget available.
Saved layouts are projected into the current grid as the window size changes.
A placement edit saves the current grid as its reference.

The top-right **Clock** slot uses Android's widget host for a compatible system
Clock widget. If Android requires binding consent, the slot displays the
system time. Tap the time to request the widget. Android owns the binding and
the shared workspace records its ID and provider.

The bottom dock is a separate embedded InfiniFolder view of
`Internal storage/infini/launch/dock`. New docks get up to five app links:
Phone, Browser, Email, Messages, and Camera where available. Settings and
InfiniFolder can fill missing roles. Link filenames are numbered in dock order;
the visible label and icon come from each link's metadata. Long-press a dock
item to open dock settings. The dialog controls dock height, background color and opacity, plus border color, opacity and width. **Apply** saves and refreshes the dock while leaving settings open;
**Save** applies and closes it. The dialog shows how many square icons fit at
the current height before horizontal scrolling. Capacity is calculated from
the available width and updates as the height or screen size changes. It also lets you move the selected
item earlier or later, or remove it. Removal moves it to
`infini/launch/removed-dock/` for recovery. Dragging between the dock
and a desktop workspace always moves the existing item without a copy/move
prompt. Dropping a link onto the dock moves that link and inserts it at the
drop position, animating the other items to make room. Dropping an ordinary
file or folder creates a dock link at that position and leaves the source in
place. While a non-link is over the dock, its drag preview shows a LINK badge;
the badge clears when it leaves the dock. InfiniFolder's normal Pin board
remains a separate interface with the same animated insertion feedback. About
four or five slots fit at normal phone widths, wider views fit more, and extra
items scroll left to right.
Desktop app links and pinned Android shortcuts launch directly from Home.
Other dock entries are resolved by Folder and passed to the visible Home
activity to open them. Drag and transfer completion callbacks use the documented
`com.infiniforge.launch.HomeCallbacks` Binder protocol, independent of either
app's class loader.

### Pinned shortcuts

When InfiniLaunch is the default Home, a valid Android shortcut pin request
uses the publisher's icon and label and places a real `.iflink` in the
current workspace. The pin handler runs without a window or a second prompt. A tap opens the publisher's
actual shortcut, including its deep link or action. Opening a pinned shortcut
link from InfiniFolder returns to InfiniLaunch, which checks that the matching
link still exists under the desktop or dock before invoking Android's shortcut
host API. Use **Remove** in the shortcut’s desktop menu to move the link out of
the workspace. Earlier dock shortcut files can be opened from the dock.
[Android pin request contract](https://developer.android.com/reference/android/content/pm/LauncherApps.PinItemRequest)

### Embedded backgrounds

The embedded Programs view uses a 20% tint over the wallpaper by default and a
pale-blue 1 dp border at 50% opacity. The dock defaults to a Plum tint at 30%
opacity, 100 dp height, and the same 50% border. Long-press the dock to change
its appearance. InfiniFolder's **Floating and embedded defaults** setting
changes the app-wide window appearance and sort defaults; a directory's
`.folder.xml` overrides only the properties it defines.
Android 12 and later blur the wallpaper within those panes when compositor
blur is available. On other devices or when Android disables blur, they use
the same tint over the unblurred wallpaper.
The dock's `.folder.xml` stores its appearance and layout. `dockHeightDp`
controls the dock band from 56 to 200 dp and defaults to 100. `backgroundColor`
accepts Android color values (omit it to use the wallpaper theme), and
`backgroundAlpha` ranges from `0` for a clear tint to `1` for opaque. The file
also supports `borderColor`, `borderAlpha`, and `borderWidthDp` (0 through 8 dp).
`visibleIcons="auto"`
calculates square icon capacity from the available width
and dock height; additional entries scroll horizontally. Text, icons, and touch targets
remain fully visible.

```xml
<folder version="1" layout="horizontal" visibleIcons="auto" dockHeightDp="100" backgroundColor="#FF42243D" backgroundAlpha="0.30" borderColor="#FFBAD8E4" borderAlpha="0.50" borderWidthDp="1" animateMoves="true"/>
```

For the regular embedded browser, use `layout="vertical"` or omit the
layout attribute. Folder reloads the file when the embedded directory refreshes.

## Further workspace features

The ordinary app icon and Android's Home entry point open the same workspace
experience. Offer the Home role through Android's consent flow and give the
user a way to return to Android's Home settings. Programs opens the embedded
InfiniFolder browser at the shared Programs root. The widget lists items
alphabetically by their visible names, including app shortcut labels.
Resource links use Folder's
public opening workflow. Android's shortcut APIs handle Android-provided
shortcuts when the launcher has host permission.

Future work includes user-defined workspace ordering and placing additional
item types on their grids. The host stores item identities, bounds, and
references in each workspace's settings. Store a widget binding ID with its
provider identity. Store a browser panel's configured root as an authoritative
Folder URI. Store pinned links by durable reference. For a pinned Programs app,
use its package target to look up its current entry in Programs after a move.
Empty layout state can show Programs as a starting action once Folder is
available.

A missing link, revoked widget binding, or inaccessible folder leaves an
item the user can repair or remove. A declined or cancelled operation keeps
the previous layout. Existing layout records survive loss of the default Home
role or temporary Folder access. Persist layout changes only when the shared
file can be updated successfully.

Display Android's current system wallpaper behind the launcher workspace.
Home's wallpaper controls can open Android's standard wallpaper settings or
preview. Any file that InfiniLaunch selects for wallpaper comes through
InfiniFolder.

## Third-party Android widgets

From **Home options**, choose **Add widget**. The chooser groups widgets by
app and shows each app's icon. Open an app to see its widget previews or
icons. InfiniLaunch finds a free position and opens Android's binding or
provider configuration prompt when required. Built-in Programs and Clock
appear under InfiniLaunch when removed. Long-press the widget itself to open
the grid editor. The Clock and added app widgets use Android's
`AppWidgetHost`. The grid, bindings, and placements are saved in the shared
workspace file.
Release allocated IDs if placement is cancelled or cannot be saved. When
removing a placed widget, commit the layout change before releasing its ID.
Reconcile stale and unreferenced host IDs against a successfully read layout;
an unreadable layout is not evidence that widget IDs are unused. Forward
size changes to the provider and tie host listening to the Home lifecycle.

Android owns the actual bindings. If a layout is copied to another device or
a binding becomes invalid, show the widget's saved position with an action to
bind it again. Handle Android shortcuts separately from widget placement.

## Interactive File Browser item

The **Programs** widget is the built-in File Browser item. The widget chooser
can restore it. Choosing other roots through InfiniFolder is planned.
`EmbeddedFileBrowser` keeps the panel at its configured root. Folder forwards
a hold inside the embedded surface to the launcher's workspace editor. Folder draws the content and owns navigation, menus,
file actions, and session snapshots. InfiniLaunch supplies the slot and
manages its size, visibility, and error state.

The full interactive browser is a custom workspace item. Android's
`RemoteViews` cannot distribute the embedded custom view as a standard widget
for other launchers. Open sessions only for visible panels, release them when
detached, and respect Folder's session limits. If a panel is unavailable, show
a status and a retry action. Test dragging and resizing outside the hosted
surface as well as taps, scrolling, and Back inside it.

## InfiniCanvas live wallpaper

Ship a system-selectable Android `WallpaperService`. In its configuration
flow, select a still image or animation using the Folder-only libinfiniselect
path and show a preview. Confirming a working source writes its authoritative
reference in `wallpaper/source.iflink`. If selection, resolution, or decoding
fails, preserve the previously selected source. Use Android's wallpaper
preview or selection flow to apply it.

Use libinfinicanvas's viewer decode and playback APIs for all the formats and
subsets the pinned library can render. Support its still bitmap, native animated
drawable, frame renderer, and indexed palette cycling paths. Examples include
GIF, animated WebP, APNG, FLI/FLC, MNG, supported ORA composites, `.icanim`, and
cycling IFF. SVG/SVGZ is still artwork in the current Canvas library. Platform
codecs and Canvas's decoder budgets determine what a particular file can play.
The service owns the presentation and timing; format parsing stays in Canvas.

On service restart or a new preview, resolve the saved source through Folder
again and open the currently granted resource. Decode and render at the
required surface size within Canvas's bounds. Drive animation while the
surface exists and is visible, pause on hide, and release resources when the
surface or engine goes away. Keep preview and running engine state separate.
If the source becomes unavailable, show an interim background and keep its
reference so playback can resume when access returns.

## References

- [Android Home role](https://developer.android.com/reference/android/app/role/RoleManager)
- [Android widget host](https://developer.android.com/develop/ui/views/appwidgets/host)
- [Android widget layout constraints](https://developer.android.com/develop/ui/views/appwidgets)
- [Android wallpaper engine](https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine)
- [InfiniCanvas decode and playback APIs](https://github.com/infiniforge/libinfinicanvas#full-image-viewer-composition)
- [InfiniSelect embedded browser](https://github.com/infiniforge/libinfiniselect/blob/main/docs/embedded-browser.md)
