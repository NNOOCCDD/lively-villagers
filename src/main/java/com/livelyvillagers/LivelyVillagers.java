package com.livelyvillagers;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.BiConsumer;

public class LivelyVillagers implements ModInitializer {
	public static final String MOD_ID = "livelyvillagers";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final boolean DEBUG = Boolean.getBoolean("livelyvillagers.debug");

	public static final AttachmentType<VillagerMind> MIND = AttachmentRegistry.<VillagerMind>builder()
		.persistent(VillagerMind.CODEC)
		.initializer(VillagerMind::random)
		.buildAndRegister(id("mind"));
	public static final AttachmentType<VillagerState> STATE = AttachmentRegistry.createDefaulted(id("state"), VillagerState::new);

	/** Self-test only: overrides the random chance of greetings and bedtime lines when >= 0. */
	public static float forcedChance = -1;
	/** Self-test only: sees every reaction. */
	public static BiConsumer<String, Villager> traceListener = (event, v) -> {
	};

	@Override
	public void onInitialize() {
		LivelyConfig.load();

		ServerTickEvents.END_SERVER_TICK.register(SpeechBubbles::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> SpeechBubbles.onEntityLoad(entity));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SpeechBubbles.clearAll());

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel) {
				Reactions.onBlockBroken(serverLevel, player, pos, state);
			}
		});

		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide || hand != InteractionHand.MAIN_HAND || player.isSpectator()
				|| !(entity instanceof Villager v) || !v.isAlive() || v.isNoAi() || v.isTrading()) {
				return InteractionResult.PASS;
			}
			if (v.isSleeping()) {
				Reactions.onClickedSleeping(v);
				return InteractionResult.PASS;
			}
			// Sneak + empty hand: they introduce themselves instead of opening trades.
			if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
				Reactions.introduce(v, player);
				return InteractionResult.SUCCESS;
			}
			// Name tags and spawn eggs keep their vanilla use without chatter.
			ItemStack held = player.getMainHandItem();
			if (!held.is(Items.NAME_TAG) && !held.is(Items.VILLAGER_SPAWN_EGG) && !held.is(Items.LEAD)) {
				Reactions.onClicked(v, player);
			}
			return InteractionResult.PASS;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("lively")
				.then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
				.then(Commands.literal("personality")
					.requires(src -> src.hasPermission(2))
					.then(Commands.argument("personality", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
							Arrays.stream(Personality.values()).map(Personality::getSerializedName), builder))
						.executes(ctx -> setPersonality(ctx.getSource(), StringArgumentType.getString(ctx, "personality")))))));

		if (Boolean.getBoolean("livelyvillagers.selftest")) {
			SelfTest.install();
		} else if (Boolean.getBoolean("livelyvillagers.showcase")) {
			Showcase.install();
		} else if (Boolean.getBoolean("livelyvillagers.film")) {
			Film.install();
		}
		LOGGER.info("Lively Villagers loaded");
	}

	private static int info(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = src.getPlayerOrException();
		Villager v = lookedAtVillager(player);
		if (v == null) {
			src.sendFailure(Component.literal("Look at a villager first."));
			return 0;
		}
		VillagerMind mind = mind(v);
		src.sendSuccess(() -> Component.literal(Reactions.displayName(v) + " the " + Reactions.jobName(v)
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
		src.sendSuccess(() -> Component.literal(Reactions.displayName(v) + " is now " + p.displayName().toLowerCase(Locale.ROOT) + "."), true);
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

	public static VillagerMind mind(Villager v) {
		return v.getAttachedOrCreate(MIND);
	}

	public static void setMind(Villager v, VillagerMind mind) {
		v.setAttached(MIND, mind);
	}

	public static VillagerState state(Villager v) {
		return v.getAttachedOrCreate(STATE);
	}

	public static void trace(String event, Villager v, String detail) {
		if (DEBUG) {
			LOGGER.info("[react] {} {} ({} {}): {}", event, v.getUUID().toString().substring(0, 8), mind(v).name(), mind(v).personality(), detail);
		}
		traceListener.accept(event, v);
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
