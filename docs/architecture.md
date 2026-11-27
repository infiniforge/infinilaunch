# InfiniLaunch architecture

This document specifies application boundaries and file layout. InfiniFolder
serves the Programs and dock directories and stores the first workspace
layout. Wallpaper source persistence is planned.

## Responsibilities

| Component | Owns |
| --- | --- |
| InfiniLaunch | Android Home and ordinary app entry points, workspace layout, package-event scheduling, install notification policy, widget hosting, and the wallpaper engine. |
| InfiniFolder | Access to shared storage, directory and file operations, the app inventory service, and the browser that opens items in Programs and other folders. |
| libinfiniselect | The embedded browser gateway, `.iflink` serialization, and the typed clients for Folder's selection, storage, and source resolution contracts. |
| libinfinicore | The existing `AppsUri` package identity and `AppCategories` categorization helpers. |
| libinfinicanvas | Image detection, decoding, still frames, and supported animation renderers. |
| Android | The Home role, installed package metadata and events, notification permission and posting, widget bindings and shortcut host permission, and the selected system wallpaper service. |

Android 11 (API 30) is the minimum because the embedded browser needs
`SurfaceControlViewHost`. InfiniLaunch builds as its own app with pinned
libraries and no InfiniFolder Gradle dependency. Loading Folder capabilities
happens at runtime. The application ID is `com.infinilaunch`. Folder verifies
this caller for the Programs provider contract.

## Shared file layout

Paths are relative to the current Android user's shared internal storage as
shown in InfiniFolder. These paths name the data. Access is through Folder's
public contract.

| Path | Contents and ownership |
| --- | --- |
| `infini/launch/programs/` | The user-managed Programs tree and its app `.iflink` files. Sync creates missing app links and the destination category folders through Folder. |
| `infini/launch/dock/` | User-managed shortcuts and other resources. A hidden `.folder.xml` sets the dock presentation. |
| `infini/launch/desktop/<workspace>/.desktop.json` | Workspace grid, widget and launcher bounds (whole-cell spans with optional half-cell position offsets), bottom-to-top layer order, per-item horizontal and vertical dp margins, and widget bindings. |
| `infini/launch/desktop/<workspace>/` | Direct child files and shortcuts appear as desktop launchers. |
| `infini/launch/desktop/.active` | The selected workspace directory name. |
| `infini/launch/workspaces/layout.json` | Legacy layout copied into Home when its desktop settings are missing. |
| `infini/launch/wallpaper/source.iflink` | The selected wallpaper source as a durable resource reference. The original file can live anywhere accessible to Folder. |
| `infini/launch/state/install-sync.json` | A small versioned checkpoint for install notification delivery, with any unissued summary. Programs itself remains the only catalog of links. |

Programs, the dock, workspace layout, and sync checkpoint use the paths above.
The wallpaper path is reserved for its milestone. Create files
only when their feature needs them. Store versioned metadata as plain files in this tree and
replace it atomically through Folder. Check for stale writes where a file can be
edited by both apps. Retain malformed or unsupported-version user files and
offer a clear recovery state instead of silently resetting them. Serialize
workspace writes even if multiple Home entry points are open.

The launcher has no private database, private preferences, persistent package
index, or private copy of this tree. In-memory state may be rebuilt from the
files and current Android platform state. Android still maintains system-owned
state such as the Home role and widget bindings. Browser session state is
Folder-owned and opaque to the launcher.

## Provider contracts

InfiniLaunch uses libinfiniselect to host Folder's browser. Folder's session
opens files and links on tap, and its full-browser button opens the current
directory for editing. Programs anchors the embedded view to an authoritative
directory provided by Folder. The workspace editor keeps temporary widget
previews in memory. Folder supplies the preview for its current Programs
surface through the same caller-verified provider.

The Programs-specific Android provider method is
`com.infinifolder.launch.PROGRAMS`, version 1, on
`content://com.infinifolder.selection`. The caller is verified by the provider.
It returns the Programs or dock directory, lists named desktop workspaces and
their direct file launchers, provides each launcher's resolved label and icon
from Folder's shared resource icon resolver, reads or saves dock presentation
and desktop settings,
resolves foreground opens, manages native shortcut files, orders and removes
dock entries, routes launcher moves between managed directories, runs
reconciliation, and acknowledges pending notices. The initial client lives in InfiniLaunch because the method is
specific to this app. libinfiniselect's existing browser and `.iflink` format
are unchanged. The Folder implementation and wire fields are documented in
`docs/launch-programs.md` in the InfiniFolder repository.

Folder requests a fresh current-profile Android inventory and applies the shared
category helper. Folder atomically creates links for packages absent from the
whole tree and stores the install checkpoint. The result identifies new apps
and their actual category locations. Workspace writes replace the shared file
only if it still matches what the launcher read. Wallpaper operations will
extend the contract when that feature is implemented.

The default libinfiniselect picker and collections support system or private
fallbacks for other clients. InfiniLaunch uses a Folder-only path, including
for wallpaper and browser root selection. If the public API does not yet offer
a strict path, add one to libinfiniselect. A Folder-only failure leaves the
feature paused and presents a status and retry action. Home continues to
provide access to Android Home settings.

Resource references in launcher files remain authoritative URIs. A temporary
content URI and its grant are used for the current handoff or decode. When
reopening a resource, resolve it again through Folder. Preserve the source
when access expires, is denied, or the provider is offline.

## Contract references

- [InfiniSelect embedded browser and FileShortcut](https://github.com/infiniforge/libinfiniselect/blob/main/docs/embedded-browser.md)
- [InfiniSelect collection and listing relay](https://github.com/infiniforge/libinfiniselect/blob/main/docs/relay-storage.md)
- [AppsUri in libinfinicore](https://github.com/infiniforge/libinfinicore/blob/main/core-android/src/main/java/com/infinifolder/apps/AppsUri.kt)
- [AppCategories in libinfinicore](https://github.com/infiniforge/libinfinicore/blob/main/core-android/src/main/java/com/infinifolder/apps/AppCategories.kt)
- [Canvas runtime capabilities](https://github.com/infiniforge/libinfinicanvas/blob/main/CAPABILITIES.md)
- [Local implementation workflow](workflow.md)
