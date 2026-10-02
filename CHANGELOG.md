# Changelog

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
