package com.livelyvillagers;

import com.livelyvillagers.Lines.Topic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class Reactions {
	// Entity events the client already knows how to draw for villagers.
	private static final byte HEARTS = 12;
	private static final byte ANGRY = 13;
	private static final byte HAPPY = 14;
	private static final byte SWEAT = 42;

	private enum Feeling { LOVE_JOB, WANT_JOB, LIKE, DISLIKE, SCARED, NEUTRAL }

	// ---------------------------------------------------------------- per-tick brain

	public static void tick(Villager v) {
		if (!(v.level() instanceof ServerLevel level) || v.isNoAi()) {
			return;
		}
		long now = level.getGameTime();
		VillagerState state = LivelyVillagers.state(v);

		if (state.heldGift != null) {
			if (now >= state.clearHandAt) {
				if (!v.isTrading() && v.getMainHandItem().is(state.heldGift)) {
					v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				}
				state.heldGift = null;
			} else if (!v.isTrading() && v.getMainHandItem().isEmpty()) {
				// ShowTradesToPlayer empties the hand every tick while a player is near; this runs after the
				// brain, so the gift is what gets synced. Trade previews still win because they fill the hand.
				v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(state.heldGift));
			}
		}

		// Spread the heavier checks over ticks: each villager thinks twice a second.
		if ((now + v.getId()) % 10 != 0) {
			return;
		}
		LivelyConfig cfg = LivelyConfig.get();
		boolean panicking = v.getBrain().isActive(Activity.PANIC);
		if (cfg.raidLines) {
			raid(v, level, state);
		}
		boolean inRaid = state.raidId != -1;
		// During a raid vanilla switches villagers to the RAID/HIDE activities instead of PANIC, so a
		// raider in sight is what counts as panicking there.
		boolean threatened = panicking || (inRaid && v.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE));
		if (cfg.dangerShouts) {
			panic(v, level, state, threatened, inRaid, now);
		}
		if (cfg.extraDangers) {
			fleeExplosives(v, level, state, now);
		}
		if (inRaid && !v.isSleeping()) {
			raidHero(v, level, state);
		} else if (cfg.greetings && !panicking && !v.isSleeping() && !v.isTrading()) {
			greet(v, level, state, now);
		}
		if (cfg.greetings) {
			bedtime(v, level, state, panicking, now);
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

	/** When the schedule switches to rest, some villagers announce it (only if someone is around to hear). */
	private static void bedtime(Villager v, ServerLevel level, VillagerState state, boolean panicking, long now) {
		boolean resting = v.getBrain().isActive(Activity.REST);
		if (resting && !state.wasResting) {
			float chance = LivelyVillagers.forcedChance >= 0 ? LivelyVillagers.forcedChance : 0.4F;
			// Everyone switches on the same tick; spread the mumbling over the next ~10 seconds.
			int spread = LivelyVillagers.forcedChance >= 0 ? 60 : 180;
			state.bedtimeLineAt = v.getRandom().nextFloat() < chance ? now + 10 + v.getRandom().nextInt(spread) : -1;
		}
		state.wasResting = resting;
		if (state.bedtimeLineAt < 0 || now < state.bedtimeLineAt) {
			return;
		}
		state.bedtimeLineAt = -1;
		if (resting && !panicking && !v.isSleeping() && !v.isTrading()
			&& level.getNearestPlayer(v, 16.0) != null) {
			say(v, Topic.BEDTIME, SoundEvents.VILLAGER_AMBIENT, Map.of());
		}
	}

	/** Minimum ticks between greetings aimed at the same player, from any villager. */
	private static final long PLAYER_GREET_GAP = 30;
	private static final Map<UUID, Long> PLAYER_GREETED_UNTIL = new HashMap<>();

	/** A villager walled in on three or four sides at foot level: a trading-hall or farm cell. */
	public static boolean isBoxedIn(Villager v, ServerLevel level) {
		BlockPos feet = v.blockPosition();
		int walls = 0;
		for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			BlockPos side = feet.relative(d);
			if (!level.getBlockState(side).getCollisionShape(level, side).isEmpty()) {
				walls++;
			}
		}
		return walls >= 3;
	}

	private static void greet(Villager v, ServerLevel level, VillagerState state, long now) {
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
		double chance = LivelyVillagers.forcedChance >= 0 ? LivelyVillagers.forcedChance
			: mind.personality().greetChance * cfg.greetChanceMultiplier;
		if (v.getRandom().nextFloat() >= chance) {
			// A quiet moment shouldn't lock the villager out for the full cooldown.
			state.lastGreeted.put(newcomer.getUUID(), now + cfg.greetRetrySeconds * 20L);
			LivelyVillagers.trace("greet-skip", v, newcomer.getScoreboardName());
			return;
		}
		// Walking down a trading hall shouldn't set off every villager at once.
		Long playerQuietUntil = PLAYER_GREETED_UNTIL.get(newcomer.getUUID());
		if (LivelyVillagers.forcedChance < 0 && playerQuietUntil != null && now < playerQuietUntil) {
			return;
		}
		if (isBoxedIn(v, level)) {
			return;
		}
		PLAYER_GREETED_UNTIL.put(newcomer.getUUID(), now + PLAYER_GREET_GAP);
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
				level.broadcastEntityEvent(v, HAPPY);
			}
		} else if (rep >= 25) {
			topic = Topic.GREET_WARM;
			sound = SoundEvents.VILLAGER_YES;
			level.broadcastEntityEvent(v, HAPPY);
		} else if (level.isRainingAt(v.blockPosition()) && v.getRandom().nextInt(3) == 0) {
			topic = Topic.GREET_RAIN;
		} else {
			topic = Topic.GREET;
		}
		lookAt(v, newcomer);
		say(v, topic, sound, Map.of("player", newcomer.getScoreboardName()));
	}

	private static final int MAX_PANIC_SHOUTS = 2;

	private static final int MAX_RAID_SHOUTS = 4;

	private static void panic(Villager v, ServerLevel level, VillagerState state, boolean panicking, boolean inRaid, long now) {
		Brain<Villager> brain = v.getBrain();
		if (panicking && !state.wasPanicking) {
			state.panicEndedAt = -1;
			state.panicStartedAt = now;
			state.panicShouts = 0;
			state.nextPanicShout = now + 60 + v.getRandom().nextInt(60);
			level.broadcastEntityEvent(v, SWEAT);
			Optional<LivingEntity> attacker = brain.getMemory(MemoryModuleType.HURT_BY_ENTITY);
			Optional<LivingEntity> hostile = brain.getMemory(MemoryModuleType.NEAREST_HOSTILE);
			if (attacker.isPresent() && attacker.get() instanceof Player) {
				say(v, Topic.PANIC_HURT, SoundEvents.VILLAGER_HURT, Map.of());
			} else if (inRaid) {
				state.raidShouts++;
				String threat = hostile.or(() -> attacker).map(e -> e.getType().getDescription().getString()).orElse("Raiders");
				say(v, Topic.RAID_PANIC, SoundEvents.VILLAGER_NO, Map.of("threat", threat));
			} else if (hostile.isPresent()) {
				say(v, Topic.PANIC_HOSTILE, SoundEvents.VILLAGER_NO, Map.of("threat", hostile.get().getType().getDescription().getString()));
			} else {
				say(v, Topic.PANIC_LOOP, SoundEvents.VILLAGER_NO, Map.of());
			}
		} else if (panicking && now >= state.nextPanicShout) {
			state.nextPanicShout = now + 60 + v.getRandom().nextInt(80);
			// A couple of cries, then quiet: villagers in iron farms see a zombie all day long.
			boolean longPanic = now - state.panicStartedAt > 400;
			if (inRaid) {
				// Raids get their own, slightly longer, allowance of cries.
				if (state.raidShouts < MAX_RAID_SHOUTS && v.getRandom().nextInt(3) == 0) {
					state.raidShouts++;
					level.broadcastEntityEvent(v, SWEAT);
					String threat = brain.getMemory(MemoryModuleType.NEAREST_HOSTILE)
						.map(e -> e.getType().getDescription().getString()).orElse("Raiders");
					say(v, Topic.RAID_PANIC, SoundEvents.VILLAGER_NO, Map.of("threat", threat));
				}
			} else if (state.panicShouts < MAX_PANIC_SHOUTS && !longPanic && v.getRandom().nextInt(3) == 0) {
				state.panicShouts++;
				level.broadcastEntityEvent(v, SWEAT);
				say(v, Topic.PANIC_LOOP, SoundEvents.VILLAGER_NO, Map.of());
			}
		} else if (!panicking && state.wasPanicking) {
			state.panicEndedAt = now;
		} else if (!panicking && state.panicEndedAt > 0 && now - state.panicEndedAt >= 60) {
			state.panicEndedAt = -1;
			if (inRaid) {
				state.wasPanicking = panicking;
				return;
			}
			say(v, Topic.CALM, SoundEvents.VILLAGER_AMBIENT, Map.of());
		}
		state.wasPanicking = panicking;
	}

	/**
	 * Follows the raid around this villager: the first to notice it shouts a warning and sends the
	 * neighbours into hiding (the same memory the village bell sets); afterwards they cheer or mourn.
	 */
	private static void raid(Villager v, ServerLevel level, VillagerState state) {
		Raid raid = level.getRaidAt(v.blockPosition());
		// isActive() only means "loaded"; a won or lost raid stays active while it winds down.
		boolean ongoing = raid != null && raid.isActive() && !raid.isOver() && !raid.isStopped();
		if (ongoing && state.raidId != raid.getId()) {
			state.raidId = raid.getId();
			state.raidShouts = 0;
			state.raidHeroSaid = false;
			float chance = LivelyVillagers.forcedChance >= 0 ? LivelyVillagers.forcedChance : 0.35F;
			if (!v.isSleeping() && v.getRandom().nextFloat() < chance && takeRaidWarning(raid.getId(), level.getGameTime())) {
				state.raidShouts++;
				level.broadcastEntityEvent(v, SWEAT);
				say(v, Topic.RAID_START, SoundEvents.VILLAGER_NO, Map.of());
				if (LivelyConfig.get().raidAlarm) {
					soundAlarm(v, level);
				}
			}
		} else if (!ongoing && state.raidId != -1) {
			if (raid != null && raid.getId() == state.raidId && !v.isSleeping()) {
				Player hero = level.getNearestPlayer(v, 48.0);
				Map<String, String> vars = Map.of("player", hero != null ? hero.getScoreboardName() : "hero");
				float cheer = LivelyVillagers.forcedChance >= 0 ? LivelyVillagers.forcedChance : 0.6F;
				if (raid.isVictory()) {
					level.broadcastEntityEvent(v, HAPPY);
					if (v.getRandom().nextFloat() < cheer) {
						say(v, Topic.RAID_WON, SoundEvents.VILLAGER_CELEBRATE, vars);
					}
				} else if (raid.isLoss()) {
					v.setUnhappyCounter(40);
					say(v, Topic.RAID_LOST, SoundEvents.VILLAGER_NO, vars);
				}
			}
			state.raidId = -1;
		}
	}

	/** At most a few spoken warnings per raid, spaced out, so the whole village doesn't shout at once. */
	private static final int MAX_RAID_WARNINGS = 4;
	private static final Map<Integer, long[]> RAID_WARNINGS = new HashMap<>();

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

	/** During a raid, a villager near the player begs them for help (once per raid). */
	private static void raidHero(Villager v, ServerLevel level, VillagerState state) {
		if (state.raidHeroSaid || v.isTrading()) {
			return;
		}
		Player player = level.getNearestPlayer(v.getX(), v.getY(), v.getZ(), 8.0, p -> !p.isSpectator() && v.hasLineOfSight(p));
		if (player == null) {
			return;
		}
		state.raidHeroSaid = true;
		float chance = LivelyVillagers.forcedChance >= 0 ? LivelyVillagers.forcedChance : 0.5F;
		long now = level.getGameTime();
		Long quietUntil = PLAYER_GREETED_UNTIL.get(player.getUUID());
		if (quietUntil != null && now < quietUntil) {
			return;
		}
		if (v.getRandom().nextFloat() < chance) {
			PLAYER_GREETED_UNTIL.put(player.getUUID(), now + 60);
			lookAt(v, player);
			say(v, Topic.RAID_HERO, SoundEvents.VILLAGER_YES, Map.of("player", player.getScoreboardName()));
		}
	}

	/** Sometimes a dying villager gets some last words (the bubble stays where they fell). */
	public static void onDeath(Villager v, DamageSource source) {
		if (!(v.level() instanceof ServerLevel level)) {
			return;
		}
		double chance = LivelyVillagers.forcedChance >= 0 ? LivelyVillagers.forcedChance : LivelyConfig.get().deathLineChance;
		if (v.getRandom().nextDouble() >= chance) {
			return;
		}
		Entity killer = source.getEntity();
		Player near = level.getNearestPlayer(v, 32.0);
		Map<String, String> vars = new HashMap<>();
		vars.put("player", killer instanceof Player p ? p.getScoreboardName() : near != null ? near.getScoreboardName() : "hero");
		Topic topic = killer instanceof Player ? Topic.DEATH_BY_PLAYER
			: killer instanceof net.minecraft.world.entity.raid.Raider ? Topic.DEATH_BY_RAIDER : Topic.DEATH;
		say(v, topic, null, vars);
	}

	/** Lit TNT and hissing creepers aren't something the vanilla brain fears; run from them. */
	private static void fleeExplosives(Villager v, ServerLevel level, VillagerState state, long now) {
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
			level.broadcastEntityEvent(v, SWEAT);
			say(v, Topic.PANIC_TNT, SoundEvents.VILLAGER_NO, Map.of());
		}
	}

	// ---------------------------------------------------------------- blocks

	public static void onBlockPlaced(ServerLevel level, BlockPos pos, BlockState placed, Player player) {
		LivelyConfig cfg = LivelyConfig.get();
		if (!cfg.blockReactions) {
			return;
		}
		Vec3 center = Vec3.atCenterOf(pos);
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(cfg.blockReactRadius),
			v -> v.isAlive() && !v.isNoAi() && !v.isSleeping() && v.distanceToSqr(center) <= cfg.blockReactRadius * cfg.blockReactRadius);
		villagers.sort(Comparator.comparingDouble(v -> v.distanceToSqr(center)));

		long now = level.getGameTime();
		int speakers = 0;
		for (Villager v : villagers) {
			VillagerState state = LivelyVillagers.state(v);
			Feeling feeling = feel(v, placed);
			boolean tooClose = v.distanceToSqr(center) <= 1.6 * 1.6 && !isBoxedIn(v, level);
			if (feeling == Feeling.NEUTRAL && !tooClose && LivelyVillagers.mind(v).personality() != Personality.CURIOUS) {
				continue;
			}
			v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
			if (speakers >= 2 || now < state.nextBlockReaction) {
				continue;
			}
			Map<String, String> vars = Map.of("block", blockName(placed), "player", player.getScoreboardName());
			if (tooClose && feeling != Feeling.SCARED) {
				v.setUnhappyCounter(20);
				say(v, Topic.TOO_CLOSE, SoundEvents.VILLAGER_NO, vars);
			} else {
				switch (feeling) {
					case LOVE_JOB -> {
						level.broadcastEntityEvent(v, HEARTS);
						say(v, Topic.LOVE_JOB_SITE, SoundEvents.VILLAGER_CELEBRATE, vars);
					}
					case WANT_JOB -> {
						level.broadcastEntityEvent(v, HAPPY);
						say(v, Topic.WANT_JOB_SITE, SoundEvents.VILLAGER_YES, vars);
					}
					case LIKE -> {
						level.broadcastEntityEvent(v, HAPPY);
						say(v, Topic.LIKE_BLOCK, SoundEvents.VILLAGER_YES, vars);
					}
					case DISLIKE -> {
						level.broadcastEntityEvent(v, ANGRY);
						v.setUnhappyCounter(40);
						say(v, Topic.DISLIKE_BLOCK, SoundEvents.VILLAGER_NO, vars);
					}
					case SCARED -> {
						level.broadcastEntityEvent(v, SWEAT);
						say(v, Topic.SCARY_BLOCK, SoundEvents.VILLAGER_HURT, vars);
					}
					case NEUTRAL -> {
						if (v.getRandom().nextInt(4) != 0) {
							continue;
						}
						say(v, Topic.CURIOUS_BLOCK, SoundEvents.VILLAGER_AMBIENT, vars);
					}
				}
			}
			state.nextBlockReaction = now + 80;
			speakers++;
		}
	}

	public static void onBlockBroken(ServerLevel level, Player player, BlockPos pos, BlockState broken) {
		if (!LivelyConfig.get().blockReactions) {
			return;
		}
		GlobalPos here = GlobalPos.of(level.dimension(), pos);
		Vec3 center = Vec3.atCenterOf(pos);
		double r = LivelyConfig.get().blockReactRadius;
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(r),
			v -> v.isAlive() && !v.isNoAi() && v.distanceToSqr(center) <= r * r);
		villagers.sort(Comparator.comparingDouble(v -> v.distanceToSqr(center)));
		Map<String, String> vars = Map.of("block", blockName(broken), "player", player.getScoreboardName());
		boolean someoneMourned = false;
		for (Villager v : villagers) {
			Brain<Villager> brain = v.getBrain();
			Topic topic = null;
			if (brain.getMemory(MemoryModuleType.JOB_SITE).filter(here::equals).isPresent()
				|| brain.getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).filter(here::equals).isPresent()) {
				topic = Topic.BROKE_JOB_SITE;
			} else if (broken.getBlock() instanceof BedBlock && brain.getMemory(MemoryModuleType.HOME)
				.filter(home -> home.dimension() == level.dimension() && home.pos().distManhattan(pos) <= 1).isPresent()) {
				topic = Topic.BROKE_BED;
			} else if (broken.getBlock() instanceof BellBlock && brain.getMemory(MemoryModuleType.MEETING_POINT).filter(here::equals).isPresent()) {
				topic = Topic.BROKE_BELL;
			}
			if (topic != null) {
				level.broadcastEntityEvent(v, ANGRY);
				v.setUnhappyCounter(40);
				lookAt(v, player);
				say(v, topic, SoundEvents.VILLAGER_NO, vars);
			} else if (!someoneMourned && !v.isSleeping() && feel(v, broken) == Feeling.LIKE && v.getRandom().nextBoolean()) {
				someoneMourned = true;
				lookAt(v, player);
				say(v, Topic.BROKE_LIKED, SoundEvents.VILLAGER_NO, vars);
			}
		}
	}

	private static Feeling feel(Villager v, BlockState state) {
		Optional<Holder<PoiType>> poi = PoiTypes.forState(state);
		VillagerProfession job = v.getVillagerData().getProfession();
		if (poi.isPresent() && !v.isBaby()) {
			if (job != VillagerProfession.NONE && job != VillagerProfession.NITWIT && job.heldJobSite().test(poi.get())) {
				return Feeling.LOVE_JOB;
			}
			if (job == VillagerProfession.NONE && VillagerProfession.NONE.acquirableJobSite().test(poi.get())) {
				return Feeling.WANT_JOB;
			}
		}
		if (state.is(Blocks.TNT)) {
			return Feeling.SCARED;
		}
		if (state.is(Blocks.WITHER_ROSE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.COBWEB)
			|| state.is(Blocks.SCULK_SHRIEKER) || state.is(Blocks.SCULK_CATALYST) || state.getBlock() instanceof AbstractSkullBlock) {
			return Feeling.DISLIKE;
		}
		if (state.is(BlockTags.FLOWERS) || state.is(BlockTags.FLOWER_POTS) || state.is(BlockTags.BEDS)
			|| state.is(BlockTags.CANDLES) || state.is(BlockTags.CANDLE_CAKES) || state.is(BlockTags.BANNERS)
			|| state.is(BlockTags.CROPS) || state.is(Blocks.CAKE) || state.is(Blocks.BELL) || state.is(Blocks.LANTERN)
			|| state.is(Blocks.SOUL_LANTERN) || state.is(Blocks.JACK_O_LANTERN) || state.is(Blocks.HAY_BLOCK)
			|| state.is(Blocks.BOOKSHELF) || state.is(Blocks.SEA_LANTERN) || state.is(Blocks.GLOWSTONE)) {
			return Feeling.LIKE;
		}
		return Feeling.NEUTRAL;
	}

	// ---------------------------------------------------------------- gifts

	/** Ticks a villager ignores further gifts after taking one. */
	private static final int GIFT_COOLDOWN = 60;

	/** Gifts only count when a player threw them; dropped flowers from farms are left alone. */
	public static boolean isPlayerGift(ItemEntity item) {
		return isGift(item.getItem()) && item.getOwner() instanceof Player;
	}

	public static boolean acceptsGiftNow(Villager v) {
		return !v.isSleeping() && v.level().getGameTime() >= LivelyVillagers.state(v).nextGiftAt;
	}

	public static boolean isGift(ItemStack stack) {
		return isLovedGift(stack) || (stack.is(ItemTags.FLOWERS) && !stack.is(Items.WITHER_ROSE))
			|| stack.is(Items.COOKIE) || stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES);
	}

	private static boolean isLovedGift(ItemStack stack) {
		return stack.is(Items.CAKE) || stack.is(Items.PUMPKIN_PIE) || stack.is(Items.HONEY_BOTTLE);
	}

	/** Called instead of the vanilla pickup for gift items: the villager takes one, holds it, and reacts. */
	public static void onGift(Villager v, ItemEntity itemEntity) {
		if (!(v.level() instanceof ServerLevel level)) {
			return;
		}
		ItemStack stack = itemEntity.getItem();
		Item item = stack.getItem();
		boolean loved = isLovedGift(stack);
		LivelyVillagers.state(v).nextGiftAt = level.getGameTime() + GIFT_COOLDOWN;
		v.take(itemEntity, 1);
		stack.shrink(1);
		if (stack.isEmpty()) {
			itemEntity.discard();
		}

		VillagerState state = LivelyVillagers.state(v);
		if (!v.isTrading() && (v.getMainHandItem().isEmpty() || state.heldGift != null)) {
			v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
			state.heldGift = item;
			state.clearHandAt = level.getGameTime() + 100;
		}

		String itemName = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
		Entity owner = itemEntity.getOwner();
		if (!(owner instanceof ServerPlayer player)) {
			level.broadcastEntityEvent(v, HAPPY);
			say(v, Topic.FOUND_GIFT, SoundEvents.VILLAGER_YES, Map.of("item", itemName));
			return;
		}
		lookAt(v, player);
		Map<String, String> vars = Map.of("item", itemName, "player", player.getScoreboardName());
		VillagerMind mind = LivelyVillagers.mind(v);
		long day = level.getDayTime() / 24000L;
		if (mind.lastGiftDay().getOrDefault(player.getUUID(), -1L) == day) {
			level.broadcastEntityEvent(v, HAPPY);
			say(v, Topic.GIFT_AGAIN, SoundEvents.VILLAGER_YES, vars);
			return;
		}
		int rep = loved ? LivelyConfig.get().lovedGiftReputation : LivelyConfig.get().giftReputation;
		if (rep > 0) {
			v.getGossips().add(player.getUUID(), GossipType.MINOR_POSITIVE, rep);
		}
		LivelyVillagers.setMind(v, mind.withGift(player.getUUID(), day));
		level.broadcastEntityEvent(v, HEARTS);
		say(v, loved ? Topic.GIFT_LOVE : Topic.GIFT_LIKE, SoundEvents.VILLAGER_CELEBRATE, vars);
	}

	// ---------------------------------------------------------------- talking

	/**
	 * Plain right-click. Vanilla still runs afterwards: workers open trades, the jobless shake their heads
	 * (and already play the "no" sound, so no extra sound for them).
	 */
	public static void onClicked(Villager v, Player player) {
		if (!clickCooldownOver(v)) {
			return;
		}
		lookAt(v, player);
		Map<String, String> vars = Map.of("player", player.getScoreboardName());
		VillagerProfession job = v.getVillagerData().getProfession();
		if (v.isBaby()) {
			say(v, Topic.CLICK_BABY, null, vars);
		} else if (job == VillagerProfession.NONE || job == VillagerProfession.NITWIT) {
			say(v, isSleepyTime(v) ? Topic.CLICK_JOBLESS_NIGHT : Topic.CLICK_JOBLESS, null, vars);
		} else if (v.getPlayerReputation(player) <= -10) {
			say(v, Topic.GREET_COLD, SoundEvents.VILLAGER_NO, vars);
		} else {
			say(v, isSleepyTime(v) ? Topic.CLICK_WORKER_NIGHT : Topic.CLICK_WORKER, SoundEvents.VILLAGER_TRADE, vars);
		}
	}

	/** Right-clicking a sleeping villager: they mumble in their sleep (and stay asleep). */
	public static void onClickedSleeping(Villager v) {
		if (clickCooldownOver(v)) {
			say(v, Topic.SLEEP_TALK, null, Map.of());
		}
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

	public static void introduce(Villager v, Player player) {
		if (!clickCooldownOver(v)) {
			return;
		}
		lookAt(v, player);
		Map<String, String> vars = new HashMap<>();
		vars.put("player", player.getScoreboardName());
		vars.put("personality", LivelyVillagers.mind(v).personality().displayName().toLowerCase(Locale.ROOT));
		say(v, v.isBaby() ? Topic.GREET_BABY : Topic.INTRO, SoundEvents.VILLAGER_AMBIENT, vars);
	}

	/** Dev film only: the next line spoken, instead of a random pick. */
	public static String nextLineOverride;

	/** How far away a player can be and still hear villagers talk. */
	private static final double HEARING_RANGE = 32.0;

	public static void say(Villager v, Topic topic, SoundEvent sound, Map<String, String> vars) {
		// Farms in spawn chunks or chunk-loaded bases would otherwise churn bubbles and sounds all day.
		if (!(v.level() instanceof ServerLevel level) || !anyoneListening(level, v)) {
			nextLineOverride = null;
			return;
		}
		VillagerMind mind = LivelyVillagers.mind(v);
		String picked = nextLineOverride != null ? nextLineOverride : Lines.pick(topic, mind.personality(), v.getRandom());
		nextLineOverride = null;
		String line = picked
			.replace("{name}", displayName(v))
			.replace("{job}", jobName(v));
		for (Map.Entry<String, String> e : vars.entrySet()) {
			line = line.replace("{" + e.getKey() + "}", e.getValue());
		}
		float pitch = mind.personality().pitch * (v.isBaby() ? 1.4F : 1.0F) + (v.getRandom().nextFloat() - 0.5F) * 0.1F;
		if (sound != null) {
			v.playSound(sound, 1.0F, pitch);
		}
		if (LivelyConfig.get().speechBubbles) {
			SpeechBubbles.show(v, line);
		}
		LivelyVillagers.trace(topic.name(), v, line);
	}

	private static boolean anyoneListening(ServerLevel level, Villager v) {
		return level.getNearestPlayer(v.getX(), v.getY(), v.getZ(), HEARING_RANGE, p -> !p.isSpectator()) != null;
	}

	public static String displayName(Villager v) {
		return v.hasCustomName() ? v.getCustomName().getString() : LivelyVillagers.mind(v).name();
	}

	public static String jobName(Villager v) {
		VillagerProfession p = v.getVillagerData().getProfession();
		if (p == VillagerProfession.NONE) {
			return "Villager";
		}
		String n = p.name();
		return Character.toUpperCase(n.charAt(0)) + n.substring(1);
	}

	private static String blockName(BlockState state) {
		return state.getBlock().getName().getString().toLowerCase(Locale.ROOT);
	}

	private static void lookAt(Villager v, LivingEntity target) {
		BehaviorUtils.lookAtEntity(v, target);
		v.getLookControl().setLookAt(target, 30.0F, 30.0F);
	}

	private Reactions() {
	}
}
