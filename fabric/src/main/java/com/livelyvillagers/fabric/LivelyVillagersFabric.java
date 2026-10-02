package com.livelyvillagers.fabric;

import com.livelyvillagers.LivelyVillager;
import com.livelyvillagers.LivelyVillagers;
import com.livelyvillagers.VillagerMind;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/** Fabric entry point: wires Fabric API events to the loader-neutral core. */
public class LivelyVillagersFabric implements ModInitializer {
	/**
	 * Where 1.0.x stored villager names and personalities (a Fabric data attachment). Still registered
	 * so old saves can be read; each villager's data moves to the loader-neutral format on load.
	 */
	@SuppressWarnings("UnstableApiUsage")
	private static final AttachmentType<VillagerMind> LEGACY_MIND = AttachmentRegistry.<VillagerMind>builder()
		.persistent(VillagerMind.CODEC)
		.buildAndRegister(ResourceLocation.fromNamespaceAndPath(LivelyVillagers.MOD_ID, "mind"));

	@Override
	public void onInitialize() {
		LivelyVillagers.init(FabricLoader.getInstance().getConfigDir());

		ServerTickEvents.END_SERVER_TICK.register(LivelyVillagers::onServerTick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			migrateLegacyMind(entity);
			LivelyVillagers.onEntityLoad(entity);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LivelyVillagers.onServerStopping());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> LivelyVillagers.onPlayerJoin(handler.getPlayer()));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Villager v) {
				LivelyVillagers.onVillagerDeath(v, source);
			}
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) ->
			LivelyVillagers.onBlockBroken(level, player, pos, state));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
			LivelyVillagers.onUseEntity(player, level, hand, entity));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			LivelyVillagers.registerCommands(dispatcher));
	}

	@SuppressWarnings("UnstableApiUsage")
	private static void migrateLegacyMind(Entity entity) {
		if (entity instanceof Villager v && v.hasAttached(LEGACY_MIND)) {
			VillagerMind old = v.removeAttached(LEGACY_MIND);
			if (old != null && !((LivelyVillager) v).livelyvillagers$hasMind()) {
				LivelyVillagers.setMind(v, old);
			}
		}
	}
}
