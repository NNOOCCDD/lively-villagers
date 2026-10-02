package com.livelyvillagers;

import net.minecraft.world.entity.npc.Villager;

import java.util.function.BiConsumer;

/**
 * Seams for the dev-only tools (self-test, raid test, screenshot and film directors). Nothing in a
 * normal game touches these; they let the tools make random chances certain, watch every line, script
 * a specific line and scale bubbles for the camera.
 */
public final class TestHooks {
	/** When >= 0, replaces every random chance (1 = always). */
	public static float forcedChance = -1;
	/** Sees every line spoken (and a few other events) as (event, villager). */
	public static BiConsumer<String, Villager> traceListener = (event, v) -> {
	};
	/** The next line spoken, instead of a random pick. */
	public static String nextLineOverride;
	/** Speech-bubble size multiplier, for wide screenshots. */
	public static float bubbleScale = 1.0F;

	public static boolean forced() {
		return forcedChance >= 0;
	}

	/** The given chance, or the forced one while a test is running. */
	public static float chance(float normal) {
		return forced() ? forcedChance : normal;
	}

	private TestHooks() {
	}
}
