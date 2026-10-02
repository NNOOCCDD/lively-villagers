## 1.1.0: NeoForge support, raids & last words

### 🆕 NeoForge
- Lively Villagers now runs on **NeoForge 1.21.1** as well as Fabric, with the same features. Grab the jar for your loader.
- Still server-side on both loaders: only the server needs it, players can join without it.

### ⚔️ Raids
- **Raid alarm:** the first villager to spot a raid shouts a warning (*"Ring the bell! RING THE BELL!"*) and sends the neighbours running to hide, just like the village bell.
- **Raid panic:** villagers cower with raid lines (*"They're at the door!"*, *"\*hides under the bed\*"*, *"Not my crops!"*).
- **Pleas for help:** villagers near you beg you to save them (*"Steve! Please, save us!"*).
- **Victory or defeat:** win and the village cheers (*"Three cheers for the hero!"*); lose and they mourn (*"Our poor village..."*).

### 🪦 Last words
- Sometimes a dying villager says some last words, and the bubble stays where they fell. *"Why... Steve?"*, *"Curse you... pillagers..."*, or a grumpy *"Typical."*

### 🔧 Fixes
- **Gifts:** villagers take one gift at a time, so a thrown stack is no longer eaten in seconds. Only items a player threw count; flower farms and item collectors are left alone.
- **Iron farms:** villagers in a long panic cry out a couple of times, then stay quiet.
- **Performance:** villagers only talk when a player is within 32 blocks, so chunk-loaded farms don't spawn speech bubbles all day.
- **Trading halls:** villagers in 1x1 cells don't greet you or complain while you build, and you get at most one greeting every 1.5 seconds.
- **Weather:** a daytime thunderstorm no longer makes villagers say goodnight.
- **Greetings:** invisible and sneaking players aren't greeted.

### ⚙️ Commands & config
- New `/lively reload` (operators) reloads the config without restarting.
- New options: `raidLines`, `raidAlarm`, `deathLineChance`.

### 💾 Upgrading
- Villagers keep their names and personalities when you update from 1.0.0; old data converts automatically.
