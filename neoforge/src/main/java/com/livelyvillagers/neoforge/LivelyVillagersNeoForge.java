package com.livelyvillagers.neoforge;

import com.livelyvillagers.LivelyVillagers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** NeoForge entry point: wires NeoForge events to the loader-neutral core. */
@Mod(LivelyVillagers.MOD_ID)
public class LivelyVillagersNeoForge {
	public LivelyVillagersNeoForge(IEventBus modBus) {
		LivelyVillagers.init(FMLPaths.CONFIGDIR.get());

		IEventBus bus = NeoForge.EVENT_BUS;
		bus.addListener((ServerTickEvent.Post e) -> LivelyVillagers.onServerTick(e.getServer()));
		bus.addListener((EntityJoinLevelEvent e) -> {
			if (!e.getLevel().isClientSide()) {
				LivelyVillagers.onEntityLoad(e.getEntity());
			}
		});
		bus.addListener((ServerStoppingEvent e) -> LivelyVillagers.onServerStopping());
		bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
			if (e.getEntity() instanceof ServerPlayer player) {
				LivelyVillagers.onPlayerJoin(player);
			}
		});
		// Lowest priority, skipping cancelled events: only villagers that really died get last words.
		bus.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, e -> {
			if (e.getEntity() instanceof Villager v) {
				LivelyVillagers.onVillagerDeath(v, e.getSource());
			}
		});
		bus.addListener(EventPriority.LOWEST, false, BlockEvent.BreakEvent.class, e -> {
			if (e.getLevel() instanceof Level level) {
				LivelyVillagers.onBlockBroken(level, e.getPlayer(), e.getPos(), e.getState());
			}
		});
		bus.addListener((PlayerInteractEvent.EntityInteract e) -> {
			InteractionResult result = LivelyVillagers.onUseEntity(e.getEntity(), e.getLevel(), e.getHand(), e.getTarget());
			if (result != InteractionResult.PASS) {
				e.setCancellationResult(result);
				e.setCanceled(true);
			}
		});
		bus.addListener((RegisterCommandsEvent e) -> LivelyVillagers.registerCommands(e.getDispatcher()));

		// Dev-only client harness for the self-test and raid test (left out of release jars).
		if (FMLEnvironment.dist.isClient() && (Boolean.getBoolean("livelyvillagers.selftest") || Boolean.getBoolean("livelyvillagers.raidtest"))) {
			try {
				Class.forName("com.livelyvillagers.dev.client.NeoTestClient").getDeclaredMethod("install").invoke(null);
			} catch (ReflectiveOperationException ex) {
				LivelyVillagers.LOGGER.error("Dev test client is not available in this build", ex);
			}
		}
	}
}
