package com.livelyvillagers;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/**
 * The loader-neutral core. The Fabric and NeoForge entry points call {@link #init} once and forward
 * their loader's events to the {@code on...} methods here; everything else is plain Minecraft code.
 */
public final class LivelyVillagers {
	public static final String MOD_ID = "livelyvillagers";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final boolean DEBUG = Boolean.getBoolean("livelyvillagers.debug");

	public static void init(Path configDir) {
		LivelyConfig.init(configDir);
		// Dev-only tools (self-test, raid test, screenshot and film directors) are left out of release
		// jars, so they are only ever reached by name, when their flag is set.
		for (String tool : new String[] {"selftest:SelfTest", "raidtest:RaidTest", "showcase:Showcase", "film:Film"}) {
			String[] parts = tool.split(":");
			if (Boolean.getBoolean("livelyvillagers." + parts[0])) {
				try {
					Class.forName("com.livelyvillagers.dev." + parts[1]).getDeclaredMethod("install").invoke(null);
				} catch (ReflectiveOperationException e) {
					LOGGER.error("Dev tool {} is not available in this build", parts[1], e);
				}
			}
		}
		LOGGER.info("Lively Villagers loaded");
	}

	// ------------------------------------------------------------------ events, forwarded by each loader

	public static void onServerTick(MinecraftServer server) {
		SpeechBubbles.tick(server);
		TestHooks.serverTick(server);
	}

	public static void onEntityLoad(Entity entity) {
		SpeechBubbles.onEntityLoad(entity);
	}

	public static void onServerStopping() {
		SpeechBubbles.clearAll();
	}

	public static void onPlayerJoin(ServerPlayer player) {
		TestHooks.playerJoin(player);
	}

	public static void onVillagerDeath(Villager v, DamageSource source) {
		if (LivelyConfig.get().deathLineChance > 0) {
			Danger.onDeath(v, source);
		}
	}

	/** After a player broke a block. */
	public static void onBlockBroken(Level level, Player player, BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel serverLevel) {
			BlockReactions.onBlockBroken(serverLevel, player, pos, state);
		}
	}

	/**
	 * A player right-clicked an entity. PASS lets vanilla carry on (trading, head shakes); anything else
	 * means the mod handled it.
	 */
	public static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity) {
		if (level.isClientSide || hand != InteractionHand.MAIN_HAND || player.isSpectator()
			|| !(entity instanceof Villager v) || !v.isAlive() || v.isNoAi() || v.isTrading()) {
			return InteractionResult.PASS;
		}
		if (v.isSleeping()) {
			Greetings.onClickedSleeping(v);
			return InteractionResult.PASS;
		}
		// Sneak + empty hand: they introduce themselves instead of opening trades.
		if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
			Greetings.introduce(v, player);
			return InteractionResult.SUCCESS;
		}
		// Name tags, leads and spawn eggs keep their vanilla use without chatter.
		ItemStack held = player.getMainHandItem();
		if (!held.is(Items.NAME_TAG) && !held.is(Items.VILLAGER_SPAWN_EGG) && !held.is(Items.LEAD)) {
			Greetings.onClicked(v, player);
		}
		return InteractionResult.PASS;
	}

	public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("lively")
			.then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
			.then(Commands.literal("reload")
				.requires(src -> src.hasPermission(2))
				.executes(ctx -> {
					LivelyConfig.load();
					ctx.getSource().sendSuccess(() -> Component.literal("Lively Villagers config reloaded."), true);
					return 1;
				}))
			.then(Commands.literal("personality")
				.requires(src -> src.hasPermission(2))
				.then(Commands.argument("personality", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
						Arrays.stream(Personality.values()).map(Personality::getSerializedName), builder))
					.executes(ctx -> setPersonality(ctx.getSource(), StringArgumentType.getString(ctx, "personality"))))));
	}

	private static int info(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = src.getPlayerOrException();
		Villager v = lookedAtVillager(player);
		if (v == null) {
			src.sendFailure(Component.literal("Look at a villager first."));
			return 0;
		}
		VillagerMind mind = mind(v);
		src.sendSuccess(() -> Component.literal(Speech.displayName(v) + " the " + Speech.jobName(v)
			+ " (" + mind.personality().displayName() + ") - your reputation: " + v.getPlayerReputation(player)), false);
		return 1;
	}

	private static int setPersonality(CommandSourceStack src, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Personality p;
		try {
			p = Personality.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			src.sendFailure(Component.literal("Unknown personality: " + name));
			return 0;
		}
		Villager v = lookedAtVillager(src.getPlayerOrException());
		if (v == null) {
			src.sendFailure(Component.literal("Look at a villager first."));
			return 0;
		}
		setMind(v, mind(v).withPersonality(p));
		src.sendSuccess(() -> Component.literal(Speech.displayName(v) + " is now " + p.displayName().toLowerCase(Locale.ROOT) + "."), true);
		return 1;
	}

	private static Villager lookedAtVillager(ServerPlayer player) {
		double reach = 8.0;
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F).scale(reach);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(look),
			player.getBoundingBox().expandTowards(look).inflate(1.0), e -> e instanceof Villager, reach * reach);
		return hit != null ? (Villager) hit.getEntity() : null;
	}

	// ------------------------------------------------------------------ per-villager data

	/** The villager's name, personality and gift record (saved with the villager). */
	public static VillagerMind mind(Villager v) {
		return ((LivelyVillager) v).livelyvillagers$mind();
	}

	public static void setMind(Villager v, VillagerMind mind) {
		((LivelyVillager) v).livelyvillagers$setMind(mind);
	}

	/** Cooldowns and flags (not saved). */
	public static VillagerState state(Villager v) {
		return ((LivelyVillager) v).livelyvillagers$state();
	}

	public static void trace(String event, Villager v, String detail) {
		if (DEBUG) {
			LOGGER.info("[react] {} {} ({} {}): {}", event, v.getUUID().toString().substring(0, 8), mind(v).name(), mind(v).personality(), detail);
		}
		TestHooks.traceListener.accept(event, v);
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
