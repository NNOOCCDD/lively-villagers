package com.livelyvillagers.client;

import com.livelyvillagers.LivelyVillagers;
import com.livelyvillagers.SelfTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Dev-only: with -Dlivelyvillagers.selftest=true, makes a fresh flat world, runs SelfTest and screenshots it. */
public class SelfTestClient implements ClientModInitializer {
	private static boolean started;
	private static boolean showcase;

	@Override
	public void onInitializeClient() {
		showcase = Boolean.getBoolean("livelyvillagers.showcase");
		if (!Boolean.getBoolean("livelyvillagers.selftest") && !showcase) {
			return;
		}
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!started && (screen instanceof TitleScreen || screen instanceof AccessibilityOnboardingScreen)) {
				started = true;
				client.options.onboardAccessibility = false;
				client.options.save();
				client.execute(() -> createWorld(client, screen));
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			client.options.hideGui = true;
			if (showcase) {
				client.options.renderDistance().set(16);
				client.options.particles().set(net.minecraft.client.ParticleStatus.ALL);
				client.options.fov().set(60);
			}
		});
		SelfTest.screenshot = name -> {
			Minecraft client = Minecraft.getInstance();
			client.execute(() -> Screenshot.grab(client.gameDirectory, "lively-" + name + ".png", client.getMainRenderTarget(),
				message -> LivelyVillagers.LOGGER.info("SELFTEST screenshot {}: {}", name, message.getString())));
		};
		SelfTest.finished = () -> Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
	}

	private static void createWorld(Minecraft client, Screen parent) {
		String name = (showcase ? "lively-showcase-" : "lively-test-") + System.currentTimeMillis() / 1000;
		LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.EASY, true, new GameRules(), WorldDataConfiguration.DEFAULT);
		var preset = showcase ? WorldPresets.NORMAL : WorldPresets.FLAT;
		client.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20261002L, showcase, false),
			registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(preset).value().createWorldDimensions(),
			parent);
	}
}
