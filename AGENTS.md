# InfiniLaunch agent guidance

InfiniLaunch is an Android Home app whose Programs menu is a real shared
directory. This repository currently contains the design and implementation
workflow.

## Ownership and integration

- Keep InfiniLaunch independently buildable and installable. Use declared, pinned
  library dependencies and document the standalone toolchain and build command.
- InfiniFolder supplies all file browsing, file selection, editing, and access to
  the shared launcher directory. Integrate through libinfiniselect and documented
  Android contracts. Use the explicit unavailable state if Folder cannot provide
  a feature. Do not add a system picker, second browser, or alternate app drawer.
- Keep launcher-owned durable data in the shared `infini/launch` tree. The Programs
  tree and its real `.iflink` files are authoritative. Do not create a private
  database, private preferences store, or mirror of Programs.
- Use public Android APIs for Home, package events, notifications, widget hosting,
  and wallpaper. Use libinfinicanvas for wallpaper decoding and animation.
- Keep the storage provider and browser implementation in InfiniFolder. Never
  import a sibling application's code, Gradle project, or build output, or
  access its private data.

## Development workflow

1. Read [the generated ecosystem map](docs/ECOSYSTEM.md), then the
   [application spec](docs/APPLICATION.md) and [architecture](docs/architecture.md).
2. Use [the implementation workflow](docs/workflow.md) for sequencing and
   acceptance. Read [Programs](docs/programs.md) and [Home](docs/home.md) before
   changing those features.
3. Inspect the owning library before adding reusable behavior. Check the exact
   dependency pin and public protocol before changing a cross-repository contract.
   Land each app or library change in its own repository.
4. Update the documentation index and relevant behavior or API docs with the
   implementation. The generated ecosystem map is synchronized centrally and
   must not be edited in this repository.
