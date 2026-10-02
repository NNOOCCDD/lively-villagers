package com.livelyvillagers;

import com.livelyvillagers.Lines.Topic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;

import java.util.HashMap;
import java.util.Map;

/**
 * Follows the raid around a villager: the first to notice shouts a warning and sends the neighbours
 * into hiding (the same memory the village bell sets), villagers near the player beg for help, and
 * afterwards they cheer or mourn. Panic lines during the raid live in {@link Danger}.
 */
public final class RaidReactions {
	/** At most a few spoken warnings per raid, spaced out, so the whole village doesn't shout at once. */
	private static final int MAX_RAID_WARNINGS = 4;
	private static final Map<Integer, long[]> RAID_WARNINGS = new HashMap<>();

	static void tick(Villager v, ServerLevel level, VillagerState state) {
		Raid raid = level.getRaidAt(v.blockPosition());
		// isActive() only means "loaded"; a won or lost raid stays active while it winds down.
		boolean ongoing = raid != null && raid.isActive() && !raid.isOver() && !raid.isStopped();
		if (ongoing && state.raidId != raid.getId()) {
			state.raidId = raid.getId();
			state.raidShouts = 0;
			state.raidHeroSaid = false;
			if (!v.isSleeping() && v.getRandom().nextFloat() < TestHooks.chance(0.35F) && takeRaidWarning(raid.getId(), level.getGameTime())) {
				state.raidShouts++;
				level.broadcastEntityEvent(v, Speech.SWEAT);
				Speech.say(v, Topic.RAID_START, SoundEvents.VILLAGER_NO, Map.of());
				if (LivelyConfig.get().raidAlarm) {
					soundAlarm(v, level);
				}
			}
		} else if (!ongoing && state.raidId != -1) {
			if (raid != null && raid.getId() == state.raidId && !v.isSleeping()) {
				Player hero = level.getNearestPlayer(v, 48.0);
				Map<String, String> vars = Map.of("player", hero != null ? hero.getScoreboardName() : "hero");
				if (raid.isVictory()) {
					level.broadcastEntityEvent(v, Speech.HAPPY);
					if (v.getRandom().nextFloat() < TestHooks.chance(0.6F)) {
						Speech.say(v, Topic.RAID_WON, SoundEvents.VILLAGER_CELEBRATE, vars);
					}
				} else if (raid.isLoss()) {
					v.setUnhappyCounter(40);
					Speech.say(v, Topic.RAID_LOST, SoundEvents.VILLAGER_NO, vars);
				}
			}
			state.raidId = -1;
		}
	}

	private static boolean takeRaidWarning(int raidId, long now) {
		long[] w = RAID_WARNINGS.computeIfAbsent(raidId, k -> new long[] {0, 0});
		if (w[1] >= MAX_RAID_WARNINGS || now < w[0]) {
			return false;
		}
		w[0] = now + 30;
		w[1]++;
		return true;
	}

	/** Neighbours "hear the bell": vanilla villagers with this memory run indoors and hide. */
	private static void soundAlarm(Villager caller, ServerLevel level) {
		long now = level.getGameTime();
		for (Villager other : level.getEntitiesOfClass(Villager.class, caller.getBoundingBox().inflate(16), o -> o != caller && o.isAlive())) {
			other.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, now);
		}
		LivelyVillagers.trace("RAID_ALARM", caller, "neighbours warned");
	}

	/** During a raid, a villager near the player begs them for help (at most once per raid). */
	static void askForHelp(Villager v, ServerLevel level, VillagerState state) {
		if (state.raidHeroSaid || v.isTrading()) {
			return;
		}
		Player player = level.getNearestPlayer(v.getX(), v.getY(), v.getZ(), 8.0, p -> !p.isSpectator() && v.hasLineOfSight(p));
		if (player == null) {
			return;
		}
		state.raidHeroSaid = true;
		long now = level.getGameTime();
		if (!Greetings.playerReady(player.getUUID(), now)) {
			return;
		}
		if (v.getRandom().nextFloat() < TestHooks.chance(0.5F)) {
			Greetings.playerAddressed(player.getUUID(), now + 60);
			Speech.lookAt(v, player);
			Speech.say(v, Topic.RAID_HERO, SoundEvents.VILLAGER_YES, Map.of("player", player.getScoreboardName()));
		}
	}

	private RaidReactions() {
	}
}
