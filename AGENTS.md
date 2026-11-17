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

## Ecosystem workflow for agents

1. Read `docs/ECOSYSTEM.md` first to understand the component map and ownership boundaries.
2. Before changing a public interface, read this repository's architecture and API documentation listed below and inspect its compatibility notes.
3. Check the owning shared library repository before implementing behavior that may already exist elsewhere. Do not conclude a capability is unavailable just because its implementation is outside the current repository.
4. Keep application responsibilities, shared library APIs, and other applications' private data/source separate.
5. Before a cross-repository change, check the exact dependency pin and the relevant interoperability contract. Dependency versions and protocol versions are separate; do not infer protocol incompatibility from a library-version mismatch.
6. Update the local documentation index and relevant API/behavior documentation when public behavior changes. `docs/ECOSYSTEM.md` is generated from the central manifest and must not be edited locally.

## Documentation index

- `README.md` — project purpose, prerequisites, and standalone build instructions.
- `AGENTS.md` — repository-specific development rules and this ecosystem workflow.
- `docs/README.md` — local documentation index.
- `docs/ECOSYSTEM.md` — generated ecosystem-wide inventory, boundaries, dependencies, versions, and protocols.