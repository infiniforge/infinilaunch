# InfiniLaunch

InfiniLaunch is an Android Home app that uses real directory trees and
InfiniFolder's browser for Programs and file access. Installed applications are
represented by ordinary `.iflink` files under `Internal storage/infini/launch/programs`.
Users organize that tree in InfiniFolder and can mix app links with their own
folders, files, and shortcuts.

**Status:** Android Home, Programs sync, and a transparent workspace. The default
workspace has the Clock at the top right, the movable Programs widget at the
lower right, and a scrolling dock backed by `infini/launch/dock`. The
launcher hosts Android widgets and one-cell file launchers in named desktop
workspaces. New Android pinned shortcuts are files in the active workspace.
InfiniCanvas live wallpaper is planned. Use Android 11 (API 30) or newer. Folder owns shared directory access and the
browser. When it is unavailable, InfiniLaunch shows its status and Home settings.

## Standalone build

Install Git, Python 3, JDK 17 or 21, Android SDK platform 36, and Android build
tools 35.0.0. Set `ANDROID_HOME` or this checkout's untracked
`local.properties`. On Termux, install its native `aapt2`.

```sh
./build.sh --max-workers=1 --console=plain
```

The script fetches the exact libinfiniselect source revision in
[dependencies.lock.json](dependencies.lock.json), then builds
`app/build/outputs/apk/debug/app-debug.apk` with the checksum-pinned Gradle
wrapper. A sibling app checkout is not required. The Programs runtime requires
the [InfiniFolder Programs provider contract](docs/architecture.md#provider-contracts)
and Folder's shared storage access.

## Documentation

- [Application behavior](docs/APPLICATION.md)
- [Architecture and shared file layout](docs/architecture.md)
- [Programs sync and install notifications](docs/programs.md)
- [Workspaces, widgets, and wallpaper](docs/home.md)
- [Implementation workflow and acceptance checks](docs/workflow.md)
- [Documentation index](docs/README.md)
