package com.livelyvillagers;

import com.livelyvillagers.Lines.Topic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Saying hi as players pass, bedtime mumbles, and talking back when clicked. */
public final class Greetings {
	/** Minimum ticks between greetings aimed at the same player, from any villager. */
	private static final long PLAYER_GREET_GAP = 30;
	private static final Map<UUID, Long> PLAYER_GREETED_UNTIL = new HashMap<>();

	/** Whether another villager may address this player yet (shared with raid pleas). */
	static boolean playerReady(UUID player, long now) {
		Long until = PLAYER_GREETED_UNTIL.get(player);
		return until == null || now >= until;
	}

	static void playerAddressed(UUID player, long until) {
		PLAYER_GREETED_UNTIL.put(player, until);
	}

	/** Greets a player who just came into range, if the villager's personality and mood allow. */
	static void greet(Villager v, ServerLevel level, VillagerState state, long now) {
		double r = LivelyConfig.get().greetRadius;
		List<ServerPlayer> near = level.getEntitiesOfClass(ServerPlayer.class, v.getBoundingBox().inflate(r),
			p -> p.isAlive() && !p.isSpectator() && !p.isInvisible() && !p.isShiftKeyDown()
				&& p.distanceTo(v) <= r && v.hasLineOfSight(p));
		Set<UUID> inRange = new HashSet<>();
		ServerPlayer newcomer = null;
		for (ServerPlayer p : near) {
			inRange.add(p.getUUID());
			if (newcomer == null && !state.playersInRange.contains(p.getUUID())) {
				newcomer = p;
			}
		}
		state.playersInRange = inRange;
		if (newcomer == null) {
			return;
		}
		// lastGreeted holds the earliest time this player may be greeted again.
		Long allowedAt = state.lastGreeted.get(newcomer.getUUID());
		if (allowedAt != null && now < allowedAt) {
			return;
		}
		LivelyConfig cfg = LivelyConfig.get();
		VillagerMind mind = LivelyVillagers.mind(v);
		if (v.getRandom().nextFloat() >= TestHooks.chance((float) (mind.personality().greetChance * cfg.greetChanceMultiplier))) {
			// A quiet moment shouldn't lock the villager out for the full cooldown.
			state.lastGreeted.put(newcomer.getUUID(), now + cfg.greetRetrySeconds * 20L);
			LivelyVillagers.trace("greet-skip", v, newcomer.getScoreboardName());
			return;
		}
		// Walking down a trading hall shouldn't set off every villager at once.
		if (!TestHooks.forced() && !playerReady(newcomer.getUUID(), now)) {
			return;
		}
		if (isBoxedIn(v, level)) {
			return;
		}
		playerAddressed(newcomer.getUUID(), now + PLAYER_GREET_GAP);
		state.lastGreeted.put(newcomer.getUUID(), now + cfg.greetCooldownSeconds * 20L);

		int rep = v.getPlayerReputation(newcomer);
		Topic topic;
		SoundEvent sound = SoundEvents.VILLAGER_AMBIENT;
		if (v.isBaby()) {
			topic = Topic.GREET_BABY;
		} else if (rep <= -10) {
			topic = Topic.GREET_COLD;
			sound = SoundEvents.VILLAGER_NO;
			v.setUnhappyCounter(40);
		} else if (isSleepyTime(v)) {
			topic = Topic.GREET_NIGHT;
			if (rep >= 25) {
				level.broadcastEntityEvent(v, Speech.HAPPY);
			}
		} else if (rep >= 25) {
			topic = Topic.GREET_WARM;
			sound = SoundEvents.VILLAGER_YES;
			level.broadcastEntityEvent(v, Speech.HAPPY);
		} else if (level.isRainingAt(v.blockPosition()) && v.getRandom().nextInt(3) == 0) {
			topic = Topic.GREET_RAIN;
		} else {
			topic = Topic.GREET;
		}
		Speech.lookAt(v, newcomer);
		Speech.say(v, topic, sound, Map.of("player", newcomer.getScoreboardName()));
	}

	/** When the schedule switches to rest, some villagers announce it (only if someone is around to hear). */
	static void bedtime(Villager v, ServerLevel level, VillagerState state, boolean panicking, long now) {
		boolean resting = v.getBrain().isActive(Activity.REST);
		if (resting && !state.wasResting) {
			// Everyone switches on the same tick; spread the mumbling over the next ~10 seconds.
			int spread = TestHooks.forced() ? 60 : 180;
			state.bedtimeLineAt = v.getRandom().nextFloat() < TestHooks.chance(0.4F) ? now + 10 + v.getRandom().nextInt(spread) : -1;
		}
		state.wasResting = resting;
		if (state.bedtimeLineAt < 0 || now < state.bedtimeLineAt) {
			return;
		}
		state.bedtimeLineAt = -1;
		if (resting && !panicking && !v.isSleeping() && !v.isTrading() && level.getNearestPlayer(v, 16.0) != null) {
			Speech.say(v, Topic.BEDTIME, SoundEvents.VILLAGER_AMBIENT, Map.of());
		}
	}

	/** Evening/night by the clock (not the sky, which storms darken), or the villager's rest schedule. */
	public static boolean isSleepyTime(Villager v) {
		if (v.level().dimensionType().hasFixedTime()) {
			return v.getBrain().isActive(Activity.REST);
		}
		long time = v.level().getDayTime() % 24000L;
		return (time >= 12500L && time < 23500L) || v.getBrain().isActive(Activity.REST);
	}

	/** A villager walled in on three or four sides at foot level: a trading-hall or farm cell. */
	public static boolean isBoxedIn(Villager v, ServerLevel level) {
		BlockPos feet = v.blockPosition();
		int walls = 0;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos side = feet.relative(d);
			if (!level.getBlockState(side).getCollisionShape(level, side).isEmpty()) {
				walls++;
			}
		}
		return walls >= 3;
	}

	/**
	 * Plain right-click. Vanilla still runs afterwards: workers open trades, the jobless shake their heads
	 * (and already play the "no" sound, so no extra sound for them).
	 */
	public static void onClicked(Villager v, Player player) {
		if (!clickCooldownOver(v)) {
			return;
		}
		Speech.lookAt(v, player);
		Map<String, String> vars = Map.of("player", player.getScoreboardName());
		VillagerProfession job = v.getVillagerData().getProfession();
		if (v.isBaby()) {
			Speech.say(v, Topic.CLICK_BABY, null, vars);
		} else if (job == VillagerProfession.NONE || job == VillagerProfession.NITWIT) {
			Speech.say(v, isSleepyTime(v) ? Topic.CLICK_JOBLESS_NIGHT : Topic.CLICK_JOBLESS, null, vars);
		} else if (v.getPlayerReputation(player) <= -10) {
			Speech.say(v, Topic.GREET_COLD, SoundEvents.VILLAGER_NO, vars);
		} else {
			Speech.say(v, isSleepyTime(v) ? Topic.CLICK_WORKER_NIGHT : Topic.CLICK_WORKER, SoundEvents.VILLAGER_TRADE, vars);
		}
	}

	/** Right-clicking a sleeping villager: they mumble in their sleep (and stay asleep). */
	public static void onClickedSleeping(Villager v) {
		if (clickCooldownOver(v)) {
			Speech.say(v, Topic.SLEEP_TALK, null, Map.of());
		}
	}

	/** Sneak + empty-hand click: the villager introduces themselves. */
	public static void introduce(Villager v, Player player) {
		if (!clickCooldownOver(v)) {
			return;
		}
		Speech.lookAt(v, player);
		Map<String, String> vars = new HashMap<>();
		vars.put("player", player.getScoreboardName());
		vars.put("personality", LivelyVillagers.mind(v).personality().displayName().toLowerCase(Locale.ROOT));
		Speech.say(v, v.isBaby() ? Topic.GREET_BABY : Topic.INTRO, SoundEvents.VILLAGER_AMBIENT, vars);
	}

	/** The server can get two interact packets per click; only answer the first. */
	private static boolean clickCooldownOver(Villager v) {
		VillagerState state = LivelyVillagers.state(v);
		long now = v.level().getGameTime();
		if (now < state.nextClickLine) {
			return false;
		}
		state.nextClickLine = now + 10;
		return true;
	}

	private Greetings() {
	}
}
