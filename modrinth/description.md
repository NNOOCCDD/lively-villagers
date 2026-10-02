# Lively Villagers

**Every villager gets a name, a personality and something to say.** They greet you as you walk past, react to what you build, thank you for gifts, panic when monsters show up and yawn their way to bed at night.

No new blocks, items or textures: just vanilla villages that feel like people actually live there.

<!-- Paste your YouTube embed here once the showcase video is up:
<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/VIDEO_ID" title="Lively Villagers showcase" allowfullscreen></iframe>
-->

## ✨ Features

### 💬 Speech bubbles
Villagers talk with little bubbles above their heads. Everyone nearby sees them, no resource pack needed.

### 🎭 Personalities
Every villager is **cheerful**, **curious**, **shy** or **grumpy**, and it shows in what they say and how often they say it. Name tags rename them in speech too.

### 👋 Greetings
Walk past a villager and they'll say hi. Villagers who like you greet you warmly; the ones you've wronged just say *"Hmph."* What they say also changes with the time of day and the weather.

### 🧱 Reactions to your building
- They **love** their own workstation: *"A new lectern! Just what I needed!"*
- They **like** flowers, lanterns, beds, crops, banners and bookshelves.
- They **dislike** skulls, cobwebs, magma and wither roses.
- They're **terrified** of TNT.
- Build right on top of them and they'll tell you to watch it. Break their bed, bell or workstation and they'll complain.

### 🎁 Gifts
Throw flowers, cookies or berries near a villager (cake, pumpkin pie and honey are favourites). They walk over, hold the gift for a moment and thank you with hearts. **Your first gift to a villager each Minecraft day raises your reputation**, which also lowers their trade prices.

### 🧟 Danger
Villagers now also fear **creepers, skeletons, spiders, witches, blazes, wardens and withers**, not just zombies and illagers. They run from lit TNT and hissing creepers, shout while they flee (*"Creeper! RUN!"*), and calm down afterwards.

### 👆 Poke them
- Jobless villagers grumble: *"Stop touching me!"*
- Traders greet you like a customer before the trade screen opens.
- Sneak + right-click with an empty hand and they introduce themselves.
- Poke a sleeping villager and they talk in their sleep.

### ⚔️ Raids
- The first villager to spot a raid shouts a warning (*"Ring the bell! RING THE BELL!"*) and sends the neighbours running to hide, just like the village bell.
- Villagers cower with raid lines (*"They're at the door!"*, *"\*hides under the bed\*"*) and beg you for help.
- Win the raid and the village cheers (*"Three cheers for the hero!"*). Lose it and they mourn.

### 🪦 Last words
Sometimes a dying villager gets some last words, and the bubble stays where they fell. *"Why... Steve?"*, *"Curse you... pillagers..."*, or a grumpy *"Typical."*

### 🌙 Bedtime
At sunset villagers get sleepy: *"Time to turn in for the night."* Their greetings and shop lines turn into yawns.

## 🛠️ Installation

1. Install **Fabric Loader** for Minecraft **1.21.1**.
2. Put **[Fabric API](https://modrinth.com/mod/fabric-api)** and **Lively Villagers** in your `mods` folder.
3. Launch and go say hi to a villager.

**Server-side:** on a server, only the server needs the mod. Players can join with a vanilla client. It also works in singleplayer and LAN worlds.

## ⚙️ Commands & config

| Command | What it does |
|---|---|
| `/lively info` | Name, personality and your reputation for the villager you're looking at |
| `/lively personality <cheerful\|curious\|shy\|grumpy>` | Change a villager's personality (operators) |
| `/lively reload` | Reload the config file (operators) |

`config/lively-villagers.json` is created on first launch. You can switch each feature on or off and tune the greeting range, cooldowns, chattiness and gift reputation.

## 📝 Good to know

- Villagers only pick up gifts when the `mobGriefing` game rule is on (the same as vanilla bread and seeds).
- Built to stay quiet where it should: villagers only talk with a player nearby, iron-farm villagers stop crying out after a couple of shouts, and trading-hall villagers in 1x1 cells don't greet you.
- Because villagers now fear more mobs, a group of scared villagers can summon an iron golem for creepers and skeletons too, which may affect some iron farm designs.
- Found a bug or have an idea for a villager line? Let me know in the comments or issues.

## ❤️ Credits

- Built on [Fabric](https://fabricmc.net/) and Fabric API.
- Developed with the help of AI (Claude Code by Anthropic, using the Universal Modder plugin).
