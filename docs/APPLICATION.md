# InfiniLaunch application

## Purpose

InfiniLaunch provides Android Home, workspaces, and access to a directory of
programs and other resources. It also opens from its own app icon so users can
try it before selecting it as their default Home. Android handles Home selection
and lets users switch to another Home app.

## Programs

Programs opens InfiniFolder's browser at `Internal storage/infini/launch/programs`.
Each subdirectory can be a category and can contain any number of other
directories, app links, ordinary files, or other shortcuts. InfiniFolder handles
navigation and edits through its existing tools. Tapping an app link uses
InfiniFolder's existing application-link dispatch, and other items open through
the applicable InfiniFolder workflow.

On startup or an Android install event, a short background job makes sure every
installed package visible in the supported Android profile has an app link
somewhere under Programs. It places missing links in their defined categories and
keeps existing placements. After a successful pass, it reports only genuine new
installs and their actual locations. [Programs sync](programs.md) defines the
rules for initial setup, repairs, and interrupted passes.

## Home, widgets, and wallpaper

Home shows Android's current wallpaper behind named workspaces, with a
system Clock slot at the top right and the embedded Programs widget at the
lower right by default. The bottom dock scrolls through files in
`infini/launch/dock`, including Android pinned shortcuts. Long-press empty
space for application widgets. Workspace edits are saved through InfiniFolder.
Each directory in `infini/launch/desktop` is a workspace and its files appear as
launchers. Swipes cycle through the workspaces. Configurable browser roots and
libinfinicanvas live wallpaper are planned. See [Home and wallpaper](home.md).

## Runtime requirements and unavailable states

The minimum is Android 11 (API 30), with the personal/current profile as
the first supported profile. Additional work and private profiles need an
explicit profile-aware link contract and device coverage.

A compatible InfiniFolder installation with access to shared internal storage is
required for Programs, file selection, and access to the launcher files. The
InfiniLaunch entry point stays available for a status message and Android Home
settings if Folder is missing, incompatible, or denied access. It resumes the
shared workflows when access is restored. InfiniLaunch uses only InfiniFolder
for browsing and selection; there is no Android system picker, replacement
browser, alternate app list, or private data store.

## Implementation status

The Home activity, initial workspace, Clock slot, dock, Folder-hosted Programs
widget, package-triggered sync, and shared install checkpoint are implemented.
See the [implementation workflow](workflow.md) for remaining delivery and
device checks.
