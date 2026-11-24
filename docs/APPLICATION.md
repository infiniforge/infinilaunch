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

Home provides multiple workspace pages, standard Android wallpaper, app and
resource shortcuts, and resizable third-party Android widgets. An InfiniFolder
browser panel can occupy a slot in the same grid and open Programs or another
Folder directory. The included live wallpaper selects its source through
InfiniFolder and plays supported still images and animations using
libinfinicanvas. See [Home and wallpaper](home.md).

## Runtime requirements and unavailable states

The planned minimum is Android 11 (API 30), with the personal/current profile as
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

This is the approved direction for the design. The application, storage API
extensions, install job, workspaces, widgets, and wallpaper are all planned.
See the [implementation workflow](workflow.md) for delivery order and acceptance.
