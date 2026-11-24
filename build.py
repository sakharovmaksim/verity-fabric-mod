#!/usr/bin/env python3
"""Build against the installed Minecraft Launcher files using Java 25."""
import argparse
import json
import os
import shutil
import subprocess
import tempfile
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parent
DEFAULT_GAME = Path.home() / "Library/Application Support/minecraft"
VERSION = "26.3"
LOADER_VERSION = "0.20.1"
PROFILE = f"fabric-loader-{LOADER_VERSION}-{VERSION}"
API_VERSION = "0.166.2+26.3"


def read_json(file):
    return json.loads(file.read_text(encoding="utf-8"))


def library_jars(home, manifest, *, require_all=False):
    for library in manifest["libraries"]:
        artifact = library.get("downloads", {}).get("artifact", {})
        path = artifact.get("path")
        if not path:
            group, name, version = library["name"].split(":")
            path = f"{group.replace('.', '/')}/{name}/{version}/{name}-{version}.jar"
        jar = home / "libraries" / path
        if jar.is_file():
            yield jar
        elif require_all:
            raise FileNotFoundError(jar)


def java_bin(home):
    if os.environ.get("JAVA_HOME"):
        return Path(os.environ["JAVA_HOME"]) / "bin"
    component = next(
        (home / "runtime").glob("java-runtime-epsilon/mac-os-*/java-runtime-epsilon/jre.bundle/Contents/Home/bin/javac"),
        None,
    )
    if component:
        return component.parent
    javac = shutil.which("javac")
    if javac:
        return Path(javac).parent
    raise FileNotFoundError("Java 25 JDK: set JAVA_HOME or run Minecraft 26.3 in Minecraft Launcher")


def run():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--minecraft-dir", type=Path, default=DEFAULT_GAME)
    parser.add_argument("--fabric-api", type=Path, help="path to Fabric API 0.166.2+26.3")
    args = parser.parse_args()
    home = args.minecraft_dir.expanduser().resolve()
    api = args.fabric_api
    if api is None:
        filename = f"fabric-api-{API_VERSION}.jar"
        candidates = [home / "mods" / filename, home.with_name("minecraft-verity-26.3") / "mods" / filename]
        api = next((file for file in candidates if file.is_file()), candidates[0])
    version_dir = home / "versions" / VERSION
    game = version_dir / f"{VERSION}.jar"
    manifest = read_json(version_dir / f"{VERSION}.json")
    fabric = read_json(home / "versions" / PROFILE / f"{PROFILE}.json")
    if not game.is_file():
        raise FileNotFoundError(game)

    binaries = java_bin(home)
    output = ROOT / "build"
    output.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output) as work_dir, ZipFile(api.expanduser()) as fabric_api:
        work = Path(work_dir)
        metadata = json.loads(fabric_api.read("fabric.mod.json"))
        if metadata["id"] != "fabric-api" or metadata["version"] != API_VERSION:
            raise ValueError(f"Expected Fabric API {API_VERSION}")
        modules = [Path(fabric_api.extract(entry["file"], work)) for entry in metadata["jars"]]
        dependencies = [game, *library_jars(home, manifest), *library_jars(home, fabric, require_all=True), *modules]
        classes = work / "classes"
        sources = sorted((ROOT / "src/main/java").rglob("*.java"))
        subprocess.run([
            binaries / "javac", "--release", "25", "-encoding", "UTF-8",
            "-classpath", os.pathsep.join(map(str, dependencies)), "-d", classes, *sources,
        ], check=True)
        shutil.copytree(ROOT / "src/main/resources", classes, dirs_exist_ok=True)
        mod_version = read_json(classes / "fabric.mod.json")["version"]
        name = f"echo-lure-{mod_version}+mc{VERSION}.jar"
        jar = work / name
        subprocess.run([
            binaries / "jar", "--create", "--file", jar,
            "--date=2026-09-15T00:00:00Z", "-C", classes, ".",
        ], check=True)
        jar.replace(output / name)
    print(output / name)


if __name__ == "__main__":
    try:
        run()
    except subprocess.CalledProcessError as error:
        raise SystemExit(f"{Path(error.cmd[0]).name} failed with exit code {error.returncode}") from error
    except (OSError, ValueError, KeyError) as error:
        raise SystemExit(f"Build failed: {error}") from error
