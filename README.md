# Lively Villagers

Lively Villagers adds voice lines, new personalities & behaviour. Just something to make vanilla nicer!

- **Greetings:** walk within 7 blocks of a villager and they may say hi (cheerful always, curious usually, grumpy and shy about half the time). If they stay quiet they get another chance after 15 seconds; once they greet you they wait 90 seconds.
  What they say depends on your reputation, the time of day and the weather.
- **Block reactions:** villagers react to blocks placed near them. They love their own workstation, like
  flowers, lanterns, beds and crops, dislike skulls, cobwebs and magma, are scared of TNT, and complain if you
  build right on top of them. Break their workstation, bed or bell and they'll tell you about it.
- **Gifts:** throw flowers, cookies or berries (or cake, pumpkin pie or honey for extra love) near a villager.
  They walk over, take it, hold it for a moment and thank you. The first gift each Minecraft day raises your
  reputation with them a little, which also makes their trades cheaper.
- **Danger:** villagers also fear creepers, skeletons, spiders, witches and more, shout when they panic, run
  from lit TNT and hissing creepers, and calm down afterwards.
- **Right-click:** villagers without a job (and nitwits) grumble ("Hey...", "Stop touching me!"); working villagers greet you with a shop line before trading opens.
- **Night:** after dark villagers get sleepy: yawning greetings, "Time to turn in for the night." when they head to bed, tired shop lines, and sleep-talk if you poke them while they sleep.
- **Introductions:** sneak and right-click a villager with an empty hand to have them introduce themselves.

## Install
1. Install Fabric Loader for 1.21.1 and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Put `lively-villagers-<version>.jar` in your `mods` folder.

It's server-side: on a server, only the server needs it; players can join with a vanilla client.

## Commands
- `/lively info`: name, personality and your reputation for the villager you're looking at.
- `/lively personality <cheerful|curious|shy|grumpy>` (operators): change a villager's personality.

Name tags rename a villager in their speech too.

## Config
`config/lively-villagers.json` is created on first launch. You can turn each feature off, set the greeting
radius and cooldown, and set how much reputation gifts give.

## Notes
- Villagers only pick up gifts when the `mobGriefing` game rule is on (same as vanilla bread and seeds).
- Because villagers now also fear creepers and skeletons, a group of scared villagers can call an iron golem
  for those mobs too, the same way vanilla villagers do for zombies.

## Building
Gradle needs JDK 25 (Loom 1.18); the mod targets Java 21.

```
JAVA_HOME=/path/to/jdk-25 ./gradlew build              # jar in build/libs
JAVA_HOME=/path/to/jdk-25 ./gradlew runClient -Pselftest  # scripted in-game test + screenshots in run/screenshots
JAVA_HOME=/path/to/jdk-25 ./gradlew runClient -Pshowcase   # staged hero screenshots in real villages (1920x1080)
```
