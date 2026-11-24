#!/usr/bin/env python3
"""Create a ready-to-copy mods/ ZIP and its SHA-256 checksum."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build


def release_version(tag=None):
    version = build.read_json(build.ROOT / "src/main/resources/fabric.mod.json")["version"]
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:-[a-zA-Z0-9.-]+)?", version):
        raise ValueError(f"Invalid mod version: {version}")
    if tag is not None and tag not in (version, "v" + version):
        raise ValueError(f"Tag {tag!r} must match fabric.mod.json: v{version} or {version}")
    return version


def jar_metadata(path, mod_id, version):
    with ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise ValueError(f"Corrupt JAR: {path}")
        metadata = json.loads(archive.read("fabric.mod.json"))
        if metadata["id"] != mod_id or metadata["version"] != version:
            raise ValueError(f"Unexpected mod/version in {path}")


def package(mod, api, destination, version):
    jar_metadata(mod, "echo_lure", version)
    jar_metadata(api, "fabric-api", build.API_VERSION)
    files = {
        f"mods/echo-lure-{version}+mc{build.VERSION}.jar": mod.read_bytes(),
        f"mods/fabric-api-{build.API_VERSION}.jar": api.read_bytes(),
    }
    with ZipFile(api) as archive:
        files["LICENSES/Fabric-API.txt"] = archive.read("LICENSE-fabric-api")
    files["INSTALL.txt"] = f"""Yellow Ball {version} for Minecraft Java {build.VERSION}

You need Fabric Loader {build.LOADER_VERSION} for Minecraft {build.VERSION}.
This archive works on macOS, Windows, and Linux. Install Minecraft and Fabric Loader separately.

1. Close Minecraft. Find your Fabric profile's Game Directory in Minecraft Launcher.
   On macOS, the default is ~/Library/Application Support/minecraft.
2. When upgrading, remove the old echo-lure-*.jar from the mods folder.
   Remove any Fabric API version that differs from the bundled version.
3. Copy both JARs from the archive's mods folder into your game's mods folder.
   Create the folder if needed. Copy the two files individually to keep your other mods.
4. Launch the Fabric profile for Minecraft {build.VERSION}, open a world, and run /yellow status.

For free-form chat, install Ollama and run ollama pull gemma4:e4b to download the model.
Keep Ollama running while you play. To use a different model, edit the tag in
config/echo_lure.properties in the game directory and restart Minecraft.
Item requests such as "verity: give me 8 diamonds" work without Ollama.

Right-click to pick up or throw Yellow Ball. Shift + right-click to store it in your inventory.
Hold the Yellow Ball item and right-click in the air to release it.
To start a conversation, type "verity: hi!" in chat.

Full instructions are in the project README.
https://github.com/sakharovmaksim/verity-fabric-mod#readme
""".encode("utf-8")
    checksums = "".join(f"{hashlib.sha256(data).hexdigest()}  {name}\n" for name, data in sorted(files.items()))
    files["SHA256SUMS.txt"] = checksums.encode("ascii")
    destination.mkdir(parents=True, exist_ok=True)
    output = destination / f"yellow-ball-{version}+mc{build.VERSION}.zip"
    temporary = output.with_suffix(".zip.tmp")
    with ZipFile(temporary, "w", compression=ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(files.items()):
            entry = ZipInfo(name, date_time=(2026, 9, 15, 0, 0, 0))
            entry.compress_type = ZIP_DEFLATED
            entry.create_system = 3
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, data)
    temporary.replace(output)
    checksum = output.with_suffix(".zip.sha256")
    checksum.write_text(f"{hashlib.sha256(output.read_bytes()).hexdigest()}  {output.name}\n", encoding="ascii")
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", help="optional v-prefixed version, must match fabric.mod.json")
    parser.add_argument("--check-tag", action="store_true", help="validate version without packaging")
    parser.add_argument("--fabric-api", type=Path,
                        default=build.ROOT / "build/minecraft/mods" / f"fabric-api-{build.API_VERSION}.jar")
    parser.add_argument("--output-dir", type=Path, default=build.ROOT / "build/release")
    args = parser.parse_args()
    version = release_version(args.tag)
    if args.check_tag:
        print(f"Release version: {version}")
        return
    mod = build.ROOT / "build" / f"echo-lure-{version}+mc{build.VERSION}.jar"
    print(package(mod, args.fabric_api.expanduser(), args.output_dir, version))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError) as error:
        raise SystemExit(f"Release packaging failed: {error}") from error
