#!/usr/bin/env python3
"""Resolve release, snapshot and GitHub-only release-candidate versions."""

import os
import re
import subprocess
from pathlib import Path

CORE = r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)"
STABLE = re.compile(CORE)
RC = re.compile(rf"({CORE})-rc\.(0|[1-9][0-9]*)")
VERSION = re.compile(rf"{CORE}(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?")


def resolve(tags, ref_type, ref_name):
    if ref_type == "tag":
        if not ref_name.startswith("v"):
            raise ValueError("Release tags must start with v")
        version = ref_name[1:]
    elif ref_type == "branch" and ref_name == "main":
        stable = [tuple(map(int, tag[1:].split("."))) for tag in tags
                  if tag.startswith("v") and STABLE.fullmatch(tag[1:])]
        latest = max(stable, default=(0, 0, 0))
        version = f"{latest[0]}.{latest[1]}.{latest[2] + 1}-SNAPSHOT"
    else:
        raise ValueError("Releases require a version tag; automatic snapshots use main")
    match = VERSION.fullmatch(version)
    if not match or (match[1] and any(part.isascii() and part.isdigit() and len(part) > 1 and part.startswith("0")
                                    for part in match[1].split("."))):
        raise ValueError(f"Invalid release version: {version}")
    return {
        "version": version,
        "is_snapshot": str(version.endswith("-SNAPSHOT")).lower(),
        "is_prerelease": str(match[1] is not None).lower(),
        "test_release": str(RC.fullmatch(version) is not None).lower(),
        "release_tag": f"v{version}",
    }


if __name__ == "__main__":
    tags = subprocess.check_output(["git", "tag", "--list"], text=True).splitlines()
    try:
        values = resolve(tags, os.environ["GITHUB_REF_TYPE"], os.environ["GITHUB_REF_NAME"])
    except ValueError as error:
        raise SystemExit(str(error)) from error
    values["revision"] = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    with Path(os.environ["GITHUB_OUTPUT"]).open("a") as output:
        output.writelines(f"{key}={value}\n" for key, value in values.items())
    print(f"Resolved {values['version']}; GitHub-only RC: {values['test_release']}")
