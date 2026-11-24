# Programs and install reconciliation

## Directory contract

`Internal storage/infini/launch/programs` is the Programs menu. It accepts
ordinary folders, files, and all supported InfiniFolder link types. Users can
create, rename, nest, and move directories in InfiniFolder and place app links
anywhere under the root. An app link directly in Programs also counts.

A generated entry is a version-one `FileShortcut` document with an existing
`apps://package/<package>` target and the Android display label. Existing Folder
links may carry a category query. Compare package identities parsed through the
app URI contract, independent of that query, link basename, display name, or
parent folder. One link anywhere satisfies the package. Keep user-created copies
where they are. A package without a launch activity uses Folder's existing
App Info behavior when Android allows it.

For a missing package, create one link in the directory matching its defined
`AppCategories` category. Use the shared classification rules, including their
system and uncategorized cases. Custom placements take precedence once a link
exists. Preserve existing folder names and file contents. A deleted last link
is restored on the next successful pass if its package is still installed.

## Triggers and job lifetime

- When the launcher starts, queue a one-time pass on a background worker.
- When Android delivers `LauncherApps.Callback.onPackageAdded` for the supported
  profile, queue the same work. Register the callback with the process lifetime
  so a live Home process can receive events while another app is foregrounded.
- Serialize passes. Coalesce bursts, and schedule a follow-up pass if an install
  event arrives during a pass. The job exits once the work is complete. There
  is no polling, periodic scan, or continuously running foreground service.
- Startup catches up after the process dies or misses a callback. Android does
  not guarantee a cold start of InfiniLaunch for arbitrary package installs.
  An update event is distinct from an install event.

## Pass workflow

1. Request a fresh, complete current-profile installation inventory from Folder.
   Android is the source of installed package state. Cover packages Folder can
   see, including packages with no launcher activity. Treat an unavailable or
   incomplete inventory as a failed pass.
2. Resolve or create the real Programs root through Folder. Walk real
   subdirectories, including user-created and hidden folders. Never traverse
   symbolic links, virtual containers, or directory shortcut targets. Identify
   valid app `.iflink` files by reading bounded payloads. Other items are left as
   ordinary browsable content. A malformed link stays in place. If a relevant
   subtree or link cannot be read, the pass cannot establish that a shortcut is
   absent.
3. For each absent package, ask Folder to create the link only after it checks
   for an existing occurrence. Make the create atomic and enforce the root
   scope. Coordinate with other reconciliation passes and Folder moves where
   possible. If the tree changes mid-scan, retry the affected check or return
   an incomplete result rather than assuming absence. Interrupted writes can
   be retried safely because the next pass uses the actual tree.
4. Report the actual category paths for completed new installs. Advance the
   shared checkpoint only once the inventory and link pass are complete. A
   failure leaves the files already created in place and preserves the
   checkpoint for the next authorized run.

The job is additive. Uninstalling an app leaves its link for the user to keep
or remove. If the app is reinstalled and the link remains, it keeps its prior
placement. A package that goes away during a pass can be picked up if it is
installed again later. The worker never opens any link.

## Install notifications

The first successful pass seeds the directory and records a baseline silently.
After that, post one summary for the genuine new installs handled in that pass.
Include each application's label and its actual relative folder, such as
`Programs/Audio` or `Programs/Work/Tools`. Tapping the summary opens Programs
through Folder; use a category deep link when the notice has a single
destination. An install can be reported at its existing placement if someone
already put its link there.

An Android install event is a direct signal. For missed events, compare
`PackageInfo.firstInstallTime` from the fresh inventory against the previous
successful scan cutoff. Take the new cutoff at scan start so an install after
it can be handled on the next pass. Exclude installs newer than that cutoff
from the current startup catch-up report. When times are ambiguous, such as
after a clock rollback, avoid guessing from file modification times or treating
repaired links as installs. Updates, moves, renames, and repairs are silent.

Store the cutoff and an unfinished notification summary, if any, in the shared
checkpoint. Commit the summary and cutoff together before posting. Use a stable
notification tag for that summary so retrying an interrupted post replaces the
same notice. Before retrying, confirm the packages are still present and the
locations are current. If notification permission is denied, link creation still
succeeds, and a later permission grant does not turn silent passes into a flood
of old notifications. A missing checkpoint starts a new silent baseline without
clearing existing files.

- [Android package callbacks](https://developer.android.com/reference/android/content/pm/LauncherApps.Callback)
- [Android implicit broadcast limits](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions)
- [Android package install timestamps](https://developer.android.com/reference/android/content/pm/PackageInfo#firstInstallTime)
- [Architecture and provider ownership](architecture.md)
