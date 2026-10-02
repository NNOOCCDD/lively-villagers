package com.livelyvillagers.dev;

import com.livelyvillagers.Greetings;
import com.livelyvillagers.LivelyVillagers;
import com.livelyvillagers.Personality;
import com.livelyvillagers.SpeechBubbles;
import com.livelyvillagers.TestHooks;
import com.livelyvillagers.VillagerMind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Dev-only scripted scene (-Dlivelyvillagers.selftest=true): one villager in a fence pen, the player
 * walks up, places blocks, throws gifts and spawns a creeper. Every step checks the reaction trace and
 * asks the client for a screenshot.
 */
public final class SelfTest {
	/** Set by the client entrypoint: takes a screenshot with the given name. */
	public static Consumer<String> screenshot = name -> {
	};
	/** Set by the client entrypoint: called when the run is over. */
	public static Runnable finished = () -> {
	};

	private static final List<String> events = new ArrayList<>();
	private static final List<String> results = new ArrayList<>();
	private static ServerPlayer player;
	private static Villager villager;
	private static Creeper creeper;
	private static BlockPos base;
	private static long startTick = -1;
	private static int reputationBefore;

	public static void install() {
		TestHooks.forcedChance = 1.0F;
		TestHooks.traceListener = (event, v) -> {
			if (v == villager) {
				events.add(event);
			}
		};
		TestHooks.onPlayerJoin(p -> {
			player = p;
			startTick = p.getServer().getTickCount() + 60;
		});
		TestHooks.onServerTick(SelfTest::tick);
	}

	private static void tick(MinecraftServer server) {
		if (startTick < 0 || player == null) {
			return;
		}
		long t = server.getTickCount() - startTick;
		if (t < 0) {
			return;
		}
		ServerLevel level = player.serverLevel();
		try {
			step((int) t, level, server);
		} catch (RuntimeException e) {
			LivelyVillagers.LOGGER.error("SELFTEST crashed at t={}", t, e);
			results.add("FAIL crashed at t=" + t + ": " + e);
			finish();
		}
	}

	private static void step(int t, ServerLevel level, MinecraftServer server) {
		switch (t) {
			case 0 -> setup(level, server);
			// Player walks up from out of range -> greeting.
			case 40 -> {
				events.clear();
				face(level, base.offset(0, 0, -3));
			}
			case 60 -> {
				expect("greeting", "GREET", "GREET_WARM", "GREET_NIGHT", "GREET_RAIN");
				screenshot.accept("1-greeting");
			}
			// Place a flower next to the pen -> liked block.
			case 140 -> {
				events.clear();
				place(level, Items.POPPY, base.offset(3, 0, -1));
			}
			case 160 -> {
				expect("flower placed", "LIKE_BLOCK", "CURIOUS_BLOCK");
				screenshot.accept("2-likes-flower");
			}
			// Place TNT -> scared.
			case 240 -> {
				events.clear();
				place(level, Items.TNT, base.offset(-3, 0, -1));
			}
			case 260 -> {
				expect("tnt placed", "SCARY_BLOCK");
				screenshot.accept("3-scared-of-tnt");
				level.setBlockAndUpdate(base.offset(-3, 0, -1), Blocks.AIR.defaultBlockState());
			}
			// Place the farmer's job site -> loves it.
			case 340 -> {
				events.clear();
				place(level, Items.COMPOSTER, base.offset(3, 0, 1));
			}
			case 360 -> {
				expect("composter placed", "LOVE_JOB_SITE");
				screenshot.accept("4-loves-job-site");
			}
			// Throw a flower into the pen -> gift, reputation goes up.
			case 440 -> {
				events.clear();
				reputationBefore = villager.getPlayerReputation(player);
				throwGift(level, Items.DANDELION);
			}
			case 480 -> {
				expect("first gift", "GIFT_LIKE");
				int after = villager.getPlayerReputation(player);
				check("gift raised reputation (" + reputationBefore + " -> " + after + ")", after > reputationBefore);
				check("villager holds the flower", villager.getMainHandItem().is(Items.DANDELION));
				screenshot.accept("5-gift");
			}
			// Same day again -> thanks, no extra reputation.
			case 600 -> {
				events.clear();
				reputationBefore = villager.getPlayerReputation(player);
				throwGift(level, Items.CAKE);
			}
			case 640 -> {
				expect("second gift same day", "GIFT_AGAIN");
				check("no extra reputation on second gift", villager.getPlayerReputation(player) == reputationBefore);
			}
			// A creeper shows up -> panic and shout.
			case 760 -> {
				events.clear();
				creeper = EntityType.CREEPER.spawn(level, base.offset(5, 0, 2), MobSpawnType.COMMAND);
				if (creeper != null) {
					creeper.setNoAi(true);
				}
			}
			case 830 -> {
				expect("creeper nearby", "PANIC_HOSTILE");
				check("villager is panicking", villager.getBrain().isActive(Activity.PANIC));
				screenshot.accept("6-panic");
				if (creeper != null) {
					creeper.discard();
				}
				events.clear();
			}
			case 1000 -> {
				expect("calms down", "CALM");
				check("panic over", !villager.getBrain().isActive(Activity.PANIC));
				screenshot.accept("7-calm");
			}
			// Break their job site -> upset (only if they claimed it).
			case 1060 -> {
				events.clear();
				boolean claimed = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent();
				LivelyVillagers.LOGGER.info("SELFTEST job site claimed: {}", claimed);
				player.gameMode.destroyBlock(base.offset(3, 0, 1));
				if (!claimed) {
					events.add("BROKE_JOB_SITE(skipped: not claimed)");
				}
			}
			case 1080 -> {
				if (events.stream().anyMatch(e -> e.startsWith("BROKE_JOB_SITE(skipped"))) {
					results.add("SKIP job site broken (villager had not claimed it yet)");
				} else {
					expect("job site broken", "BROKE_JOB_SITE");
				}
				screenshot.accept("8-broke-job-site");
			}
			// Right-click a working villager -> business line (fired twice, like the two interact packets).
			case 1120 -> {
				events.clear();
				face(level, base.offset(0, 0, -3));
				rightClick(level);
				rightClick(level);
			}
			case 1130 -> {
				expect("right-click worker", "CLICK_WORKER");
				check("one line per click (" + events.size() + ")", events.size() == 1);
				screenshot.accept("9-click-worker");
			}
			// Same villager without a job -> "Stop touching me!".
			case 1200 -> {
				events.clear();
				villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
				rightClick(level);
			}
			case 1210 -> {
				expect("right-click jobless", "CLICK_JOBLESS");
				screenshot.accept("10-click-jobless");
			}
			// Night falls: bedtime mumble, sleepy greeting, sleepy right-click.
			case 1260 -> {
				events.clear();
				face(level, base.offset(0, 0, -12));
				level.setDayTime(13000);
			}
			case 1480 -> {
				expect("bedtime line", "BEDTIME");
				LivelyVillagers.state(villager).lastGreeted.clear();
				events.clear();
				face(level, base.offset(0, 0, -3));
			}
			case 1500 -> {
				expect("night greeting", "GREET_NIGHT");
				screenshot.accept("11-night-greeting");
			}
			case 1560 -> {
				events.clear();
				rightClick(level);
			}
			case 1570 -> {
				expect("night right-click jobless", "CLICK_JOBLESS_NIGHT");
				screenshot.accept("12-night-click");
			}
			// Realistic walk-by: 8 penned villagers with random personalities, real odds, walking speed.
			// --- 1.0.1: a thrown stack is taken one at a time, not eaten in seconds.
			case 1600 -> {
				level.setDayTime(6000);
				events.clear();
				LivelyVillagers.state(villager).nextGiftAt = 0;
				throwGift(level, Items.POPPY, 5);
			}
			case 1660 -> {
				long gifts = events.stream().filter(e -> e.startsWith("GIFT")).count();
				int left = countItems(level, Items.POPPY);
				check("stack gift: one taken (" + gifts + " gift lines, " + left + "/5 left on the ground)", gifts == 1 && left == 4);
				clearItems(level);
				events.clear();
			}
			// --- 1.0.1: flowers nobody threw stay on the ground.
			case 1680 -> {
				LivelyVillagers.state(villager).nextGiftAt = 0;
				ItemEntity loose = new ItemEntity(level, villager.getX() + 0.3, villager.getY() + 0.5, villager.getZ() + 0.3, new ItemStack(Items.DANDELION));
				loose.setNoPickUpDelay();
				level.addFreshEntity(loose);
			}
			case 1760 -> {
				check("unthrown flower left alone", countItems(level, Items.DANDELION) == 1
					&& events.stream().noneMatch(e -> e.startsWith("GIFT") || e.startsWith("FOUND")));
				clearItems(level);
			}
			// --- 1.0.1: a daytime thunderstorm is not bedtime.
			case 1780 -> {
				level.setWeatherParameters(0, 6000, true, true);
			}
			case 1790 -> {
				check("thunderstorm at noon is not sleepy time", !Greetings.isSleepyTime(villager));
				level.setWeatherParameters(6000, 0, false, false);
				// --- 1.0.1: /lively reload
				int ok;
				try {
					ok = server.getCommands().getDispatcher().execute("lively reload", server.createCommandSourceStack());
				} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
					ok = -1;
				}
				check("/lively reload", ok == 1);
				// --- 1.0.1: trading-hall cells are recognised.
				Villager boxed = EntityType.VILLAGER.spawn(level, base.offset(9, 0, 0), MobSpawnType.COMMAND);
				ring(level, base.offset(9, 0, 0), 1);
				check("1x1 cell villager is boxed in, pen villager is not", Greetings.isBoxedIn(boxed, level) && !Greetings.isBoxedIn(villager, level));
				boxed.discard();
			}
			// --- 1.0.1: nobody around, nothing said (the villager still panics).
			case 1810 -> {
				face(level, base.offset(0, 0, -70));
				events.clear();
				creeper = EntityType.CREEPER.spawn(level, base.offset(4, 0, 2), MobSpawnType.COMMAND);
				if (creeper != null) {
					creeper.setNoAi(true);
				}
			}
			case 1900 -> {
				check("panics silently with no player within 32 blocks (" + events + ")",
					villager.getBrain().isActive(Activity.PANIC) && events.isEmpty());
				face(level, base.offset(0, 0, -3));
				events.clear();
			}
			// --- 1.0.1: a long panic (iron farm) cries out at most twice.
			case 2310 -> {
				long loops = events.stream().filter(e -> e.equals("PANIC_LOOP")).count();
				check("long panic capped (" + loops + " loop shouts in 20 s)", loops <= 2);
				if (creeper != null) {
					creeper.discard();
				}
				events.clear();
			}
			// --- 1.0.1: last words, left hanging where the villager fell.
			case 2330 -> {
				villager.hurt(level.damageSources().playerAttack(player), 1000.0F);
			}
			case 2370 -> {
				expect("last words when killed by a player", "DEATH_BY_PLAYER");
				long bubbles = level.getEntitiesOfClass(net.minecraft.world.entity.Display.TextDisplay.class,
					new AABB(base).inflate(4), d -> d.getTags().contains(SpeechBubbles.TAG)).size();
				check("last-words bubble outlives the villager (" + bubbles + ")", villager.isRemoved() && bubbles >= 1);
			}
			case 2400 -> setupWalk(level);
			default -> {
				if (t > 2420 && t <= 2420 + WALK_TICKS) {
					walkStep(level, t - 2420);
				} else if (t == 2420 + WALK_TICKS + 20) {
					long greeted = walkers.stream().filter(greetedWalkers::contains).count();
					results.add("INFO walk-by: " + greeted + "/" + walkers.size() + " villagers said hi " + walkLog);
					finish();
				}
			}
		}
	}

	private static final int WALK_TICKS = 220;
	private static final List<Villager> walkers = new ArrayList<>();
	private static final java.util.Set<Villager> greetedWalkers = new java.util.HashSet<>();
	private static final List<String> walkLog = new ArrayList<>();
	private static BlockPos walkStart;

	private static void setupWalk(ServerLevel level) {
		TestHooks.forcedChance = -1;
		level.setDayTime(6000);
		walkStart = base.offset(-8, 0, 20);
		for (int i = 0; i < 8; i++) {
			BlockPos pos = walkStart.offset(4 + i * 5, 0, (i % 2 == 0) ? 5 : -5);
			ring(level, pos, 2);
			Villager w = EntityType.VILLAGER.spawn(level, pos, MobSpawnType.COMMAND);
			walkers.add(w);
			walkLog.add(LivelyVillagers.mind(w).personality().getSerializedName());
		}
		TestHooks.traceListener = TestHooks.traceListener.andThen((event, v) -> {
			if (walkers.contains(v) && event.startsWith("GREET")) {
				greetedWalkers.add(v);
			}
		});
		player.teleportTo(level, walkStart.getX() + 0.5, walkStart.getY(), walkStart.getZ() + 0.5, -90.0F, 10.0F);
	}

	/** Walking speed is about 4.3 blocks per second. */
	private static void walkStep(ServerLevel level, int step) {
		double x = walkStart.getX() + 0.5 + step * 0.2158;
		player.teleportTo(level, x, walkStart.getY(), walkStart.getZ() + 0.5, -90.0F, 10.0F);
	}

	private static void ring(ServerLevel level, BlockPos center, int r) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
					level.setBlockAndUpdate(center.offset(dx, 0, dz), Blocks.OAK_FENCE.defaultBlockState());
				}
			}
		}
	}

	private static void setup(ServerLevel level, MinecraftServer server) {
		GameRules rules = level.getGameRules();
		rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
		rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		level.setDayTime(6000);
		level.setWeatherParameters(6000, 0, false, false);
		player.setGameMode(GameType.CREATIVE);

		base = player.blockPosition().offset(0, 0, 12);
		for (Entity e : level.getEntities((Entity) null, new AABB(base).inflate(40), e -> !(e instanceof Player))) {
			e.discard();
		}
		// A 1x1 fence pen so the villager stays put; its eyes are above the fence, so it still sees the player.
		// A 3x3 fence pen: the villager stays close but isn't boxed in like a trading-hall cell.
		ring(level, base, 2);
		villager = EntityType.VILLAGER.spawn(level, base, MobSpawnType.COMMAND);
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
		// Vanilla drops the profession of a level-1, 0 XP villager with no job site; keep this one a farmer.
		villager.setVillagerXp(1);
		LivelyVillagers.setMind(villager, new VillagerMind("Barnaby", Personality.CHEERFUL, Map.of()));
		// Start far away, out of greeting range.
		face(level, base.offset(0, 0, -12));
		results.clear();
		LivelyVillagers.LOGGER.info("SELFTEST setup done at {}", base);
	}

	private static void face(ServerLevel level, BlockPos at) {
		Vec3 from = Vec3.atBottomCenterOf(at).add(0, player.getEyeHeight(), 0);
		Vec3 to = villager.getEyePosition().add(0, 0.3, 0);
		Vec3 d = to.subtract(from);
		float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, pitch);
	}

	private static void place(ServerLevel level, Item item, BlockPos pos) {
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
		BlockPos ground = pos.below();
		player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false));
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
	}

	private static void rightClick(ServerLevel level) {
		LivelyVillagers.onUseEntity(player, level, InteractionHand.MAIN_HAND, villager);
	}

	private static void throwGift(ServerLevel level, Item item) {
		throwGift(level, item, 1);
	}

	private static int countItems(ServerLevel level, Item item) {
		return level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(4), i -> i.getItem().is(item))
			.stream().mapToInt(i -> i.getItem().getCount()).sum();
	}

	private static void clearItems(ServerLevel level) {
		level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(6)).forEach(Entity::discard);
	}

	private static void throwGift(ServerLevel level, Item item, int count) {
		ItemEntity gift = new ItemEntity(level, villager.getX() + 0.3, villager.getY() + 0.5, villager.getZ() + 0.3, new ItemStack(item, count));
		gift.setDeltaMovement(Vec3.ZERO);
		gift.setThrower(player);
		gift.setNoPickUpDelay();
		level.addFreshEntity(gift);
	}

	private static void expect(String what, String... anyOf) {
		boolean ok = events.stream().anyMatch(e -> List.of(anyOf).contains(e));
		results.add((ok ? "PASS " : "FAIL ") + what + " -> " + events);
	}

	private static void check(String what, boolean ok) {
		results.add((ok ? "PASS " : "FAIL ") + what);
	}

	private static void finish() {
		startTick = -1;
		long fails = results.stream().filter(r -> r.startsWith("FAIL")).count();
		LivelyVillagers.LOGGER.info("SELFTEST RESULTS ({} fail):", fails);
		results.forEach(r -> LivelyVillagers.LOGGER.info("SELFTEST {}", r));
		LivelyVillagers.LOGGER.info("SELFTEST DONE");
		finished.run();
	}

	private SelfTest() {
	}
}
