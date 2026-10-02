package com.livelyvillagers;

import com.livelyvillagers.Lines.Topic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Panicking (with a cap, for iron farms), running from explosives, and last words. */
public final class Danger {
	/** Extra cries during one ordinary panic; iron-farm villagers would otherwise shout forever. */
	private static final int MAX_PANIC_SHOUTS = 2;
	/** Raids get a slightly longer allowance. */
	private static final int MAX_RAID_SHOUTS = 4;
	/** After this many ticks of continuous panic, stay quiet. */
	private static final long LONG_PANIC = 400;

	static void panic(Villager v, ServerLevel level, VillagerState state, boolean panicking, boolean inRaid, long now) {
		Brain<Villager> brain = v.getBrain();
		if (panicking && !state.wasPanicking) {
			state.panicEndedAt = -1;
			state.panicStartedAt = now;
			state.panicShouts = 0;
			state.nextPanicShout = now + 60 + v.getRandom().nextInt(60);
			level.broadcastEntityEvent(v, Speech.SWEAT);
			Optional<LivingEntity> attacker = brain.getMemory(MemoryModuleType.HURT_BY_ENTITY);
			Optional<LivingEntity> hostile = brain.getMemory(MemoryModuleType.NEAREST_HOSTILE);
			if (attacker.isPresent() && attacker.get() instanceof Player) {
				Speech.say(v, Topic.PANIC_HURT, SoundEvents.VILLAGER_HURT, Map.of());
			} else if (inRaid) {
				state.raidShouts++;
				Speech.say(v, Topic.RAID_PANIC, SoundEvents.VILLAGER_NO, Map.of("threat", threatName(hostile.or(() -> attacker))));
			} else if (hostile.isPresent()) {
				Speech.say(v, Topic.PANIC_HOSTILE, SoundEvents.VILLAGER_NO, Map.of("threat", threatName(hostile)));
			} else {
				Speech.say(v, Topic.PANIC_LOOP, SoundEvents.VILLAGER_NO, Map.of());
			}
		} else if (panicking && now >= state.nextPanicShout) {
			state.nextPanicShout = now + 60 + v.getRandom().nextInt(80);
			if (inRaid) {
				if (state.raidShouts < MAX_RAID_SHOUTS && v.getRandom().nextInt(3) == 0) {
					state.raidShouts++;
					level.broadcastEntityEvent(v, Speech.SWEAT);
					Speech.say(v, Topic.RAID_PANIC, SoundEvents.VILLAGER_NO,
						Map.of("threat", threatName(brain.getMemory(MemoryModuleType.NEAREST_HOSTILE))));
				}
			} else if (state.panicShouts < MAX_PANIC_SHOUTS && now - state.panicStartedAt <= LONG_PANIC && v.getRandom().nextInt(3) == 0) {
				state.panicShouts++;
				level.broadcastEntityEvent(v, Speech.SWEAT);
				Speech.say(v, Topic.PANIC_LOOP, SoundEvents.VILLAGER_NO, Map.of());
			}
		} else if (!panicking && state.wasPanicking) {
			state.panicEndedAt = now;
		} else if (!panicking && state.panicEndedAt > 0 && now - state.panicEndedAt >= 60) {
			state.panicEndedAt = -1;
			// Mid-raid there is nothing to be relieved about yet; the raid's end has its own lines.
			if (!inRaid) {
				Speech.say(v, Topic.CALM, SoundEvents.VILLAGER_AMBIENT, Map.of());
			}
		}
		state.wasPanicking = panicking;
	}

	private static String threatName(Optional<LivingEntity> threat) {
		return threat.map(e -> e.getType().getDescription().getString()).orElse("Raiders");
	}

	/** Lit TNT and hissing creepers aren't something the vanilla brain fears; run from them. */
	static void fleeExplosives(Villager v, ServerLevel level, VillagerState state, long now) {
		AABB box = v.getBoundingBox().inflate(7);
		Entity danger = level.getEntitiesOfClass(PrimedTnt.class, box, e -> true).stream().findFirst()
			.map(e -> (Entity) e)
			.or(() -> level.getEntitiesOfClass(Creeper.class, box, c -> c.getSwellDir() > 0).stream().findFirst())
			.orElse(null);
		if (danger == null) {
			return;
		}
		Vec3 away = DefaultRandomPos.getPosAway(v, 12, 6, danger.position());
		if (away != null) {
			v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(away, 0.75F, 0));
		}
		if (now >= state.nextPanicShout) {
			state.nextPanicShout = now + 60;
			level.broadcastEntityEvent(v, Speech.SWEAT);
			Speech.say(v, Topic.PANIC_TNT, SoundEvents.VILLAGER_NO, Map.of());
		}
	}

	/** Sometimes a dying villager gets some last words (the bubble stays where they fell). */
	public static void onDeath(Villager v, DamageSource source) {
		if (!(v.level() instanceof ServerLevel level)) {
			return;
		}
		if (v.getRandom().nextDouble() >= TestHooks.chance((float) LivelyConfig.get().deathLineChance)) {
			return;
		}
		Entity killer = source.getEntity();
		Player near = level.getNearestPlayer(v, 32.0);
		Map<String, String> vars = new HashMap<>();
		vars.put("player", killer instanceof Player p ? p.getScoreboardName() : near != null ? near.getScoreboardName() : "hero");
		Topic topic = killer instanceof Player ? Topic.DEATH_BY_PLAYER : killer instanceof Raider ? Topic.DEATH_BY_RAIDER : Topic.DEATH;
		Speech.say(v, topic, null, vars);
	}

	private Danger() {
	}
}
