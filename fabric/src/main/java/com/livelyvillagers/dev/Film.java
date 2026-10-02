package com.livelyvillagers.dev;

import com.livelyvillagers.Greetings;
import com.livelyvillagers.LivelyVillagers;
import com.livelyvillagers.Personality;
import com.livelyvillagers.TestHooks;
import com.livelyvillagers.VillagerState;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Dev-only (-Dlivelyvillagers.film=true): stages the showcase video's shots in real villages with
 * villager AI on, drives the camera, and tells the client recorder when each clip starts and stops.
 */
public final class Film {
	private record Step(int delay, Runnable action) {
	}

	// Villages in the fixed showcase seed (20261002) whose streets stage well.
	private static final BlockPos PLAINS_BELL_NEAR = new BlockPos(144, 70, 656);
	private static final BlockPos SNOWY_BELL_NEAR = new BlockPos(-2144, 66, -816);
	private static final double PRE_ROLL = 1.0;

	private static final Deque<Step> steps = new ArrayDeque<>();
	private static final List<Entity> cast = new ArrayList<>();
	private static int wait = -1;

	public static void install() {
		TestHooks.forcedChance = 1.0F;
		TestHooks.bubbleScale = 1.35F;
		FilmState.active = true;
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			Showcase.player = handler.getPlayer();
			script();
			wait = 80;
		});
		ServerTickEvents.END_SERVER_TICK.register(Film::tick);
	}

	private static void tick(MinecraftServer server) {
		if (wait < 0) {
			return;
		}
		if (wait > 0) {
			wait--;
			return;
		}
		Step step = steps.poll();
		if (step == null) {
			wait = -1;
			LivelyVillagers.LOGGER.info("FILM DONE");
			FilmState.finished = true;
			return;
		}
		try {
			step.action.run();
		} catch (RuntimeException e) {
			LivelyVillagers.LOGGER.error("FILM step failed", e);
		}
		wait = steps.isEmpty() ? 20 : steps.peek().delay;
	}

	private static void then(int delayTicks, Runnable action) {
		steps.add(new Step(delayTicks, action));
	}

	private static int ticks(double seconds) {
		return (int) Math.round(seconds * 20);
	}

	/**
	 * One clip: set the scene and camera, let chunks render, record `seconds` (plus pre-roll), firing
	 * the timed actions (seconds from the clip's t=0, after the pre-roll).
	 */
	private static void shot(String name, double seconds, Runnable setup, FilmState.CameraPath camera, Object... timed) {
		String only = System.getProperty("livelyvillagers.film.only");
		if (only != null && !only.isEmpty() && !java.util.Arrays.asList(only.split(",")).contains(name)) {
			return;
		}
		then(10, () -> {
			clearCast();
			setup.run();
			// Hold the camera at the first pose while the area renders.
			FilmState.camera = camera;
			FilmState.cameraStartNanos = System.nanoTime() + (long) (6.0e9);
		});
		then(ticks(6.0 - PRE_ROLL), () -> {
			FilmState.recording = name;
			LivelyVillagers.LOGGER.info("FILM recording {}", name);
		});
		double prev = -PRE_ROLL;
		for (int i = 0; i < timed.length; i += 2) {
			double at = ((Number) timed[i]).doubleValue();
			Runnable action = (Runnable) timed[i + 1];
			then(ticks(at - prev), action);
			prev = at;
		}
		then(ticks(seconds + 0.6 - prev), () -> {
			FilmState.recording = null;
			LivelyVillagers.LOGGER.info("FILM stopped {}", name);
		});
	}

	// ------------------------------------------------------------------ the script

	private static void script() {
		then(0, () -> {
			Showcase.level = Showcase.player.serverLevel();
			GameRules rules = Showcase.level.getGameRules();
			rules.getRule(GameRules.RULE_DAYLIGHT).set(false, Showcase.level.getServer());
			rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, Showcase.level.getServer());
			rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, Showcase.level.getServer());
			Showcase.level.setWeatherParameters(12000, 0, false, false);
			Showcase.player.setGameMode(GameType.CREATIVE);
			Showcase.player.getAbilities().flying = true;
			Showcase.player.onUpdateAbilities();
		});

		goTo(PLAINS_BELL_NEAR, 2500);

		// 02 — village life: drift in over the village, two villagers introduce themselves.
		shot("02-village-life", 6.5, () -> {
			for (int i = 0; i < 5; i++) {
				villager(randomNear(bell(), 6), i % 2 == 0 ? VillagerProfession.FARMER : VillagerProfession.NONE, 0.5);
			}
		}, t -> {
			double k = smooth(t / 6.5);
			Vec3 from = S().subtract(L().scale(20)).add(0, 12, 0);
			Vec3 to = S().subtract(L().scale(9)).add(0, 6.5, 0);
			return pose(lerp(from, to, k), Vec3.atBottomCenterOf(bell()).add(0, 1, 0));
		}, -0.5, (Runnable) () -> TestHooks.bubbleScale = 2.4F, 1.0, (Runnable) () -> introduceNearest(0),
			2.4, (Runnable) () -> introduceNearest(1), 6.0, (Runnable) () -> TestHooks.bubbleScale = 1.35F);

		// 03 — walk-by: stroll up the street toward the bell; villagers along it say hi as you pass.
		shot("03-walk-by", 7.0, () -> {
		}, t -> {
			// Constant walking pace with eased start and stop; no head-bob.
			double k = easeInOutLinear(t / 7.0, 0.12);
			Vec3 p = S().add(L().scale(-10 + 10.5 * k));
			Vec3 eye = new Vec3(p.x, S().y + 1.62, p.z);
			// One slow look from the left side of the street to the right, instead of a back-and-forth.
			double glance = -14 + 28 * smooth(t / 7.0);
			Vec3 dir = L().scale(Math.cos(Math.toRadians(glance))).add(R().scale(Math.sin(Math.toRadians(glance))));
			return pose(eye, eye.add(dir).add(0, -0.1, 0));
		}, -0.8, (Runnable) () -> {
			villager(on(-2.4, -4.5), VillagerProfession.FARMER, 0.0);
			villager(on(2.4, -1.5), VillagerProfession.LIBRARIAN, 0.0);
			villager(on(-2.2, 1.5), VillagerProfession.NONE, 0.0);
		});

		// 04 — reactions: place a flower and a lectern; real BlockItem placement triggers them.
		shot("04-reactions", 6.0, () -> {
			clearResidents();
		}, t -> {
			double k = smooth(t / 6.0);
			Vec3 cam = lerp(on(0, -4.0).add(0, 2.0, 0), on(0, -3.3).add(0, 1.85, 0), k);
			return pose(cam, on(0, 0.4).add(0, 1.3, 0));
		}, -0.8, (Runnable) () -> {
			quiet(villager(on(-1.2, 0.6), VillagerProfession.FARMER, 0.0));
			quiet(villager(on(1.3, 0.6), VillagerProfession.LIBRARIAN, 0.0));
		}, 1.0, (Runnable) () -> place(Items.POPPY, on(-2.2, -0.6)), 3.0, (Runnable) () -> place(Items.LECTERN, on(2.5, -0.5)));

		// 05 — gift: toss a poppy; the villager walks over, takes it and holds it.
		shot("05-gift", 7.0, () -> {
			clearResidents();
		}, t -> {
			double k = smooth(t / 7.0);
			Vec3 cam = lerp(on(0.6, -3.2).add(0, 1.75, 0), on(0.2, -2.4).add(0, 1.7, 0), k);
			return pose(cam, on(0, 1.6).add(0, 1.0, 0));
		}, -0.8, (Runnable) () -> quiet(villager(on(0, 3.0), VillagerProfession.FARMER, 0.0)),
			1.0, (Runnable) () -> toss(Items.POPPY, on(0, 1.4)), 1.3, (Runnable) () -> setSpeed(0.5));

		// 06 — danger: a creeper comes down the street; the villagers scatter and shout.
		shot("06-danger", 7.0, () -> {
			clearResidents();
		}, t -> {
			double k = smooth(t / 7.0);
			Vec3 cam = lerp(on(0, -6.5).add(0, 2.6, 0), on(0, -6.0).add(0, 2.4, 0), k);
			return pose(cam, on(0, 1.8).add(0, 0.9, 0));
		}, -0.8, (Runnable) () -> {
			quiet(villager(on(-1.8, 0.4), VillagerProfession.FARMER, 0.0));
			quiet(villager(on(0.2, 1.0), VillagerProfession.NONE, 0.0));
			quiet(villager(on(1.9, 0.2), VillagerProfession.FLETCHER, 0.0));
		}, 0.6, (Runnable) Film::creeper, 1.0, (Runnable) () -> setSpeed(0.5), 1.6, (Runnable) Film::steerCreeper,
			2.6, (Runnable) Film::steerCreeper, 3.6, (Runnable) Film::steerCreeper, 4.6, (Runnable) Film::steerCreeper);

		// 07 — poke: right-click a nitwit.
		shot("07-poke", 4.5, () -> {
			clearResidents();
		}, t -> {
			double k = smooth(t / 4.5);
			Vec3 cam = lerp(on(0.15, -1.9).add(0, 1.62, 0), on(0.1, -1.4).add(0, 1.62, 0), k);
			return pose(cam, on(0, 1.0).add(0, 1.55, 0));
		}, -0.8, (Runnable) () -> {
			Villager v = quiet(villager(on(0, 1.0), VillagerProfession.NITWIT, 0.0));
			LivelyVillagers.setMind(v, LivelyVillagers.mind(v).withPersonality(Personality.GRUMPY));
		}, 1.2, (Runnable) () -> {
			TestHooks.nextLineOverride = "Stop touching me!";
			poke();
		});

		goTo(SNOWY_BELL_NEAR, 12600);

		// 08 — night: at sunset the villagers announce bedtime and start heading home.
		shot("08-night", 7.5, () -> {
		}, t -> {
			double k = smooth(t / 7.5);
			Vec3 cam = lerp(on(-0.3, -4.4).add(0, 2.0, 0), on(0.1, -3.2).add(0, 1.85, 0), k);
			return pose(cam, on(0, 1.2).add(0, 1.4, 0));
		}, -0.9, (Runnable) () -> TestHooks.bubbleScale = 1.4F, -0.8, (Runnable) () -> {
			villager(on(-2.7, 1.0), VillagerProfession.FARMER, 0.0);
			villager(on(0.0, 2.0), VillagerProfession.LIBRARIAN, 0.0);
			villager(on(2.7, 0.9), VillagerProfession.NONE, 0.0);
		}, 3.4, (Runnable) () -> setSpeed(0.5));

		then(20, Film::clearCast);
	}

	// ------------------------------------------------------------------ village + stage

	private static void goTo(BlockPos near, long time) {
		then(10, () -> {
			clearCast();
			FilmState.camera = null;
			Showcase.level.setDayTime(time);
			Showcase.player.teleportTo(Showcase.level, near.getX() + 0.5, 140, near.getZ() + 0.5, 0, 90);
		});
		then(160, () -> {
			BlockPos bell = Showcase.level.getPoiManager()
				.findClosest(h -> h.is(PoiTypes.MEETING), near.atY(80), 96, PoiManager.Occupancy.ANY)
				.orElse(Showcase.ground(near));
			bellPos = bell;
			if (!Showcase.pickStage(bell)) {
				LivelyVillagers.LOGGER.warn("FILM no stage near {}", bell);
			}
			Vec3 cam = S().subtract(L().scale(10)).add(0, 6, 0);
			Showcase.aim(cam, S());
		});
		then(100, () -> {
		});
	}

	private static BlockPos bellPos;

	private static BlockPos bell() {
		return bellPos;
	}

	private static Vec3 S() {
		return Vec3.atBottomCenterOf(Showcase.stage);
	}

	private static Vec3 L() {
		return Showcase.look;
	}

	private static Vec3 R() {
		return new Vec3(-Showcase.look.z, 0, Showcase.look.x);
	}

	/** A ground-level point on the stage: `side` across the view, `depth` toward the village. */
	private static Vec3 on(double side, double depth) {
		Vec3 p = S().add(R().scale(side)).add(L().scale(depth));
		return new Vec3(p.x, Showcase.ground(BlockPos.containing(p)).getY(), p.z);
	}

	private static Vec3 randomNear(BlockPos center, int r) {
		var rnd = Showcase.level.getRandom();
		for (int i = 0; i < 20; i++) {
			BlockPos p = Showcase.ground(center.offset(rnd.nextInt(2 * r + 1) - r, 0, rnd.nextInt(2 * r + 1) - r));
			if (Math.abs(p.getY() - center.getY()) <= 2 && Showcase.level.getBlockState(p).canBeReplaced()) {
				return Vec3.atBottomCenterOf(p);
			}
		}
		return Vec3.atBottomCenterOf(Showcase.ground(center.offset(2, 0, 2)));
	}

	// ------------------------------------------------------------------ cast and actions

	private static Villager villager(Vec3 p, VillagerProfession job, double speed) {
		Villager v = EntityType.VILLAGER.spawn(Showcase.level, BlockPos.containing(p), MobSpawnType.COMMAND);
		v.moveTo(p.x, p.y, p.z, Showcase.level.getRandom().nextFloat() * 360, 0);
		v.setVillagerData(v.getVillagerData().setProfession(job));
		v.setVillagerXp(job == VillagerProfession.NONE || job == VillagerProfession.NITWIT ? 0 : 5);
		v.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
		cast.add(v);
		return v;
	}

	/** Village residents near the stage would wander into close-ups (and steal gifts): move them on. */
	private static void clearResidents() {
		for (Villager v : Showcase.level.getEntitiesOfClass(Villager.class, new AABB(Showcase.stage).inflate(14), v -> !cast.contains(v))) {
			v.discard();
		}
	}

	/** This villager already knows the player, so it won't open with a greeting. */
	private static Villager quiet(Villager v) {
		VillagerState state = LivelyVillagers.state(v);
		state.playersInRange.add(Showcase.player.getUUID());
		state.lastGreeted.put(Showcase.player.getUUID(), Long.MAX_VALUE / 2);
		return v;
	}

	private static void setSpeed(double speed) {
		for (Entity e : cast) {
			if (e instanceof Villager v) {
				v.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
			}
		}
	}

	private static void introduceNearest(int index) {
		List<Villager> vs = new ArrayList<>(cast.stream().filter(e -> e instanceof Villager).map(e -> (Villager) e).toList());
		vs.sort((a, b) -> Double.compare(a.distanceToSqr(Showcase.player), b.distanceToSqr(Showcase.player)));
		if (index < vs.size()) {
			Greetings.introduce(vs.get(index), Showcase.player);
		}
	}

	private static final java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> placed = new java.util.LinkedHashMap<>();

	private static void place(Item item, Vec3 at) {
		BlockPos ground = BlockPos.containing(at).below();
		placed.putIfAbsent(ground.above(), Showcase.level.getBlockState(ground.above()));
		Showcase.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
		Showcase.player.gameMode.useItemOn(Showcase.player, Showcase.level, Showcase.player.getMainHandItem(), InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false));
		Showcase.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
	}

	private static void toss(Item item, Vec3 target) {
		Vec3 eye = Showcase.player.getEyePosition();
		Vec3 d = target.subtract(eye);
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		// Start a little in front of the camera so the flower is seen flying, not filling the lens.
		Vec3 from = eye.add(d.x / flat * 1.3, -0.45, d.z / flat * 1.3);
		ItemEntity gift = new ItemEntity(Showcase.level, from.x, from.y, from.z, new ItemStack(item));
		gift.setDeltaMovement(d.x / flat * 0.25, 0.2, d.z / flat * 0.25);
		gift.setThrower(Showcase.player);
		gift.setPickUpDelay(10);
		Showcase.level.addFreshEntity(gift);
		cast.add(gift);
	}

	private static Creeper creeperEntity;

	private static void creeper() {
		Vec3 p = on(0.5, 8.0);
		creeperEntity = EntityType.CREEPER.spawn(Showcase.level, BlockPos.containing(p), MobSpawnType.COMMAND);
		cast.add(creeperEntity);
		steerCreeper();
	}

	private static void steerCreeper() {
		if (creeperEntity != null && creeperEntity.isAlive()) {
			Vec3 target = on(0, 0.0);
			creeperEntity.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
		}
	}

	static void poke() {
		for (Entity e : cast) {
			if (e instanceof Villager v) {
				LivelyVillagers.onUseEntity(Showcase.player, Showcase.level, InteractionHand.MAIN_HAND, v);
				// The vanilla side too: head shake and the "no" sound.
				Showcase.player.interactOn(v, InteractionHand.MAIN_HAND);
				return;
			}
		}
	}

	private static void clearCast() {
		placed.forEach((pos, state) -> Showcase.level.setBlock(pos, state, 3));
		placed.clear();
		cast.forEach(Entity::discard);
		cast.clear();
		creeperEntity = null;
		if (Showcase.level != null && Showcase.stage != null) {
			// Leftover items and blocks from earlier shots.
			for (ItemEntity it : Showcase.level.getEntitiesOfClass(ItemEntity.class, new AABB(Showcase.stage).inflate(20))) {
				it.discard();
			}
		}
	}

	// ------------------------------------------------------------------ camera math

	/** Linear motion with short eased ramps at each end (ramp = fraction of the clip). */
	private static double easeInOutLinear(double k, double ramp) {
		k = Math.min(1, Math.max(0, k));
		double v = 1 / (1 - ramp);
		if (k < ramp) {
			return v * k * k / (2 * ramp);
		}
		if (k > 1 - ramp) {
			double r = 1 - k;
			return 1 - v * r * r / (2 * ramp);
		}
		return v * (k - ramp / 2);
	}

	private static double smooth(double k) {
		k = Math.min(1, Math.max(0, k));
		return k * k * (3 - 2 * k);
	}

	private static Vec3 lerp(Vec3 a, Vec3 b, double k) {
		return a.add(b.subtract(a).scale(k));
	}

	private static FilmState.Pose pose(Vec3 eye, Vec3 target) {
		Vec3 d = target.subtract(eye);
		float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		return new FilmState.Pose(eye.x, eye.y, eye.z, yaw, pitch);
	}

	private Film() {
	}
}
