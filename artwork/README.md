# InfiniLaunch launcher icon

`launcher.svg` is the original app-grid base. The finished icon uses the
shared InfiniForge infinity emblem and the compositor in InfiniFolder's
`scripts/build-app-icons.py`, both from InfiniFolder revision
[`18b48b1`](https://github.com/infiniforge/infinifolder/tree/18b48b1fbf481b03d7d57f10547880945eb400ed).
The shared emblem and compositor retain their original attribution and terms.

The Android resources are already rendered at the suite's five launcher
densities. Android uses the same full artwork for the launcher and round
resource names and can apply its own mask. A regular APK build uses these
checked-in resources.

To regenerate after editing the base, use Python 3 and librsvg's
`rsvg-convert`, and pass a checkout or extracted copy of the pinned
InfiniFolder source explicitly.

```sh
python3 scripts/build-launcher-icon.py /path/to/pinned/infinifolder
```

The helper verifies both source inputs. Full-size composite SVGs and
intermediate PNGs go under `build/branding/infinilaunch/`.
