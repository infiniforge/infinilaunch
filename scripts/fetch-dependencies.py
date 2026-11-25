#!/usr/bin/env python3
"""Populate this checkout's pinned library sources."""
import json
from pathlib import Path
import subprocess


def git(directory, *args, capture=False):
    return subprocess.run(
        ["git", "-C", str(directory), *args],
        check=True,
        text=True,
        stdout=subprocess.PIPE if capture else None,
    ).stdout


def main():
    root = Path(__file__).resolve().parents[1]
    lock = json.loads((root / "dependencies.lock.json").read_text())
    libraries = root / lock["directory"]
    if libraries.is_symlink():
        raise SystemExit("The library directory must be inside this checkout")
    libraries.mkdir(exist_ok=True)

    for name, pin in lock["repositories"].items():
        target = libraries / name
        if target.is_symlink():
            raise SystemExit(f"Dependency {name} must be inside this checkout")
        target.mkdir(exist_ok=True)
        git_dir = target / ".git"
        if not git_dir.exists():
            if any(target.iterdir()):
                raise SystemExit(f"Refusing to replace existing files in {target}")
            git(target, "init", "-q")
        if not git_dir.is_dir() or git_dir.is_symlink():
            raise SystemExit(f"{name} must be an independent checkout")
        if git(target, "status", "--porcelain", capture=True).strip():
            raise SystemExit(f"{name} has local changes")
        current = git(target, "rev-parse", "--revs-only", "HEAD", capture=True).strip()
        if current != pin["commit"]:
            git(target, "fetch", "--depth", "1", pin["url"], pin["commit"])
            git(target, "checkout", "--detach", pin["commit"])
        print(f"{name} {pin['commit']}")


if __name__ == "__main__":
    main()
