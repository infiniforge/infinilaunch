#!/usr/bin/env python3
"""Regenerate checked-in InfiniLaunch icons using the pinned InfiniFolder compositor."""

import argparse
import hashlib
import importlib.util
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
FOLDER_REVISION = "18b48b1fbf481b03d7d57f10547880945eb400ed"
INPUTS = {
    "scripts/build-app-icons.py": "eb83d70bb404600aaf4bcaedc360c52167074caf3fa1c35f289c348433605a39",
    "infini.svg": "a2a453e9652875fb46ef3411832309935840b7e5343cb40a7ec6b5b705ccd135",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder_source", type=Path, help=f"InfiniFolder source at {FOLDER_REVISION}")
    parser.add_argument("--renderer", default=shutil.which("rsvg-convert") or "rsvg-convert")
    args = parser.parse_args()
    for relative, digest in INPUTS.items():
        source = args.folder_source / relative
        if not source.is_file() or hashlib.sha256(source.read_bytes()).hexdigest() != digest:
            parser.error(f"{source} differs from InfiniFolder {FOLDER_REVISION}")

    # Load the suite compositor unchanged with the app-specific base artwork.
    sys.dont_write_bytecode = True
    compositor_path = args.folder_source / "scripts/build-app-icons.py"
    spec = importlib.util.spec_from_file_location("infini_app_icons", compositor_path)
    compositor = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(compositor)
    output = ROOT / "build/branding"
    compositor.build_one(
        "infinilaunch",
        (ROOT / "artwork/launcher.svg").read_bytes(),
        ET.parse(args.folder_source / "infini.svg").getroot(),
        output,
        None,
        args.renderer,
    )
    generated = output / "infinilaunch"
    res = ROOT / "app/src/main/res"
    files = [Path(f"mipmap-{density}/ic_launcher.png") for density in compositor.DENSITIES]
    files.append(Path("values/icon_aliases.xml"))
    for relative in files:
        destination = res / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(generated / relative, destination)


if __name__ == "__main__":
    main()
