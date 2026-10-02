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
