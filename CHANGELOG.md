# Changelog

## 1.0.1 (Minecraft 1.21.1, Fabric)

### New: raids
- **Raid alarm:** the first villager to spot a raid shouts a warning (*"Ring the bell! RING THE BELL!"*) and sends nearby villagers running to hide, just like the village bell does.
- **Raid panic lines:** villagers cower with raid-specific lines (*"They're at the door!"*, *"\*hides under the bed\*"*, *"Not my crops!"*).
- **Pleas for help:** villagers near you during a raid beg you to save them (*"Steve! Please, save us!"*).
- **Victory and defeat:** win the raid and the village cheers (*"Three cheers for the hero!"*); lose it and they mourn (*"Our poor village..."*).

### New: last words
- Sometimes a dying villager says some last words, and the bubble stays where they fell. Killed by a player: *"Why... Steve?"* By a raider: *"Curse you... pillagers..."* Otherwise anything from *"Avenge me!"* to the grumpy *"Typical."*

### Fixes
- **Gifts:** a villager takes one gift at a time. Throwing a stack no longer has the whole stack eaten in seconds.
- **Gifts:** only items a player threw count. Flowers from flower farms and item collectors are left alone, and villagers no longer walk over to them.
- **Iron farms:** a villager stuck in a long panic (like the zombie in an iron farm) cries out a couple of times and then stays quiet.
- **Performance:** villagers only talk when a player is within 32 blocks, so farms in spawn chunks or chunk-loaded bases don't create speech bubbles all day.
- **Trading halls:** villagers boxed into a 1x1 cell don't greet you or complain about blocks placed next to them, and one player gets at most one greeting every 1.5 seconds.
- **Weather:** a daytime thunderstorm no longer makes villagers yawn and say goodnight.
- **Greetings:** invisible and sneaking players are not greeted.
- **Release jar:** development and recording tools are no longer included.

### New: commands and config
- `/lively reload` (operators) reloads `config/lively-villagers.json` without restarting.
- New options: `raidLines`, `raidAlarm`, `deathLineChance`.

## 1.0.0 (Minecraft 1.21.1, Fabric)

First release. Every villager gets a name, a personality and something to say.

### Personalities and voices
- Every villager is given a name and one of four personalities: **cheerful**, **curious**, **shy** or **grumpy**.
- Villagers talk through speech bubbles above their heads, with lines that change with personality, job, time of day and how much they like you.

### Greetings
- Walk past a villager (within 7 blocks) and they may say hi. Cheerful villagers always do; shy and grumpy ones about half the time.
- Villagers you've helped greet you warmly; villagers who dislike you shake their heads and say "Hmph."
- At night they're sleepy: "I'm so tired...", "Time to turn in for the night."
- When their schedule turns to bedtime, some announce it, but only if a player is nearby to hear.

### Right-clicking
- Villagers without a job (and nitwits) grumble: "Hey...", "Stop touching me!"
- Villagers with a job greet you with a shop line before trading opens: "Hello! Welcome to my shop!"
- Sneak + right-click with an empty hand: the villager introduces themselves.
- Poke a sleeping villager and they talk in their sleep: "Zzz... five more minutes..."

### Reactions to blocks
- They love their own workstation, and villagers without a job want any workstation.
- They like flowers, lanterns, beds, crops, bookshelves and banners.
- They dislike skulls, cobwebs, magma and wither roses, and are scared of TNT.
- They tell you off for building right next to them, and complain if you break their workstation, bed or bell.

### Gifts
- Throw flowers, cookies or berries near a villager (cake, pumpkin pie or honey count as favourites). They walk over, hold the gift for a moment, and thank you with hearts.
- Your first gift to a villager each Minecraft day raises your reputation with them, which also lowers their prices.

### Danger
- Villagers now also fear creepers, skeletons, spiders, witches, blazes, wardens and withers, not just zombies and illagers.
- They run from lit TNT and hissing creepers, shout when they panic ("Creeper! RUN!"), and calm down afterwards ("Phew... that was close.").

### Commands and config
- `/lively info`: name, personality and your reputation for the villager you're looking at.
- `/lively personality <type>` (operators): change a villager's personality. Name tags rename them in speech too.
- `config/lively-villagers.json`: turn each feature on or off, and set the greeting range, cooldowns, chattiness and gift reputation.

### Good to know
- Only the server needs the mod. Players can join with an unmodded game, and it also works in singleplayer and in worlds you open to LAN.
- Requires Fabric API.
- Villagers only pick up gifts when the `mobGriefing` game rule is on, the same as vanilla bread and seeds.
- Because villagers now fear more mobs, a group of scared villagers can call an iron golem for creepers and skeletons too.
