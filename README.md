# Yellow Ball

A Minecraft companion that emerges from a box, talks with you, and turns into a monster if you mistreat it. [Full feature guide](docs/features.md)

## Install from the release ZIP on macOS

You need **Minecraft Java Edition 26.3**, **Fabric Loader 0.20.1**, and **Fabric API 0.166.2+26.3**. These steps use Minecraft Launcher on an Intel or Apple Silicon Mac.

1. Install **Java 25** for the Fabric Installer. Choose `aarch64` or `arm64` for Apple Silicon, or `x64` or `x86_64` for Intel. The release includes a prebuilt mod. [Install Java on macOS](https://docs.fabricmc.net/players/installing-java/macos)
2. Run Minecraft **26.3** once, then close the game and the Launcher. Download the **Universal/.JAR** Fabric Installer. Select **Client**, Minecraft **26.3**, Loader **0.20.1**, and the option to create a profile. After installation, launch the Fabric profile once and close the game. [Fabric Installer](https://fabricmc.net/use/installer/)
3. Under **Assets**, download and extract `yellow-ball-<version>+mc26.3.zip`. The release ZIP contains the mod, the required Fabric API, and `INSTALL.txt`. GitHub's **Source code** downloads contain the project source. [GitHub Releases](https://github.com/sakharovmaksim/verity-fabric-mod/releases/latest)
4. In the Launcher, open **Installations**, edit your Fabric profile, and find **Game Directory**. Copy **both JARs from the ZIP's `mods` folder** into that profile's `mods` folder. Create it if needed. The default path is `~/Library/Application Support/minecraft/mods`. Use **⌘⇧G** in Finder to open it. If your profile uses a custom game directory, use its `mods` folder. When upgrading, remove the old `echo-lure-*.jar` and any Fabric API version that differs from the bundled version. Copy the two files individually to keep your other mods. [Install Fabric mods](https://docs.fabricmc.net/players/installing-mods)
5. Launch the Fabric profile for **26.3**, open a world, and enter `/yellow status`. When a new story starts, a box containing Yellow Ball appears nearby.

If the command is unavailable, check the Launcher profile, Minecraft version, and `mods` folder. A successful load shows `echo_lure 1.0.16` in `logs/latest.log` inside the game directory.

## Set up Ollama for free-form chat

Install **Ollama** and download the default model, **Gemma 4 E4B**. Ollama supports macOS **14 or later** on Apple Silicon and Intel. On Intel Macs, the model runs on the CPU. [Ollama for macOS](https://docs.ollama.com/macos)

```bash
open -a Ollama
ollama pull gemma4:e4b
```

Keep Ollama running while you play. The model runs locally with no API key or per-request fees. In single-player, run Ollama on the same computer as Minecraft. For a dedicated server, run Ollama on the server. [Gemma 4 in Ollama](https://ollama.com/library/gemma4)

### Change the model

1. After running the mod once, open `config/echo_lure.properties` in your Fabric profile's game directory. In Minecraft Launcher, open **Installations**, edit your Fabric profile, and find **Game Directory**. The default macOS path is `~/Library/Application Support/minecraft/config/echo_lure.properties`. On a dedicated server, look in the server's `config` folder.
2. Replace the tag in `ollama.model=gemma4:e4b` with the model you want. Use `ollama.model=gemma4:e2b` for the smaller Gemma 4 model or `ollama.model=gemma3:4b` to use an existing Gemma 3 installation.
3. If the model is missing, run `ollama pull <tag>` on the computer running Ollama. Save the file and restart Minecraft or the dedicated server.

## Getting started

Type `verity: hi, tell me a short story` in chat to start a conversation. You can also use `Желтый шарик:`, `yellow:`, or `/yellow ask <question>`. Amethyst shards increase trust. Landing regular hits for about 3–4 minutes drives Yellow Ball into a rage. Sometimes it dodges sideways.

Yellow Ball remembers recent exchanges during your play session. Try `verity: let's call my base Lighthouse` followed by `verity: what did we name my base?`.

Try `verity: give me 8 diamonds` or `verity: give me a diamond pickaxe`. Yellow Ball may give you the items or refuse, depending on its mood. Item requests work without Ollama and have a 30-second cooldown.

Try `verity: give me a Silence armor trim` for a smithing template, or `verity: teleport me to 120 70 -35` to move to a safe block in a loaded Overworld chunk. Teleport requests also depend on mood and have their own 30-second cooldown.

Ask for coordinates with `verity: where are the diamonds?`, `verity: найди деревню`, or `/yellow ask find a cherry grove`. Yellow Ball searches the Overworld and posts the coordinates in chat, even with Ollama closed.

After `verity: скажи координаты места с алмазами` finds ore, reply with `verity: перенеси меня туда` to ask for a teleport to a safe spot near it. Teleport odds still depend on mood.

**Right-click Yellow Ball** to pick it up, then **right-click again** to throw it in the direction you're looking. You can also use **G** or `/yellow throw`. If you throw it into lava, it survives, gets angry, and comes back.

**Shift + right-click Yellow Ball** to put it in an empty inventory slot. Hold the resulting item and **right-click in the air** to release it. The story and anger timer pause while it is stored, and trust is saved.

- `/yellow status` shows the current state and anger level.
- `/yellow stop` turns off the story.
- `/yellow start` restarts the story after it has been stopped.

## Build from source

Install Python 3 and JDK 25, follow the Minecraft and Fabric setup steps, and download Fabric API. Open a terminal in the repository root and run these commands. [Python for macOS](https://www.python.org/downloads/macos/)

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
python3 build.py --fabric-api "$HOME/Library/Application Support/minecraft/mods/fabric-api-0.166.2+26.3.jar"
```

If Fabric API is elsewhere, pass its full path to `--fabric-api`. For a different Launcher installation directory, add `--minecraft-dir "/path/to/minecraft"`. The installation directory contains `versions` and `libraries` and may be separate from the profile's game directory. The build produces `build/echo-lure-1.0.16+mc26.3.jar`.

## Publish a release

Update `version` in `src/main/resources/fabric.mod.json`, commit the changes, and create and push a matching tag.

```bash
git tag v1.0.15
git push origin v1.0.15
```

CI builds the project and publishes a GitHub Release for the tag. The release includes the installation ZIP and its SHA-256 checksum. The tag must match the mod version, with an optional `v` prefix. Tags containing a hyphen are published as prereleases. CI also checks builds on pull requests, pushes to `main` or `master`, and manual workflow runs. Publishing requires a tag.

To build the ZIP without a local Minecraft installation, you need Python 3, JDK 25, and an internet connection.

```bash
python3 -m unittest discover -s tests -v
python3 scripts/prepare_build.py
python3 build.py --minecraft-dir build/minecraft
python3 scripts/package_release.py --tag v1.0.15
```

The ZIP is written to `build/release/`. The build downloads dependencies from the official Mojang and Fabric sources and verifies their checksums. Minecraft is installed separately.
