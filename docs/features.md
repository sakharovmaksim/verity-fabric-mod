# Echo Lure features for Yellow Ball

This guide covers version **1.0.15** for Minecraft Java Edition **26.3**, Fabric Loader **0.20.1**, and Fabric API **0.166.2+26.3**.

Yellow Ball is a flying companion you can talk to as its trust in you changes and a horror story gradually unfolds. Each world saves story progress and trust separately for each player. [Installation instructions](../README.md)

## First appearance

When the story begins, a closed cardboard box appears near the player. It shakes slightly before its four flaps open. About eight seconds in, Yellow Ball rises slowly out of the box and starts talking. The empty box disappears a few seconds later.

The box needs clear space on solid ground within a few blocks of the player. If there is no suitable spot, the intro waits for one. The box is a separate entity and frees up the space when it disappears.

The intro plays once per player for the current story progression. Once completed, it stays completed after you rejoin or die. If you leave during the intro, it resumes from the saved time. To restart the story, run `/yellow stop` followed by `/yellow start`. With cheats enabled, you can also replay the intro with `/yellow demo waiting`.

## Appearance and expressions

In its friendly form, Yellow Ball is a small matte yellow sphere with dark eyes and a smile. Its face turns toward the camera. It follows its owner and catches up as they move.

| Expression | When it appears |
| --- | --- |
| Calm | The companion's default expression |
| Blink | Periodically while idle |
| Speaking | Its mouth opens and closes as it replies |
| Pleased | When given an amethyst shard |
| Startled | When struck while trust is above the rage threshold |
| Uneasy | During the Static phase |
| Angry | When trust reaches the rage threshold, just before the transformation |

The monster has its own 3D model with a hunched body, long arms, an asymmetrical face, and a moving jaw. It has animations for moving, swaying its head, and opening its mouth.

## Conversation

Open Minecraft chat and start a message with `verity:`, followed by a question or anything you want to say. You can also address the companion with `Желтый шарик:` or `yellow:`. The prefixes are case-insensitive and also accept a comma. Replies go to the player who sent the message.

```text
verity: hi, tell me a short story
Желтый шарик: hi! how are you?
verity: suggest a name for my cat
yellow: tell me a short story about an abandoned mine
verity: my cat's name is Milo, remember that
verity: what's my cat's name?
```

You can also use `/yellow ask <question>`.

## Picking up and throwing

Stand within three blocks of your companion and **right-click it** to hold it in front of you. **Right-click again** to throw it in the direction you're looking, even when aiming at a block or empty air. Release and press the button again after picking it up. While you are holding Yellow Ball, right-click is used to throw it and takes priority over eating or placing blocks.

Right-click a free-flying companion with an amethyst shard to give it the shard. **G** and `/yellow throw` also pick up or throw the companion. You can rebind G in the Controls settings.

Yellow Ball travels in an arc, collides with blocks, and can land in water or lava. It starts following its owner again after about five seconds. It survives lava, but gets startled, complains about the heat, and loses two points of trust on the shared three-second cooldown. Throwing it on its own has no effect on trust. Lava builds anger at the same rate as regular hits.

You can pick up your own companion in its normal form when you have a clear line of sight. Carrying ends when the phase changes, the story stops, or the play session ends.

### Storing Yellow Ball in your inventory

**Shift + right-click your companion** to put it in a free inventory slot. This also works while you are holding it in front of you. You can use `/yellow store` when standing nearby. Storage is available in the Contact and Static phases while the companion is in its normal form. If your inventory is full, Yellow Ball stays in the world.

The pocket companion appears as an item with a tooltip. Hold it in either hand and **right-click in the air** to release it nearby. This consumes the item even in Creative mode. If there is no room for the companion to spawn, the item stays in your inventory.

While Yellow Ball is stored, it stays out of the world, the anger bar is hidden, and the story and anger timers pause. Trust and elapsed time carry over when you rejoin or respawn. Storing the companion clears conversation history. Chat and item requests resume when you release it. The item request cooldown keeps running on world time.

The item is tied to its owner. You can keep it in a chest. If you lose it or leave it elsewhere, `/yellow release` brings your companion back nearby and invalidates the old item. Copies cannot spawn extra companions. To release Yellow Ball, the story must be enabled, you must be in the Overworld, and there must be space nearby.

Running `/yellow stop` keeps Yellow Ball stored. Running `/yellow start` enables the story again with its saved progress. A demo command cancels storage and removes the matching item from the owner's inventory.

### Free-form chat through Ollama

An **Ollama** model answers open-ended questions during the Contact and Static phases. The default model is **Gemma 4 E4B**. You can chat in Russian or English, ask for stories and Minecraft tips, or discuss other topics. The model is prompted to reply briefly in the player's language. Its replies become more mysterious during Static.

- The model runs locally with no API key or per-request fees. Start Ollama and download your chosen model before playing. To download the default model, run `ollama pull gemma4:e4b`.
- Each request contains the question, up to the last six exchanges of free-form chat, the current phase, trust, the player's coordinates, and whether it is light outside.
- Conversation history is kept in memory separately for each player. It clears when you log out, die, sleep, change dimensions, stop the story, or select a demo phase.
- The game keeps running while Yellow Ball displays “Thinking…” (`Думаю…`). Its speaking animation plays when the answer arrives.
- Questions can be up to 500 characters, with about five seconds between questions. There can be one active model request per player and up to two per server.
- Requests time out after 60 seconds. If the model is unavailable, the mod asks you to check Ollama and download the configured model tag.

Ollama generates text replies. The mod handles movement, ore search, item requests, trust, and the transformation. During Rage, Watching, and Hunt, Yellow Ball responds with short lines for that phase.

Ollama must be reachable at `http://127.0.0.1:11434/api/chat`. In single-player, this is the player's computer. On a dedicated server, this is the server itself.

On first launch, the mod creates `config/echo_lure.properties` with `ollama.model=gemma4:e4b`. The file lives in the Fabric profile's game directory in single-player and in the server's `config` folder on a dedicated server. To switch models, update the tag in `ollama.model`, download that model in Ollama, and restart the game or server. The mod uses the default model if the setting is missing or blank or if it cannot read the file. [How to change the model](../README.md#change-the-model)

### Help based on the world

During Contact and Static, the mod handles several kinds of questions directly.

| Example request | What it does |
| --- | --- |
| `verity: where are the diamonds?` | Finds the nearest diamond ore or deepslate diamond ore within 16 blocks |
| `verity: where is the iron?` | Finds the nearest iron ore or deepslate iron ore within the same radius |
| `verity: what are my coords?` | Shows your current coordinates |
| `verity: what time is it?` | Tells you whether it is light outside |
| `verity: 17 * 6` | Evaluates a simple arithmetic expression |

Ore search uses loaded chunks. If it finds no nearby ore, Yellow Ball suggests a Y level to search. Arithmetic supports addition, subtraction, multiplication, and division of two integers, including negative numbers. These answers also work with Ollama closed.

## Requesting resources and items

If Yellow Ball agrees to an item request, the mod creates the items and adds them to your inventory. Requests work in Survival without Ollama or cheats, and you can ask for items you have yet to find or mine.

```text
verity: give me 8 diamonds
verity: please give me 16 iron ingots
verity: can you give me a diamond pickaxe?
verity: bring me a stack of oak planks
verity: give me minecraft:oak_log 4
verity: give me 12 diamonds please
```

The mod supports Russian and English Minecraft 26.3 item names, common word forms, and exact registered item IDs. If it cannot recognize the item, Yellow Ball asks you to clarify. In Russian, `железо` means iron ingots. `wood` refers to oak logs and `food` refers to bread. Requests for advice or stories stay in ordinary chat, and asking where diamonds are still looks for ore.

In Contact, Yellow Ball has a **75%** chance of agreeing at neutral trust and a **95%** chance at maximum trust. The chance starts at 75%, changes by 2.5 percentage points per trust point, and drops by another 15 points during Static. The result is capped to **10–95%**. Yellow Ball refuses all requests during Rage, Watching, and Hunt. Hits and lava lower trust, while amethyst gifts raise it.

You can request one item type and at most one stack at a time, such as 64 diamonds or one pickaxe. If you omit the quantity, you get one item. The mod accepts digits, `stack`, and Russian quantity words such as `два` (two), `восемь` (eight), `стак` (stack), and `полстака` (half a stack). An accepted request or a refusal based on mood starts a **30-second cooldown in game time** that carries over when you rejoin or die. Yellow Ball tells you when it caps a request at one stack. If it agrees but your inventory lacks space, it grants nothing and starts no new cooldown.

The result becomes part of the current conversation history. The mod grants items only to the player who requested them. This feature handles item grants, with no support for commands, world changes, enchantments, or custom item data.

## Trust and provocation

You can change Yellow Ball's trust during Contact and Static. It starts at **0** and ranges from **−120** to **+12**.

| Action | Trust change | Reaction |
| --- | --- | --- |
| Right-click with an amethyst shard in your main hand | +2 | Pleased expression and an amethyst sound |
| Land a hit on your own companion | −2 | Startled expression or a transition to Rage |
| The companion stays in lava after a throw | −2 every three seconds | Startled expression and a complaint about the heat |

Trust changes share a cooldown of about three seconds. This cooldown carries over when you leave the world. Giving a gift consumes one shard in Survival. In Creative mode, you keep the shard. At maximum trust, Yellow Ball asks you to keep the gift. Only the owner's actions count toward trust.

When flying freely, Yellow Ball sometimes dodges an attempted hit by moving about half a block sideways. It needs a clear path to at least one side, and at least three seconds pass between dodges. A successful dodge leaves trust unchanged.

A hit that brings trust to **−120** triggers Rage. Starting from neutral trust, this takes 60 counted hits. Landing a hit every three seconds leads to the transformation in about three minutes. Landing one every four seconds takes about four minutes. Gifts raise the number of hits needed, and longer pauses allow anger to fade. Rapid clicks within the three-second cooldown leave anger unchanged.

In Rage, Yellow Ball shows an angry face, gives a warning, and enters the Hunt phase after about **2–3 seconds of active play**. If there is room, the monster appears where Yellow Ball was. Otherwise, the mod searches for a suitable spot near the player. The world saves the Rage state and countdown with the rest of the story progress.

## Anger bar and return to normal

Each player sees their own Yellow Ball bar at the top of the screen. It updates once per second and immediately after a gift or a counted hit.

| State | What the bar shows |
| --- | --- |
| Box | Time until Yellow Ball emerges |
| Normal companion | Anger from 0% to 100% and the estimated time until Rage under continued mistreatment |
| Rage | 100% anger and a short countdown to the transformation |
| Watching | Time until the story triggers the transformation |
| Monster | A shrinking red bar and the time until Yellow Ball returns to normal |

At neutral trust, a counted hit adds about 1.7% anger. The bar rounds to the nearest whole percent. The time estimate assumes one hit every three seconds and changes with trust. Gifts increase trust, letting Yellow Ball tolerate more hits. Before the Rage threshold is reached, negative trust recovers by one point for every ten seconds without another hit or gift. For example, anger from one hit fades after 20 seconds.

The monster form lasts **60 seconds of active play**. The hunter then disappears, the normal companion returns, and trust resets to zero. After an early Hunt caused by provocation, the story returns to Contact. After a later Hunt, it returns to Static. The countdown to later phases resumes from that point in the story.

Logging out, dying, sleeping, or changing dimensions pauses the cooldown. The world saves it with the rest of the progress. Turning off the story hides the bar. `/yellow status` also shows the current anger and countdown.

## Story progression

The timer starts automatically. It advances while the story is enabled and the player is alive, awake, in the Overworld, and outside Spectator mode. Sleeping, dying, and visiting other dimensions pause the timer. Check it with `/yellow status`.

| Phase | Starts at zero trust | What happens |
| --- | --- | --- |
| Waiting | When you enter the world | The box appears nearby, shakes, and opens |
| Contact | About 8 seconds | The companion rises out of the box, and chat and interactions become available |
| Static | About 8 minutes | The companion looks uneasy, its lines change, and sounds play periodically |
| Watching | About 20 minutes | A watcher makes brief appearances in place of the companion |
| Hunt | About 40 minutes, or after Rage | The monster pursues the player, with particles and a Darkness effect |

Trust shifts when Watching and Hunt normally start. For this calculation, trust is capped between −12 and +12, with a shift of 15 seconds per point. Positive trust delays those phases, and negative trust brings them forward. The maximum shift is three minutes. Gifts and hits have no effect on trust during Rage, Watching, or Hunt. Returning from monster form resets trust to zero.

The watcher appears for about eight seconds. The hunter can remain until the one-minute countdown ends if it is alive and close to the player. If it disappears or dies early, another encounter can occur during the same Hunt. The Darkness effect lasts five seconds at the start of a chase.

The hunter can attack in Survival or Adventure on difficulties above Peaceful, provided there is room to spawn. In Peaceful or Creative, the Hunt still plays sound events. Each hunter attacks only its assigned player. You can injure and kill the monster, and the story can continue with further encounters.

## Commands

Commands apply to the player who runs them.

| Command | What it does |
| --- | --- |
| `/yellow status` | Shows the current phase, elapsed time, and trust |
| `/yellow ask <question>` | Asks the companion a question |
| `/yellow throw` | Picks up your nearby companion or throws it in the direction you're looking if you are already holding it |
| `/yellow store` | Stores your nearby companion in your inventory while it is in its normal form |
| `/yellow release` | Releases your stored companion, even if you have lost the item |
| `/yellow stop` | Turns off the story, removes the current character, and clears chat history |
| `/yellow start` | Restarts a stopped story from the starting time and trust |
| `/yellow demo waiting` | Resets progress to Waiting |
| `/yellow demo guide` | Starts Contact with the companion |
| `/yellow demo static` | Starts Static |
| `/yellow demo anger` | Shows the angry face and starts the transformation countdown |
| `/yellow demo watch` | Starts the watcher's appearances |
| `/yellow demo hunt` | Starts the Hunt |

`/yellow demo` requires cheats or operator permissions. Choosing a demo phase overwrites current story progress and clears conversation history. The other commands are available to regular players. If the story is already enabled, `/yellow start` reports its current status.

## Saved progress

The world saves whether the story is enabled, elapsed time, trust, the transformation state, and the remaining cooldown. This data carries over when you respawn. Once you return to active play, the companion or monster appears according to the saved phase. Free-form conversation history starts over when the play session ends.
