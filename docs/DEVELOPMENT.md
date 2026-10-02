# Development notes

How the mod is built and tested, and what was learned about vanilla villagers along the way.

## Code layout
- `VillagerBrain`: runs every behaviour for a villager, from the end of its vanilla AI step (`VillagerMixin`).
- `Greetings`, `BlockReactions`, `Gifts`, `Danger`, `RaidReactions`: one behaviour each.
- `Speech` + `SpeechBubbles`: picking/filling a line from `Lines`, the sound, and the vanilla `text_display` bubble.
- `VillagerMind` (saved: name, personality, gift days) and `VillagerState` (not saved: cooldowns and flags), both Fabric data attachments.
- `LivelyConfig`: `config/lively-villagers.json`.
- `dev/` (and `src/client/.../dev/`): self-test, raid test, screenshot and film tools. Left out of release jars; `TestHooks` is their only way in.

## Testing
- `./gradlew runClient -Pselftest`: flat world, one scripted villager; prints `SELFTEST PASS/FAIL` lines to the log.
- `./gradlew runClient -Praidtest`: a real raid on a real village (fixed seed); prints `RAIDTEST` lines.
- `./gradlew runClient -Pshowcase` / `-Pfilm [-PfilmOnly=06-danger]`: hero screenshots / showcase footage.
- Release jar check: drop `build/libs/lively-villagers-<version>.jar` and Fabric API into a dedicated Fabric server and summon/kill villagers.

## Target
- Minecraft Java **1.21.1**, Fabric Loader **0.19.5**, Fabric API **0.116.17+1.21.1**, Loom **1.18-SNAPSHOT** (`fabric-loom-remap`), Mojang mappings.
- Template: `FabricMC/fabric-example-mod` branch `1.21.1`.
- Loom 1.18 needs **JDK 25 to run Gradle** (`JAVA_HOME` pointing at a JDK 25). The mod compiles/runs on a Java 21 toolchain (`/usr/lib/jvm` 21).
- Decompiled sources: `./gradlew genSources`; read them from the Loom cache, never commit them.

## Route
Loader API + Mixin. Everything is server-side using vanilla packets (text_display speech bubbles, sounds, entity-event particles), so the jar works on a server without clients installing it.

## Vanilla facts (1.21.1, Mojang names)
- `VillagerHostilesSensor.isMatchingEntity` decides NEAREST_HOSTILE; `VillagerPanicTrigger` panics on NEAREST_HOSTILE or HURT_BY. Panic walk speed 0.75.
- Villager pickup: `Mob.aiStep` (needs `mobGriefing`) → `Villager.wantsToPickUp(stack)` → `Villager.pickUpItem(ItemEntity)`; the brain walks to wanted items via `NearestItemSensor` (stack-only filter).
- `ItemEntity.getOwner()` = thrower. `Villager.getGossips().add(uuid, GossipType, n)`; `getPlayerReputation(player)`.
- `AbstractVillager.setUnhappyCounter(40)` is synced → head shake on clients. Entity events: 12 hearts, 13 angry, 14 happy, 42 sweat.
- QuickPlay singleplayer needs an existing world; selftest client creates one via `WorldOpenFlows.createFreshLevel`.

## History
- 2026-10-02: scaffolded, genSources OK.
- 2026-10-02: v1 features in; `./gradlew runClient -Pselftest` → 13 PASS, 0 FAIL, 1 SKIP (job-site break: penned villager can't path to claim the composter). Screenshots in run/screenshots.

## Gotchas found
1. Loom 1.18 refuses to run on Java 21 ("requires at least JVM runtime version 25"); compile still targets 21 via toolchain.
2. text_display NBT without "alignment" logs `Display entityNot a string` (vanilla decodes it unconditionally).
3. `ShowTradesToPlayer` clears the villager's main hand every tick while a player is within ~4 blocks; re-equip held gifts after the brain tick (customServerAiStep TAIL) and the equipment sync never sees the gap.
4. A FARMER set by command with 0 XP and no job site reverts to NONE (ResetProfession); give it 1 XP in tests.
5. ServerEntityEvents.ENTITY_LOAD also fires for addFreshEntity — register bubbles before adding them or the stale-bubble cleanup kills them.
- 2026-10-02: hero screenshots via `./gradlew runClient -Pshowcase` (Showcase.java, 1920x1080, spectator camera, HUD hidden). Curated set in screenshots/ (gitignored, ~18 MB).

## Showcase gotchas
6. Heightmap "ground" lands on roofs and hillsides; constrain the stage to within -2..+1 of the bell's Y and raycast camera→stage.
7. Snow layers and short grass are not air; use `canBeReplaced()` when checking open ground, or every snowy street gets rejected (and the picker falls back to frozen ponds).
8. Real villagers wander into the foreground; discard non-actor mobs on the camera side before each shot.
9. Locating a snowy village from a far-away desert took ~8 min of server-thread time; give runs a long timeout.

## 1.0.1 notes
10. During raids vanilla puts villagers in RAID/HIDE activities, never PANIC; "a raider in NEAREST_HOSTILE" is the raid-panic signal.
11. Raid.isActive() only means "loaded" and stays true after VICTORY/LOSS; ongoing = isActive && !isOver && !isStopped.
12. HEARD_BELL_TIME is the memory the bell sets; setting it on neighbours sends them to hide (the raid alarm).
13. Raids.createOrExtendRaid needs occupied village POIs; the flat test world has none, so raid tests run in the real showcase village (-Praidtest).
14. Release jar: dev tools are excluded in the jar task and their fabric.mod.json lines (comma-first) filtered out; LivelyVillagers loads dev tools by reflection. Verified on a real dedicated Fabric server.
