#!/usr/bin/env python3
"""Verify the complete staged KMP publication before independent consumer resolution."""

import argparse
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZipFile


def verify(repository, version, full=False, github=False):
    settings = Path(__file__).resolve().parents[2] / "settings.gradle.kts"
    library = re.search(r'rootProject\.name\s*=\s*"([^"\n]+)"', settings.read_text()).group(1)
    repository = Path(repository).resolve()
    base = repository if github else repository / "com/eignex"
    targets = ["jvm", "linuxx64"]
    if full:
        targets += ["js", "wasm-js", "wasm-wasi", "linuxarm64", "macosarm64", "mingwx64", "iosx64", "iosarm64", "iossimulatorarm64"]
    modules = [library] + [library + "-" + target for target in targets]
    for module in modules:
        directory = base if github else base / module / version
        stem = f"{module}-{version}"
        metadata = directory / f"{stem}.module"
        pom = directory / f"{stem}.pom"
        data = json.loads(metadata.read_text())
        ET.parse(pom)
        if module == library:
            published = {variant["available-at"]["module"] for variant in data["variants"] if "available-at" in variant}
            if published != set(modules[1:]):
                raise ValueError(f"Publication targets do not match the requested profile: {sorted(published)}")
        if "kbuild-platform" in metadata.read_text() or "kbuild-platform" in pom.read_text():
            raise ValueError(f"Build platform leaked into {module}")
        for variant in data["variants"]:
            redirect = variant.get("available-at")
            if redirect and not (directory / redirect["url"]).is_file():
                raise ValueError(f"Missing target metadata: {redirect['url']}")
            for artifact in variant.get("files", []):
                path = directory / artifact["url"]
                if not path.is_file() or path.stat().st_size != artifact["size"]:
                    raise ValueError(f"Missing or truncated artifact: {path}")
                if hashlib.sha256(path.read_bytes()).hexdigest() != artifact["sha256"]:
                    raise ValueError(f"Artifact checksum mismatch: {path}")
            for dependency in variant.get("dependencies", []):
                if not any(dependency.get("version", {}).get(key) for key in ("requires", "strictly", "prefers")):
                    raise ValueError(f"Versionless dependency in {module}: {dependency}")
        for classifier in ("sources", "javadoc"):
            with ZipFile(directory / f"{stem}-{classifier}.jar") as archive:
                if not any(name != "META-INF/MANIFEST.MF" and not name.endswith("/") for name in archive.namelist()):
                    raise ValueError(f"Empty {classifier} artifact for {module}")
        if module == library + "-jvm":
            for variant in data["variants"]:
                if (variant.get("attributes", {}).get("org.gradle.category") == "library"
                        and variant["attributes"].get("org.gradle.usage") in ("java-api", "java-runtime")):
                    if variant["attributes"].get("org.gradle.jvm.version") != 17:
                        raise ValueError("Published JVM requirement must remain Java 17")
            with ZipFile(directory / f"{stem}.jar") as archive:
                for name in archive.namelist():
                    if name.endswith(".class") and int.from_bytes(archive.read(name)[6:8], "big") > 61:
                        raise ValueError(f"Bytecode above Java 17: {name}")
    if github:
        checksums = dict(line.split("  ", 1)[::-1] for line in (base / "SHA256SUMS").read_text().splitlines())
        files = {path.name for path in base.iterdir() if path.is_file() and path.name != "SHA256SUMS"}
        if set(checksums) != files:
            raise ValueError("GitHub checksum manifest does not cover every release asset")
        for name, digest in checksums.items():
            if hashlib.sha256((base / name).read_bytes()).hexdigest() != digest:
                raise ValueError(f"GitHub asset checksum mismatch: {name}")
    print(f"Verified {len(modules)} complete publications at {version}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repository")
    parser.add_argument("version")
    parser.add_argument("--full", action="store_true", help="Require the complete release target set")
    parser.add_argument("--github", action="store_true", help="Verify flat GitHub Release assets and their checksum manifest")
    args = parser.parse_args()
    verify(args.repository, args.version, args.full, args.github)
