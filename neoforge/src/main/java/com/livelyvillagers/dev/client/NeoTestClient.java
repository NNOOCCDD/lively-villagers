package com.livelyvillagers.dev.client;

import com.livelyvillagers.LivelyVillagers;
import com.livelyvillagers.dev.SelfTest;
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
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Dev-only NeoForge client harness for the shared self-test (flat world) and raid test (the fixed
 * showcase seed): creates the world, hides the HUD, saves screenshots and quits when the test ends.
 */
public final class NeoTestClient {
	private static boolean started;

	public static void install() {
		boolean village = Boolean.getBoolean("livelyvillagers.raidtest");
		NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post e) -> {
			Screen screen = e.getScreen();
			if (!started && (screen instanceof TitleScreen || screen instanceof AccessibilityOnboardingScreen)) {
				started = true;
				Minecraft client = Minecraft.getInstance();
				client.options.onboardAccessibility = false;
				client.options.save();
				client.execute(() -> createWorld(client, screen, village));
			}
		});
		NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> Minecraft.getInstance().options.hideGui = true);
		SelfTest.screenshot = name -> {
			Minecraft client = Minecraft.getInstance();
			client.execute(() -> Screenshot.grab(client.gameDirectory, "lively-neoforge-" + name + ".png", client.getMainRenderTarget(),
				message -> LivelyVillagers.LOGGER.info("SELFTEST screenshot {}: {}", name, message.getString())));
		};
		SelfTest.finished = () -> Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
	}

	private static void createWorld(Minecraft client, Screen parent, boolean village) {
		String name = (village ? "lively-raidtest-" : "lively-test-") + System.currentTimeMillis() / 1000;
		LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.EASY, true, new GameRules(), WorldDataConfiguration.DEFAULT);
		var preset = village ? WorldPresets.NORMAL : WorldPresets.FLAT;
		client.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20261002L, village, false),
			registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(preset).value().createWorldDimensions(),
			parent);
	}

	private NeoTestClient() {
	}
}
