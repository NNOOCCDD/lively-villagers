package com.livelyvillagers;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dev-only (-Dlivelyvillagers.showcase=true): finds real villages, stages villagers in front of the
 * buildings with speech bubbles and takes hero screenshots from a spectator camera.
 */
public final class Showcase {
	private record Step(int delay, Runnable action) {
	}

	private static final byte HEARTS = 12;
	private static final byte ANGRY = 13;
	private static final byte HAPPY = 14;
	private static final byte SWEAT = 42;

	private static final Deque<Step> steps = new ArrayDeque<>();
	private static final List<Entity> actors = new ArrayList<>();
	private static final Map<BlockPos, BlockState> placed = new LinkedHashMap<>();
	static ServerPlayer player;
	static ServerLevel level;
	private static int wait = -1;

	/** The open patch in front of the village where actors stand, and the direction the camera looks. */
	static BlockPos stage;
	static Vec3 look;

	public static void install() {
		SpeechBubbles.scaleBoost = 1.25F;
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			player = handler.getPlayer();
			script();
			wait = 60;
		});
		ServerTickEvents.END_SERVER_TICK.register(Showcase::tick);
	}

	private static void tick(MinecraftServer server) {
		if (wait < 0 || player == null) {
			return;
		}
		if (wait > 0) {
			wait--;
			return;
		}
		Step step = steps.poll();
		if (step == null) {
			wait = -1;
			LivelyVillagers.LOGGER.info("SHOWCASE DONE");
			SelfTest.finished.run();
			return;
		}
		try {
			step.action.run();
		} catch (RuntimeException e) {
			LivelyVillagers.LOGGER.error("SHOWCASE step failed", e);
		}
		wait = steps.isEmpty() ? 40 : steps.peek().delay;
	}

	private static void then(int delay, Runnable action) {
		steps.add(new Step(delay, action));
	}

	/** Stage a scene, give bubbles and particles a moment, shoot, clean up. */
	private static void shot(String name, Runnable setup) {
		then(20, () -> {
			clear();
			setup.run();
		});
		then(30, () -> SelfTest.screenshot.accept(name));
	}

	// ------------------------------------------------------------------ the script

	private static void script() {
		then(0, () -> {
			level = player.serverLevel();
			GameRules rules = level.getGameRules();
			rules.getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
			rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
			rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
			level.setWeatherParameters(12000, 0, false, false);
			player.setGameMode(GameType.SPECTATOR);
		});

		// ---------------- plains, morning
		goToVillage(BuiltinStructures.VILLAGE_PLAINS, 1500);
		shot("hero-01-plains-village", () -> {
			SpeechBubbles.scaleBoost = 1.9F;
			Villager a = actor(-3.0, 0.5, VillagerProfession.FARMER, false, "Hello hello! What a wonderful day!");
			Villager b = actor(-1.0, -0.5, VillagerProfession.LIBRARIAN, false, "Ooh, an adventurer!");
			Villager c = actor(1.0, 0.5, VillagerProfession.NONE, false, "Good day!");
			Villager d = actor(3.0, -0.5, VillagerProfession.ARMORER, false, "Hrmph.");
			Vec3 cam = camera(10, 4.0);
			faceAll(cam, a, b, c, d);
			particles(HAPPY, a, b);
		});
		shot("group-02-greetings", () -> {
			SpeechBubbles.scaleBoost = 1.25F;
			Villager a = actor(-2.1, 0, VillagerProfession.BUTCHER, false, "Hello hello! Take your time!");
			Villager b = actor(0, 0.6, VillagerProfession.TOOLSMITH, false, "Buying or browsing?");
			Villager c = actor(2.1, 0, VillagerProfession.CARTOGRAPHER, false, "Oh! Um... w-welcome.");
			faceAll(camera(6.0, 1.9), a, b, c);
		});
		shot("single-03-gift", () -> {
			Villager a = actor(0, 0, VillagerProfession.FARMER, false, "Aww, you shouldn't have!");
			a.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.POPPY));
			faceAll(camera(3.2, 1.7), a);
			particles(HEARTS, a);
		});
		shot("single-04-new-workstation", () -> {
			Villager a = actor(0.6, 0, VillagerProfession.LIBRARIAN, false, "A new lectern! Just what I needed!");
			block(at(-0.9, 0.3), Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LecternBlock.FACING, facingCamera()));
			faceAll(camera(3.6, 1.8), a);
			particles(HEARTS, a);
		});
		shot("group-05-tnt", () -> {
			block(at(0, 1.4), Blocks.TNT.defaultBlockState());
			Villager a = actor(-1.5, -0.2, VillagerProfession.FISHERMAN, false, "Is that... TNT?!");
			Villager b = actor(1.5, -0.2, VillagerProfession.SHEPHERD, false, "Put that away!");
			faceAll(camera(5.2, 1.9), a, b);
			particles(SWEAT, a, b);
		});
		shot("group-06-creeper-panic", () -> {
			SpeechBubbles.scaleBoost = 1.5F;
			Mob creeper = mob(EntityType.CREEPER, 3.0, -1.2);
			Villager a = actor(-2.2, -0.6, VillagerProfession.FLETCHER, false, "Aaah! Creeper!");
			Villager b = actor(-0.6, -1.2, VillagerProfession.NONE, false, "Creeper! RUN!");
			Villager c = actor(0.9, 0.2, VillagerProfession.MASON, false, "Help!");
			Vec3 cam = camera(7.0, 2.2);
			faceAll(cam, a, b, c);
			face(creeper, a.position());
			particles(SWEAT, a, b, c);
		});
		shot("single-07-jobless-poke", () -> {
			SpeechBubbles.scaleBoost = 1.25F;
			Villager a = actor(0, 0, VillagerProfession.NITWIT, false, "Stop touching me!");
			a.setUnhappyCounter(60);
			faceAll(camera(3.0, 1.7), a);
		});
		shot("single-08-shopkeeper", () -> {
			Villager a = actor(0, 0, VillagerProfession.WEAPONSMITH, false, "Hello! Welcome to my shop!");
			faceAll(camera(3.2, 1.7), a);
		});
		shot("single-09-baby", () -> {
			Villager a = actor(0, 0, VillagerProfession.NONE, true, "Hehe! That tickles!");
			faceAll(camera(2.6, 1.0), a);
			particles(HAPPY, a);
		});

		// ---------------- desert, midday
		goToVillage(BuiltinStructures.VILLAGE_DESERT, 4000);
		shot("hero-10-desert-village", () -> {
			SpeechBubbles.scaleBoost = 1.9F;
			Villager a = actor(-2.4, 0, VillagerProfession.WEAPONSMITH, false, "Emeralds only, friend.");
			Villager b = actor(0, 0.6, VillagerProfession.CLERIC, false, "Best prices in the village!");
			Villager c = actor(2.4, 0, VillagerProfession.NONE, false, "Hello, traveller!");
			faceAll(camera(10, 4.0), a, b, c);
		});
		shot("group-11-husk-attack", () -> {
			SpeechBubbles.scaleBoost = 1.25F;
			Mob husk = mob(EntityType.HUSK, 2.8, -0.8);
			Villager a = actor(-1.8, -0.8, VillagerProfession.FARMER, false, "Help! A Husk!");
			Villager b = actor(0.0, -0.2, VillagerProfession.LEATHERWORKER, false, "Aaaah!");
			faceAll(camera(6.5, 2.0), a, b);
			face(husk, b.position());
			particles(SWEAT, a, b);
		});
		shot("single-12-desert-gift", () -> {
			Villager a = actor(0, 0, VillagerProfession.CLERIC, false, "WOW! A cake! You're the best!");
			a.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CAKE));
			faceAll(camera(3.2, 1.7), a);
			particles(HEARTS, a);
		});

		// ---------------- snowy (or taiga), sunset into night
		goToVillage(BuiltinStructures.VILLAGE_SNOWY, 12600);
		shot("group-13-bedtime", () -> {
			Villager a = actor(-2.1, 0, VillagerProfession.FARMER, false, "Time to turn in for the night.");
			Villager b = actor(0, 0.6, VillagerProfession.LIBRARIAN, false, "*yaaawn*");
			Villager c = actor(2.1, 0, VillagerProfession.NONE, false, "Goodnight, everyone!");
			faceAll(camera(6.0, 2.0), a, b, c);
		});
		shot("single-14-sleep-talk", () -> {
			BlockPos foot = at(0, 0);
			// Sideways to the camera so the sleeper's face is visible.
			Direction dir = facingCamera().getClockWise();
			BlockPos head = foot.relative(dir);
			block(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, dir).setValue(BedBlock.PART, BedPart.FOOT));
			block(head, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, dir).setValue(BedBlock.PART, BedPart.HEAD));
			Villager a = actor(0, 0, VillagerProfession.FARMER, false, null);
			a.startSleeping(head);
			SpeechBubbles.show(a, "Zzz... five more minutes...");
			camera(3.6, 2.2);
		});
		then(20, () -> level.setDayTime(14500));
		shot("single-15-stargazer", () -> {
			Villager a = actor(0, 0, VillagerProfession.CARTOGRAPHER, false, "Ooh, do you see the stars tonight?");
			faceAll(camera(3.2, 1.6), a);
		});
		then(20, Showcase::clear);
	}

	// ------------------------------------------------------------------ village + stage

	private static void goToVillage(ResourceKey<Structure> key, long time) {
		then(20, () -> {
			clear();
			level.setDayTime(time);
		});
		then(0, () -> tryVillage(key, player.blockPosition(), 0));
	}

	/** Fly to the nearest village of this type; if it has no usable stage, try one further away. */
	private static void tryVillage(ResourceKey<Structure> key, BlockPos from, int attempt) {
		BlockPos found = locate(key, from);
		if (found == null && key == BuiltinStructures.VILLAGE_SNOWY) {
			found = locate(BuiltinStructures.VILLAGE_TAIGA, from);
		}
		if (found == null) {
			LivelyVillagers.LOGGER.warn("SHOWCASE no {} found", key.location());
			return;
		}
		final BlockPos village = found;
		// Hover over it so the chunks generate and the POIs (bell) register.
		player.teleportTo(level, village.getX() + 0.5, 140, village.getZ() + 0.5, 0, 90);
		LivelyVillagers.LOGGER.info("SHOWCASE {} attempt {} at {}", key.location(), attempt, village);
		List<Step> next = new ArrayList<>();
		next.add(new Step(160, () -> {
			BlockPos bell = level.getPoiManager()
				.findClosest(h -> h.is(PoiTypes.MEETING), village.atY(100), 96, PoiManager.Occupancy.ANY)
				.orElse(null);
			if (bell == null || !pickStage(bell)) {
				if (attempt < 5) {
					// Search again from well past this village, in a different direction each time.
					int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}};
					BlockPos further = village.offset(dirs[attempt][0] * 900, 0, dirs[attempt][1] * 900);
					steps.addFirst(new Step(0, () -> tryVillage(key, further, attempt + 1)));
				}
				return;
			}
			Vec3 cam = Vec3.atBottomCenterOf(stage).subtract(look.scale(12)).add(0, 8, 0);
			aim(cam, Vec3.atBottomCenterOf(stage));
		}));
		next.add(new Step(140, () -> {
		}));
		for (int i = next.size() - 1; i >= 0; i--) {
			steps.addFirst(next.get(i));
		}
	}

	static BlockPos locate(ResourceKey<Structure> key, BlockPos from) {
		Holder<Structure> structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(key);
		Pair<BlockPos, Holder<Structure>> result = level.getChunkSource().getGenerator()
			.findNearestMapStructure(level, HolderSet.direct(structure), from, 120, false);
		return result == null ? null : result.getFirst();
	}

	/**
	 * Find a flat, open patch at street level near the bell, looking back toward the bell so houses fill
	 * the background, with a clear view from where the cameras will stand.
	 */
	static boolean pickStage(BlockPos bell) {
		BlockPos best = null;
		Vec3 bestLook = null;
		double bestScore = Double.MAX_VALUE;
		for (int r = 5; r <= 18; r++) {
			for (int a = 0; a < 24; a++) {
				double ang = a * Math.PI / 12;
				BlockPos c = ground(bell.offset((int) Math.round(Math.cos(ang) * r), 0, (int) Math.round(Math.sin(ang) * r)));
				// Street level only: roofs and hillsides are what ruined the first takes.
				if (c.getY() < bell.getY() - 2 || c.getY() > bell.getY() + 1 || !flatAndOpen(c)) {
					continue;
				}
				Vec3 toBell = Vec3.atCenterOf(bell).subtract(Vec3.atCenterOf(c));
				Vec3 dir = new Vec3(toBell.x, 0, toBell.z).normalize();
				if (!clearView(c, dir, 6.0, 2.0) || !clearView(c, dir, 10.0, 4.0) || !clearView(c, dir, 3.2, 1.7)) {
					continue;
				}
				double score = Math.abs(r - 9);
				if (score < bestScore) {
					bestScore = score;
					best = c;
					bestLook = dir;
				}
			}
		}
		if (best == null) {
			LivelyVillagers.LOGGER.info("SHOWCASE no street-level stage near bell {}", bell);
			return false;
		}
		stage = best;
		look = bestLook;
		LivelyVillagers.LOGGER.info("SHOWCASE stage {} facing bell {}", stage, bell);
		return true;
	}

	/** Camera spot `distance` back from the stage is near stage height and sees the actors unobstructed. */
	private static boolean clearView(BlockPos c, Vec3 dir, double distance, double height) {
		Vec3 base = Vec3.atBottomCenterOf(c).subtract(dir.scale(distance));
		int floor = ground(BlockPos.containing(base)).getY();
		if (floor > c.getY() + 2 || floor < c.getY() - 3) {
			return false;
		}
		Vec3 cam = new Vec3(base.x, Math.max(c.getY(), floor) + height, base.z);
		Vec3 target = Vec3.atBottomCenterOf(c).add(0, 1.4, 0);
		return level.clip(new net.minecraft.world.level.ClipContext(cam, target,
			net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE,
			net.minecraft.world.phys.shapes.CollisionContext.empty())).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
	}

	/** 5x5 around c within one block of height, nothing overhead, standing on solid ground (not water). */
	private static boolean flatAndOpen(BlockPos c) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockPos p = ground(c.offset(dx, 0, dz));
				BlockState below = level.getBlockState(p.below());
				if (Math.abs(p.getY() - c.getY()) > 1 || !level.canSeeSky(p) || !level.getFluidState(p.below()).isEmpty()
					|| below.is(net.minecraft.tags.BlockTags.ICE) || below.is(Blocks.SNOW_BLOCK) && level.getBlockState(p.below(2)).is(net.minecraft.tags.BlockTags.ICE)
					|| !level.getBlockState(p).canBeReplaced() || !level.getBlockState(p.above()).isAir()) {
					return false;
				}
			}
		}
		return true;
	}

	static BlockPos ground(BlockPos p) {
		return new BlockPos(p.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()), p.getZ());
	}

	/** A spot on the stage: side is left/right across the view, depth is away from the camera. */
	private static BlockPos at(double side, double depth) {
		Vec3 right = new Vec3(-look.z, 0, look.x);
		Vec3 p = Vec3.atBottomCenterOf(stage).add(right.scale(side)).add(look.scale(depth));
		return ground(BlockPos.containing(p));
	}

	private static Vec3 spot(double side, double depth) {
		Vec3 right = new Vec3(-look.z, 0, look.x);
		Vec3 p = Vec3.atBottomCenterOf(stage).add(right.scale(side)).add(look.scale(depth));
		return new Vec3(p.x, ground(BlockPos.containing(p)).getY(), p.z);
	}

	private static Direction facingCamera() {
		return Direction.getNearest(-look.x, 0, -look.z);
	}

	// ------------------------------------------------------------------ actors

	private static Villager actor(double side, double depth, VillagerProfession job, boolean baby, String line) {
		Vec3 p = spot(side, depth);
		Villager v = EntityType.VILLAGER.spawn(level, BlockPos.containing(p), MobSpawnType.COMMAND);
		v.moveTo(p.x, p.y, p.z, 0, 0);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(job == VillagerProfession.NONE ? 1 : 2));
		v.setVillagerXp(10);
		if (baby) {
			v.setAge(-24000);
		}
		actors.add(v);
		if (line != null) {
			SpeechBubbles.show(v, line);
		}
		return v;
	}

	private static Mob mob(EntityType<? extends Mob> type, double side, double depth) {
		Vec3 p = spot(side, depth);
		Mob m = type.spawn(level, BlockPos.containing(p), MobSpawnType.COMMAND);
		m.moveTo(p.x, p.y, p.z, 0, 0);
		m.setNoAi(true);
		actors.add(m);
		return m;
	}

	private static void block(BlockPos pos, BlockState state) {
		placed.putIfAbsent(pos.immutable(), level.getBlockState(pos));
		level.setBlock(pos, state, 3);
	}

	/** Burst now and again just before the screenshot (30 ticks after setup); particles fade in about a second. */
	private static void particles(byte event, Villager... vs) {
		for (int at : new int[] {event == HEARTS ? 22 : 18, 26}) {
			if (event == HEARTS && at == 26) {
				continue;
			}
			pending.add(new int[] {at});
			pendingTargets.add(vs);
			pendingEvents.add(event);
		}
	}

	private static final List<int[]> pending = new ArrayList<>();
	private static final List<Villager[]> pendingTargets = new ArrayList<>();
	private static final List<Byte> pendingEvents = new ArrayList<>();
	private static int sceneAge;

	static {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (pending.isEmpty()) {
				return;
			}
			sceneAge++;
			for (int i = pending.size() - 1; i >= 0; i--) {
				if (sceneAge >= pending.get(i)[0]) {
					for (Villager v : pendingTargets.get(i)) {
						if (!v.isRemoved()) {
							level.broadcastEntityEvent(v, pendingEvents.get(i));
						}
					}
					pending.remove(i);
					pendingTargets.remove(i);
					pendingEvents.remove(i);
				}
			}
		});
	}

	private static void clear() {
		actors.forEach(Entity::discard);
		actors.clear();
		placed.forEach((pos, state) -> level.setBlock(pos, state, 3));
		placed.clear();
		pending.clear();
		pendingTargets.clear();
		pendingEvents.clear();
		sceneAge = 0;
	}

	// ------------------------------------------------------------------ camera

	/** Put the camera `distance` in front of the stage at `height` above it, looking at the actors. */
	private static Vec3 camera(double distance, double height) {
		Vec3 target = Vec3.atBottomCenterOf(stage).add(0, 1.4, 0);
		Vec3 cam = Vec3.atBottomCenterOf(stage).subtract(look.scale(distance));
		double floor = ground(BlockPos.containing(cam)).getY();
		cam = new Vec3(cam.x, Math.max(stage.getY(), floor) + height, cam.z);
		aim(cam, target);
		shooAway(cam);
		return cam;
	}

	/** Village residents wandering between the camera and the actors get moved out of the shot. */
	private static void shooAway(Vec3 cam) {
		Vec3 stageCenter = Vec3.atBottomCenterOf(stage);
		for (Entity e : level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(stage).inflate(16),
			e -> e instanceof Mob && !actors.contains(e))) {
			Vec3 rel = e.position().subtract(stageCenter);
			double along = rel.dot(look);
			double across = Math.abs(rel.dot(new Vec3(-look.z, 0, look.x)));
			// In front of the actors (camera side) or crowding the stage.
			if (along < 2.5 && across < 7 && e.position().distanceTo(cam) > 0.5) {
				e.discard();
			}
		}
	}

	static void aim(Vec3 cam, Vec3 target) {
		Vec3 d = target.subtract(cam);
		float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		player.teleportTo(level, cam.x, cam.y - player.getEyeHeight(), cam.z, yaw, pitch);
	}

	private static void faceAll(Vec3 cam, Mob... mobs) {
		for (Mob m : mobs) {
			face(m, cam);
		}
	}

	private static void face(Mob m, Vec3 target) {
		Vec3 d = target.subtract(m.position());
		float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
		m.setYRot(yaw);
		m.setYHeadRot(yaw);
		m.setYBodyRot(yaw);
		m.yRotO = yaw;
		m.yHeadRotO = yaw;
		m.yBodyRotO = yaw;
	}

	private Showcase() {
	}
}
