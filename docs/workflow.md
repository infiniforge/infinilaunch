# InfiniLaunch implementation workflow

This is the delivery plan. No build or device validation has happened yet.
Record actual toolchain versions, dependency pins, commands, and evidence as
each milestone lands.

## 1. Standalone shell and the first shared Programs flow

1. Create the standalone Android project in this repository with the Gradle
   wrapper, its own application ID, the ordinary app entry point, and the Home
   activity. Document how to build and install the debug APK from a fresh
   checkout. Use Android 11 as the minimum and set the target SDK against the
   supported toolchain when scaffolding.
2. Pin libinfiniselect and any direct shared dependencies. Verify the namespace
   mapping with the actual application ID on the integration build.
3. Implement the missing public storage capabilities in InfiniFolder and the
   client support in libinfiniselect. Reuse Folder's installed-app source,
   classification rules, app links, and browser. Add the strict Folder-only
   path and opening actions required by
   [the architecture](architecture.md#provider-contracts).
4. Open Programs in the shared tree and attach the embedded browser. Implement
   startup reconciliation, install event scheduling, the shared checkpoint, and
   location notifications. Surface Folder unavailability in the shell.

**Milestone demo:** Start InfiniLaunch and see the existing apps seed silently.
Install another app. See its category in a single notification, launch it from
Programs, move or rename its actual link in InfiniFolder, then reopen
InfiniLaunch and verify that the link stays in its new location.

## 2. Workspace layout and widget hosting

1. Add workspace pages and persist layout through the shared file contract.
2. Support Android Home role transitions, pinned app and resource links, and
   system wallpaper.
3. Host and configure third-party Android widgets. Add the InfiniFolder File
   Browser item to the same grid. Connect binding, browser surface, and
   workspace visibility to the appropriate lifecycle.

**Milestone demo:** Arrange and resize a third-party widget and two browser
panels pointing to different directories. Restart Home. Confirm that the
positions, bindings, and directory roots are restored.

## 3. InfiniCanvas wallpaper

1. Pin libinfinicanvas and its declared dependency closure in InfiniLaunch.
2. Add Folder-only source selection, preview, and the shared wallpaper link.
3. Integrate the Android wallpaper engine with Canvas still and animated
   renderers. Confirm the supported limits against the pinned Canvas build.
   Add reusable decoder or frame API changes in libinfinicanvas if needed.

**Milestone demo:** Apply a still image and an animation from InfiniFolder
through Android's wallpaper picker. Restart the engine and confirm that it
reopens the chosen source. Hide Home and verify playback pauses.

## Repository handoffs

| Repository | Planned work |
| --- | --- |
| InfiniLaunch | Standalone app, package callback and worker, Home layout, widget slots, wallpaper service, integration docs, and dependency pins. |
| InfiniFolder | Scoped install inventory, recursive ensure-link and metadata operations, plus public browser/open behavior needed by the launcher. |
| libinfiniselect | Typed client contracts, strict Folder-only behavior, and embedded panel support as needed. Keep existing behavior for other consumers. |
| libinfinicore | Reuse existing app URI and category helpers. Change their public API only if compatibility requires it. |
| libinfinicanvas | Reuse decode and playback APIs. Add only missing reusable rendering behavior in this library. |

Develop and review each change in its owning repository. Inspect the exact
public contract and revision before updating an app dependency pin. Version
protocol changes separately from library releases. Keep SDK and build
prerequisites in the buildable project README. Update the generated ecosystem
inventory through the central manifest when app status or contracts change.

## Acceptance checklist

These checks define release evidence. They are pending.

- **Build and entry points:** Build in a fresh InfiniLaunch checkout without an
  InfiniFolder checkout or suite directory. Install on the minimum supported
  Android release and a current release. Open from its icon, select it as Home,
  launch an app, return with Home, and switch back to another Home app.
- **Real Programs tree:** Seed visible packages into category directories. Open
  a generated link in both InfiniFolder and the embedded Programs view. Check a
  package
  without a launch activity. Rename, move, and duplicate links, including a
  move into a nested custom category. Add ordinary files and directory links.
  Inspect the actual tree after another pass. Verify that removing the last
  installed app link restores it quietly and that uninstalling does not erase
  it.
- **Background sync:** Install one app, then several in quick succession.
  Check the notification names and actual folders. Verify updates do not
  notify. Stop the launcher process, install an app, and confirm the next
  startup catches up. Check notification denial, interruption around a
  checkpoint, and a concurrent move during reconciliation.
- **Unavailable data:** Exercise missing or incompatible Folder, revoked
  storage access, an unreadable Programs subtree, and a malformed metadata
  file. Confirm that existing files are preserved, the shell explains the
  issue, no alternate picker or private store is created, and data access can
  resume after recovery.
- **Workspaces and widgets:** Exercise binding and cancellation, provider
  configuration, resize, removal, role changes, restart, and rotation. Test
  multiple browser panels, locked roots, scrolling, Back, surface recreation,
  and Folder process loss.
- **Wallpaper:** Cover a static image, a native animated drawable, a Canvas
  frame animation, and IFF palette cycling. Verify animation timing, restart,
  preview isolation, source-access loss, resource release, and visibility
  pauses on a handset.
