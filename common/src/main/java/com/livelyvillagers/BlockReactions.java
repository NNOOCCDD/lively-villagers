package com.livelyvillagers;

import com.livelyvillagers.Lines.Topic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Villagers react to blocks players place and break near them. */
public final class BlockReactions {
	private enum Feeling { LOVE_JOB, WANT_JOB, LIKE, DISLIKE, SCARED, NEUTRAL }

	/** At most this many villagers speak about one block. */
	private static final int MAX_SPEAKERS = 2;
	/** Ticks before the same villager comments on another block. */
	private static final int COOLDOWN = 80;

	public static void onBlockPlaced(ServerLevel level, BlockPos pos, BlockState placed, Player player) {
		LivelyConfig cfg = LivelyConfig.get();
		if (!cfg.blockReactions) {
			return;
		}
		Vec3 center = Vec3.atCenterOf(pos);
		List<Villager> villagers = villagersAround(level, center, cfg.blockReactRadius, true);
		long now = level.getGameTime();
		int speakers = 0;
		for (Villager v : villagers) {
			VillagerState state = LivelyVillagers.state(v);
			Feeling feeling = feel(v, placed);
			// Building a trading-hall cell around a villager shouldn't make them complain every block.
			boolean tooClose = v.distanceToSqr(center) <= 1.6 * 1.6 && !Greetings.isBoxedIn(v, level);
			if (feeling == Feeling.NEUTRAL && !tooClose && LivelyVillagers.mind(v).personality() != Personality.CURIOUS) {
				continue;
			}
			v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
			if (speakers >= MAX_SPEAKERS || now < state.nextBlockReaction) {
				continue;
			}
			Map<String, String> vars = Map.of("block", blockName(placed), "player", player.getScoreboardName());
			if (tooClose && feeling != Feeling.SCARED) {
				v.setUnhappyCounter(20);
				Speech.say(v, Topic.TOO_CLOSE, SoundEvents.VILLAGER_NO, vars);
			} else if (!react(v, level, feeling, vars)) {
				continue;
			}
			state.nextBlockReaction = now + COOLDOWN;
			speakers++;
		}
	}

	/** Speaks about a placed block; false when the villager decided to stay quiet. */
	private static boolean react(Villager v, ServerLevel level, Feeling feeling, Map<String, String> vars) {
		switch (feeling) {
			case LOVE_JOB -> {
				level.broadcastEntityEvent(v, Speech.HEARTS);
				Speech.say(v, Topic.LOVE_JOB_SITE, SoundEvents.VILLAGER_CELEBRATE, vars);
			}
			case WANT_JOB -> {
				level.broadcastEntityEvent(v, Speech.HAPPY);
				Speech.say(v, Topic.WANT_JOB_SITE, SoundEvents.VILLAGER_YES, vars);
			}
			case LIKE -> {
				level.broadcastEntityEvent(v, Speech.HAPPY);
				Speech.say(v, Topic.LIKE_BLOCK, SoundEvents.VILLAGER_YES, vars);
			}
			case DISLIKE -> {
				level.broadcastEntityEvent(v, Speech.ANGRY);
				v.setUnhappyCounter(40);
				Speech.say(v, Topic.DISLIKE_BLOCK, SoundEvents.VILLAGER_NO, vars);
			}
			case SCARED -> {
				level.broadcastEntityEvent(v, Speech.SWEAT);
				Speech.say(v, Topic.SCARY_BLOCK, SoundEvents.VILLAGER_HURT, vars);
			}
			case NEUTRAL -> {
				// Only curious villagers get here; they comment now and then.
				if (v.getRandom().nextInt(4) != 0) {
					return false;
				}
				Speech.say(v, Topic.CURIOUS_BLOCK, SoundEvents.VILLAGER_AMBIENT, vars);
			}
		}
		return true;
	}

	public static void onBlockBroken(ServerLevel level, Player player, BlockPos pos, BlockState broken) {
		LivelyConfig cfg = LivelyConfig.get();
		if (!cfg.blockReactions) {
			return;
		}
		GlobalPos here = GlobalPos.of(level.dimension(), pos);
		List<Villager> villagers = villagersAround(level, Vec3.atCenterOf(pos), cfg.blockReactRadius, false);
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
				level.broadcastEntityEvent(v, Speech.ANGRY);
				v.setUnhappyCounter(40);
				Speech.lookAt(v, player);
				Speech.say(v, topic, SoundEvents.VILLAGER_NO, vars);
			} else if (!someoneMourned && !v.isSleeping() && feel(v, broken) == Feeling.LIKE && v.getRandom().nextBoolean()) {
				someoneMourned = true;
				Speech.lookAt(v, player);
				Speech.say(v, Topic.BROKE_LIKED, SoundEvents.VILLAGER_NO, vars);
			}
		}
	}

	private static List<Villager> villagersAround(ServerLevel level, Vec3 center, double r, boolean awakeOnly) {
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class, AABB.ofSize(center, r * 2, r * 2, r * 2),
			v -> v.isAlive() && !v.isNoAi() && !(awakeOnly && v.isSleeping()) && v.distanceToSqr(center) <= r * r);
		villagers.sort(Comparator.comparingDouble(v -> v.distanceToSqr(center)));
		return villagers;
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

	private static String blockName(BlockState state) {
		return state.getBlock().getName().getString().toLowerCase(Locale.ROOT);
	}

	private BlockReactions() {
	}
}
