package com.livelyvillagers;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

/** Runs every behaviour for one villager, from the end of its vanilla server AI step. */
public final class VillagerBrain {
	public static void tick(Villager v) {
		if (!(v.level() instanceof ServerLevel level) || v.isNoAi()) {
			return;
		}
		long now = level.getGameTime();
		VillagerState state = LivelyVillagers.state(v);
		Gifts.tickHeldGift(v, state, now);

		// Spread the heavier checks over ticks: each villager thinks twice a second.
		if ((now + v.getId()) % 10 != 0) {
			return;
		}
		LivelyConfig cfg = LivelyConfig.get();
		boolean panicking = v.getBrain().isActive(Activity.PANIC);
		if (cfg.raidLines) {
			RaidReactions.tick(v, level, state);
		}
		boolean inRaid = state.raidId != -1;
		// During a raid vanilla switches villagers to the RAID/HIDE activities instead of PANIC, so a
		// raider in sight is what counts as panicking there.
		boolean threatened = panicking || (inRaid && v.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE));
		if (cfg.dangerShouts) {
			Danger.panic(v, level, state, threatened, inRaid, now);
		}
		if (cfg.extraDangers) {
			Danger.fleeExplosives(v, level, state, now);
		}
		if (inRaid && !v.isSleeping()) {
			RaidReactions.askForHelp(v, level, state);
		} else if (cfg.greetings && !panicking && !v.isSleeping() && !v.isTrading()) {
			Greetings.greet(v, level, state, now);
		}
		if (cfg.greetings) {
			Greetings.bedtime(v, level, state, panicking, now);
		}
	}

	private VillagerBrain() {
	}
}
