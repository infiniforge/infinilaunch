# InfiniLaunch

InfiniLaunch is a planned Android Home app that uses real directory trees and
InfiniFolder's browser for Programs and file access. Installed applications are
represented by ordinary `.iflink` files under `Internal storage/infini/launch/programs`.
Users organize that tree in InfiniFolder and can mix app links with their own
folders, files, and shortcuts.

**Status:** Design and implementation workflow. This checkout contains
documentation only. There is no app source, Gradle project, build command, or APK yet.

The planned app opens from its icon or as Android's default Home when the user
selects it. It provides workspaces, third-party widgets, embedded InfiniFolder
panels, and a system live wallpaper backed by libinfinicanvas. InfiniFolder is
required at runtime for browsing and access to the shared tree. The planned
minimum Android version is Android 11 (API 30).

- [Application behavior](docs/APPLICATION.md)
- [Architecture and shared file layout](docs/architecture.md)
- [Programs sync and install notifications](docs/programs.md)
- [Workspaces, widgets, and wallpaper](docs/home.md)
- [Implementation workflow and acceptance checks](docs/workflow.md)
- [Documentation index](docs/README.md)

The app will build from this repository with its own Android build and pinned
library dependencies. An installed compatible InfiniFolder supplies the runtime
services through public contracts. Toolchain versions and the standalone build
command will be recorded when the first Gradle project lands.
