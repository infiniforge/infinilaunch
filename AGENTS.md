# InfiniLaunch agent guidance

InfiniLaunch is a planned Infiniforge application launcher. The intended role includes launching apps and related launch functions; details can evolve as the project is designed. This directory is a project placeholder.

## Independence and integration

- Establish a standalone Android application build in this repository. It must build from its own checkout with only its declared and pinned library dependencies and documented toolchain.
- Keep its Gradle build independent from every other application. Do not import sibling app source or depend on sibling build outputs.
- Use public Android package, activity, and launcher APIs. Handle unavailable or disabled targets safely and respect Android permission and background-launch rules.
- Infinifolder and all other Infiniforge apps remain optional; the launcher must not depend on suite-wide source or private APIs.

## Project focus

- Keep launcher functionality and supported Android versions explicit in the app documentation as implementation develops.
- Document the app's standalone build and keep library revisions explicit.
