#!/usr/bin/env python3
"""Download the official compile dependencies into a Launcher-compatible directory."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
from http.client import HTTPException
import json
from pathlib import Path
import re
import sys
import time
from urllib.error import URLError
from urllib.request import Request, urlopen

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build

MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
FABRIC_MAVEN = "https://maven.fabricmc.net/"
DEFAULT_HOME = build.ROOT / "build/minecraft"


def fetch(url):
    for attempt in range(3):
        try:
            request = Request(url, headers={"User-Agent": "yellow-ball-build/1"})
            with urlopen(request, timeout=30) as response:
                return response.read()
        except (URLError, TimeoutError, HTTPException, ConnectionError):
            if attempt == 2:
                raise
            time.sleep(attempt + 1)


def verify(data, hashes, source):
    if not hashes:
        raise ValueError(f"No checksum for {source}")
    for algorithm, expected in hashes.items():
        if hashlib.new(algorithm, data).hexdigest() != expected.lower():
            raise ValueError(f"{algorithm} mismatch: {source}")


def download(url, target, hashes):
    if target.is_file():
        try:
            verify(target.read_bytes(), hashes, target)
            return
        except ValueError:
            pass
    data = fetch(url)
    verify(data, hashes, url)
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(target.suffix + ".downloading")
    temporary.write_bytes(data)
    temporary.replace(target)


def sha256(url):
    digest = fetch(url + ".sha256").decode("ascii").strip().split()[0]
    if not re.fullmatch(r"[a-fA-F0-9]{64}", digest):
        raise ValueError(f"Invalid checksum: {url}")
    return {"sha256": digest}


def maven_path(name):
    group, artifact, version, *classifier = name.split(":")
    suffix = "-" + classifier[0] if classifier else ""
    return f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}{suffix}.jar"


def prepare(home):
    manifest = json.loads(fetch(MANIFEST))
    version = next((v for v in manifest["versions"] if v["id"] == build.VERSION), None)
    if version is None:
        raise ValueError(f"Minecraft {build.VERSION} is missing from the official manifest")
    metadata_path = home / "versions" / build.VERSION / f"{build.VERSION}.json"
    download(version["url"], metadata_path, {"sha1": version["sha1"]})
    metadata = build.read_json(metadata_path)
    client = metadata["downloads"]["client"]
    dependencies = [(client["url"], metadata_path.with_suffix(".jar"), {"sha1": client["sha1"]})]
    # Assets, game installation and native libraries are unnecessary for javac.
    for library in metadata["libraries"]:
        artifact = library.get("downloads", {}).get("artifact")
        if artifact and ":natives-" not in library["name"]:
            dependencies.append((artifact["url"], home / "libraries" / artifact["path"],
                                 {"sha1": artifact["sha1"]}))

    profile_url = f"https://meta.fabricmc.net/v2/versions/loader/{build.VERSION}/{build.LOADER_VERSION}/profile/json"
    profile = json.loads(fetch(profile_url))
    if profile["id"] != build.PROFILE or profile["inheritsFrom"] != build.VERSION:
        raise ValueError("Unexpected Fabric profile")
    profile_path = home / "versions" / build.PROFILE / f"{build.PROFILE}.json"
    profile_path.parent.mkdir(parents=True, exist_ok=True)
    profile_path.write_text(json.dumps(profile), encoding="utf-8")
    for library in profile["libraries"]:
        relative = maven_path(library["name"])
        url = library.get("url", FABRIC_MAVEN).rstrip("/") + "/" + relative
        hashes = {name: library[name] for name in ("sha256", "sha1") if name in library}
        dependencies.append((url, home / "libraries" / relative, hashes or sha256(url)))

    api_name = f"fabric-api-{build.API_VERSION}.jar"
    api_url = f"{FABRIC_MAVEN}net/fabricmc/fabric-api/fabric-api/{build.API_VERSION}/{api_name}"
    dependencies.append((api_url, home / "mods" / api_name, sha256(api_url)))
    with ThreadPoolExecutor(max_workers=6) as executor:
        futures = [executor.submit(download, *dependency) for dependency in dependencies]
        for future in futures:
            future.result()
    print(f"Verified {len(dependencies)} dependencies in {home}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--minecraft-dir", type=Path, default=DEFAULT_HOME)
    args = parser.parse_args()
    prepare(args.minecraft_dir.expanduser().resolve())


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, HTTPException) as error:
        raise SystemExit(f"Dependency preparation failed: {error}") from error
