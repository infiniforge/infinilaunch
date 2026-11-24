# InfiniLaunch architecture

This document specifies the planned boundaries and file layout. The public
contract additions below are design requirements; their method names and wire
formats will be documented in the owning repositories before release.

## Responsibilities

| Component | Owns |
| --- | --- |
| InfiniLaunch | Android Home and ordinary app entry points, workspace layout, package-event scheduling, install notification policy, widget hosting, and the wallpaper engine. |
| InfiniFolder | Access to shared storage, directory and file operations, the app inventory service, and the browser that opens items in Programs and other folders. |
| libinfiniselect | The embedded browser gateway, `.iflink` serialization, and the typed clients for Folder's selection, storage, and source resolution contracts. |
| libinfinicore | The existing `AppsUri` package identity and `AppCategories` categorization helpers. |
| libinfinicanvas | Image detection, decoding, still frames, and supported animation renderers. |
| Android | The Home role, installed package metadata and events, notification permission and posting, widget bindings and shortcut host permission, and the selected system wallpaper service. |

Android 11 (API 30) is the planned minimum because the embedded browser needs
`SurfaceControlViewHost`. InfiniLaunch builds as its own app with pinned
libraries and no InfiniFolder Gradle dependency. Loading Folder capabilities
happens at runtime. The proposed application ID and collection client ID are
`com.infinilaunch` and `infinilaunch`, which match the existing verified package
to collection namespace rule.

## Shared file layout

Paths are relative to the current Android user's shared internal storage as
shown in InfiniFolder. These paths name the data. Access is through Folder's
public contract.

| Path | Contents and ownership |
| --- | --- |
| `infini/launch/programs/` | The user-managed Programs tree and its app `.iflink` files. Sync creates missing app links and the destination category folders through Folder. |
| `infini/launch/workspaces/layout.json` | Versioned Home layout. Stores ordered pages, grid positions and sizes, and references for pinned items, embedded browsers, and Android widgets. |
| `infini/launch/wallpaper/source.iflink` | The selected wallpaper source as a durable resource reference. The original file can live anywhere accessible to Folder. |
| `infini/launch/state/install-sync.json` | A small versioned checkpoint for install notification delivery, with any unissued summary. Programs itself remains the only catalog of links. |

Only the Programs location is fixed by the product requirement. The companion
paths above are the proposed version-one layout. Create files only when their
feature needs them. Store versioned metadata as plain files in this tree and
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

Use current libinfiniselect APIs for browser embedding and durable source
resolution where they meet the feature requirements. Keep the Programs browser
anchored at its directory and delegate file actions to Folder. Where the
existing embedded selector returns a regular file selection, expose or use a
public Folder action that opens it with the same grants and behavior as the
normal Folder browser.

Add a scoped Programs contract for a fresh current-profile install inventory
and reconciliation. The install inventory should reuse InfiniFolder's existing
installed-application provider and classifier. It needs package identity,
label, defined category, and installation time, with a clear distinction
between a complete inventory and an unavailable or incomplete result. The
existing cached or paged directory listings are not sufficient as a fresh
package inventory or an atomic create-if-missing operation.

Folder validates the caller and restricts writes to the defined
`infini/launch` operations. Programs sync only creates a link if its package is
absent from the whole Programs tree. Its result includes the authoritative
location of the existing or created link so a notification can open the
correct category. Scoped metadata reads and conditional atomic writes cover
the launcher files above. Use bounded transport and return explicit incomplete,
unsupported, unavailable, and conflict results. Implement and document the
provider in InfiniFolder and the client in libinfiniselect as separate changes.

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
