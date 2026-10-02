package com.livelyvillagers.dev;

import com.livelyvillagers.LivelyVillagers;
import com.livelyvillagers.TestHooks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dev-only (-Dlivelyvillagers.raidtest=true): starts a real raid on a real village (fixed showcase
 * seed), lets villagers react, has a raider kill one villager, defeats every wave, and reports which
 * raid lines fired.
 */
public final class RaidTest {
	private static final BlockPos PLAINS_VILLAGE = new BlockPos(144, 70, 656);
	private static final int TIMEOUT = 20 * 60 * 4;

	private static final Map<String, Integer> counts = new TreeMap<>();
	private static final List<String> results = new ArrayList<>();
	private static ServerPlayer player;
	private static ServerLevel level;
	private static long start = -1;
	private static Raid raid;
	private static BlockPos bell;
	private static long raidersSeenAt = -1;
	private static boolean killedOne;
	private static long victoryAt = -1;

	public static void install() {
		TestHooks.forcedChance = 1.0F;
		TestHooks.traceListener = (event, v) -> counts.merge(event, 1, Integer::sum);
		TestHooks.onPlayerJoin(p -> {
			player = p;
			start = p.getServer().getTickCount() + 40;
		});
		TestHooks.onServerTick(RaidTest::tick);
	}

	private static void tick(MinecraftServer server) {
		if (start < 0 || player == null) {
			return;
		}
		long t = server.getTickCount() - start;
		if (t < 0) {
			return;
		}
		try {
			step(t, server);
		} catch (RuntimeException e) {
			LivelyVillagers.LOGGER.error("RAIDTEST crashed at t={}", t, e);
			results.add("FAIL crashed: " + e);
			finish();
		}
	}

	private static void step(long t, MinecraftServer server) {
		if (t == 0) {
			level = player.serverLevel();
			GameRules rules = level.getGameRules();
			rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
			rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
			rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
			level.setDayTime(6000);
			level.setWeatherParameters(12000, 0, false, false);
			player.setGameMode(GameType.CREATIVE);
			player.teleportTo(level, PLAINS_VILLAGE.getX() + 0.5, 140, PLAINS_VILLAGE.getZ() + 0.5, 0, 90);
			return;
		}
		if (t == 200) {
			bell = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), PLAINS_VILLAGE, 96, PoiManager.Occupancy.ANY)
				.orElse(PLAINS_VILLAGE);
			BlockPos stand = bell.offset(3, 0, 3);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, stand.getX(), stand.getZ());
			player.teleportTo(level, stand.getX() + 0.5, y, stand.getZ() + 0.5, 0, 0);
			return;
		}
		if (t == 260) {
			raid = level.getRaids().createOrExtendRaid(player, bell);
			LivelyVillagers.LOGGER.info("RAIDTEST raid {} at {}", raid == null ? "null" : raid.getId(), bell);
			if (raid == null) {
				results.add("FAIL could not start a raid");
				finish();
			}
			return;
		}
		if (t < 260 || raid == null) {
			return;
		}
		if (t % 40 == 0) {
			LivelyVillagers.LOGGER.info("RAIDTEST t={} started={} waves={} alive={} victory={} counts={}", t, raid.isStarted(),
				raid.getGroupsSpawned(), raid.getTotalRaidersAlive(), raid.isVictory(), counts);
		}
		if (raid.isVictory()) {
			if (victoryAt < 0) {
				victoryAt = t;
			} else if (t - victoryAt == 120) {
				report();
			}
			return;
		}
		if (raid.isLoss() || raid.isStopped()) {
			results.add("FAIL raid ended without a victory (loss=" + raid.isLoss() + ")");
			report();
			return;
		}
		// Let each wave be seen and fled from for a few seconds, then defeat it.
		if (raid.getTotalRaidersAlive() > 0) {
			if (raidersSeenAt < 0) {
				raidersSeenAt = t;
			}
			if (!killedOne && t - raidersSeenAt == 60) {
				killOneVillager();
			}
			// Keep each wave alive until villagers have actually seen raiders (raid panic needs a raider in
			// sight; a wave can spawn far off), but not forever.
			boolean seen = counts.getOrDefault("RAID_PANIC", 0) > 0;
			if ((seen && t - raidersSeenAt >= 240) || t - raidersSeenAt >= 600) {
				for (Raider r : new ArrayList<>(raid.getAllRaiders())) {
					r.kill();
				}
				raidersSeenAt = -1;
			}
		}
		if (t > TIMEOUT) {
			results.add("FAIL timed out");
			report();
		}
	}

	private static void killOneVillager() {
		Raider raider = raid.getAllRaiders().stream().findFirst().orElse(null);
		// The nearest villager, so someone is around to hear the last words.
		Villager victim = level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(48), Villager::isAlive)
			.stream().min(java.util.Comparator.comparingDouble(v -> v.distanceToSqr(player))).orElse(null);
		if (raider != null && victim != null) {
			killedOne = true;
			victim.hurt(level.damageSources().mobAttack(raider), 1000.0F);
		}
	}

	private static void expect(String what, String event) {
		int n = counts.getOrDefault(event, 0);
		results.add((n > 0 ? "PASS " : "FAIL ") + what + " (" + event + " x" + n + ")");
	}

	private static void report() {
		expect("raid warning", "RAID_START");
		expect("alarm sent neighbours to hide", "RAID_ALARM");
		expect("raid panic lines", "RAID_PANIC");
		expect("victory cheer", "RAID_WON");
		expect("last words when killed by a raider", "DEATH_BY_RAIDER");
		int hero = counts.getOrDefault("RAID_HERO", 0);
		results.add((hero > 0 ? "PASS " : "INFO ") + "plea to the player (RAID_HERO x" + hero + "; needs a villager within 8 blocks in sight)");
		int raidShouts = counts.getOrDefault("RAID_PANIC", 0) + counts.getOrDefault("RAID_START", 0);
		results.add("INFO raid shouts in total: " + raidShouts + ", all events: " + counts);
		finish();
	}

	private static void finish() {
		start = -1;
		long fails = results.stream().filter(r -> r.startsWith("FAIL")).count();
		LivelyVillagers.LOGGER.info("RAIDTEST RESULTS ({} fail):", fails);
		results.forEach(r -> LivelyVillagers.LOGGER.info("RAIDTEST {}", r));
		LivelyVillagers.LOGGER.info("RAIDTEST DONE");
		SelfTest.finished.run();
	}

	private RaidTest() {
	}
}
