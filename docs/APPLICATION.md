# InfiniLaunch application

## Purpose

InfiniLaunch is the planned Android launcher, application organizer, and suite entry point. It extends the directory-oriented approach of Infinifolder to installed applications and launch actions while leaving file browsing and storage navigation with Infinifolder.

## Primary use cases

- Launch installed Android applications and organize them into user-defined categories or hierarchies.
- Detect newly installed applications and help place them into the user's organization.
- Create shortcuts and custom launch entries for apps and supported actions.
- Offer configurable home-screen layouts, quick access, and widget hosting if permitted by Android.
- Provide convenient access to Infiniforge applications and optionally launch resources through their public Android contracts.

## Current implementation status

This repository is a planned placeholder. It contains no Android application source, Gradle build, app build script, launcher activity, widget host, API, or declared library dependency. Application discovery, directory organization, widget hosting, and desktop-style behavior are not implemented.

## Intended responsibilities and shared libraries

InfiniLaunch should own the launcher/home-screen experience, installed-app organization, shortcut configuration, and launcher-owned layout/preferences. It should use Android's public package-manager, launcher, shortcut, widget, and activity APIs, respecting package visibility, background-launch rules, and platform restrictions.

Infinifolder remains responsible for files, storage backends, and authoritative resource navigation. Resource shortcuts should preserve their URI identity and permissions through documented Android intents/content URIs or a future public library contract. libinfiniselect could be evaluated for resource resolution where appropriate, but this placeholder declares no dependency; do not add one without a concrete user flow and API fit.

## Planned and exploratory functionality

Potential features include application categories arranged like directories, automatic classification of new apps, custom action shortcuts, home-screen/widget layouts, suite quick access, and desktop-style workflows. The exact launcher role, default-home requirements, supported Android versions, widget policy, and expected behavior when a target app is absent remain design work.

## Interoperability

The [generated ecosystem map](ECOSYSTEM.md) provides the wider application/library map. The launcher may call installed applications with public Android APIs. It should not import other app source, inspect private data, or require Infinifolder to launch apps. Resource shortcuts that target Infinifolder or another consumer must carry URI grants and durable authoritative references as required by that contract. Nothing in this placeholder implements such integration.

## Documentation audit

- Consistent: repository guidance identifies a planned launcher placeholder and keeps its build independent.
- Incomplete: launcher-vs-organizer behavior, home-screen replacement, Android version scope, package visibility, and widget policy have no implementation/specification.
- Unverified: all app organization, automatic classification, shortcuts, and widget behavior are future work.

## Limitations and outstanding work

- No launcher, application catalog, shortcut system, widget host, Android manifest, or standalone build exists.
- Android version rules and package visibility may limit installed-app discovery and background activity launches.
- Infinifolder resource navigation and InfiniLaunch app organization are separate responsibilities.
- No shared-library dependency or interapplication protocol is currently defined.
- Define whether this will replace Android's home activity or act as an optional organizer/launcher before documenting platform behavior as complete.
