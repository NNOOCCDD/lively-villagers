# Lively Villagers — mod log

## Target
- Minecraft Java **1.21.1**, Fabric Loader **0.19.5**, Fabric API **0.116.17+1.21.1**, Loom **1.18-SNAPSHOT** (`fabric-loom-remap`), Mojang mappings.
- Template: `FabricMC/fabric-example-mod` branch `1.21.1`.
- Loom 1.18 needs **JDK 25 to run Gradle**: `export JAVA_HOME=~/.jdks/jdk-25.0.4.1+1`. The mod compiles/runs on a Java 21 toolchain (`/usr/lib/jvm` 21).
- Decompiled sources (never commit): `~/mc-1.21.1-decomp` (common), `~/mc-1.21.1-decomp-client`.

## Route
Loader API + Mixin. Everything is server-side using vanilla packets (text_display speech bubbles, sounds, entity-event particles), so the jar works on a server without clients installing it.

## Vanilla facts (1.21.1, Mojang names)
- `VillagerHostilesSensor.isMatchingEntity` decides NEAREST_HOSTILE; `VillagerPanicTrigger` panics on NEAREST_HOSTILE or HURT_BY. Panic walk speed 0.75.
- Villager pickup: `Mob.aiStep` (needs `mobGriefing`) → `Villager.wantsToPickUp(stack)` → `Villager.pickUpItem(ItemEntity)`; the brain walks to wanted items via `NearestItemSensor` (stack-only filter).
- `ItemEntity.getOwner()` = thrower. `Villager.getGossips().add(uuid, GossipType, n)`; `getPlayerReputation(player)`.
- `AbstractVillager.setUnhappyCounter(40)` is synced → head shake on clients. Entity events: 12 hearts, 13 angry, 14 happy, 42 sweat.
- QuickPlay singleplayer needs an existing world; selftest client creates one via `WorldOpenFlows.createFreshLevel`.

## Log
- 2026-10-02: scaffolded, genSources OK.
- 2026-10-02: v1 features in; `./gradlew runClient -Pselftest` → 13 PASS, 0 FAIL, 1 SKIP (job-site break: penned villager can't path to claim the composter). Screenshots in run/screenshots.

## Gotchas found
1. Loom 1.18 refuses to run on Java 21 ("requires at least JVM runtime version 25"); compile still targets 21 via toolchain.
2. text_display NBT without "alignment" logs `Display entityNot a string` (vanilla decodes it unconditionally).
3. `ShowTradesToPlayer` clears the villager's main hand every tick while a player is within ~4 blocks; re-equip held gifts after the brain tick (customServerAiStep TAIL) and the equipment sync never sees the gap.
4. A FARMER set by command with 0 XP and no job site reverts to NONE (ResetProfession); give it 1 XP in tests.
5. ServerEntityEvents.ENTITY_LOAD also fires for addFreshEntity — register bubbles before adding them or the stale-bubble cleanup kills them.
