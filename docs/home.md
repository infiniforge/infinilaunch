# Home, workspaces, widgets, and wallpaper

## Android Home and workspaces

The ordinary app icon and Android's Home entry point open the same workspace
experience. Offer the Home role through Android's consent flow and give the
user a way to return to Android's Home settings. Programs opens the embedded
InfiniFolder browser at the shared Programs root. Resource links use Folder's
public opening workflow. Android's shortcut APIs handle Android-provided
shortcuts when the launcher has host permission.

Allow adding, removing, and ordering workspace pages and positioning or
resizing items on their grid. Persist ordered pages, item identities, bounds,
and references in the shared layout file. Store a widget binding ID with its
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

Use Android's `AppWidgetHost` to show widget providers the system makes
available for the current profile. Support selection, system binding consent,
provider configuration, placement, resize, cancellation, and removal.
Release allocated IDs if placement is cancelled or cannot be saved. When
removing a placed widget, commit the layout change before releasing its ID.
Reconcile stale and unreferenced host IDs against a successfully read layout;
an unreadable layout is not evidence that widget IDs are unused. Forward
size changes to the provider and tie host listening to the Home lifecycle.

Android owns the actual bindings. If a layout is copied to another device or
a binding becomes invalid, show the widget's saved position with an action to
bind it again. Handle Android shortcuts separately from widget placement.

## Interactive File Browser item

Offer **File Browser** in InfiniLaunch's item picker beside Android widgets.
Place and resize it in the same grid. The default root is Programs, and the
user can configure another directory through InfiniFolder. Use
`EmbeddedFileBrowser` with its root boundary so each panel stays in its
configured tree. Folder draws the content and owns navigation, menus,
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
