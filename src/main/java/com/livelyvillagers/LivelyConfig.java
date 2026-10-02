package com.livelyvillagers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Plain JSON config at config/lively-villagers.json; missing keys fall back to these defaults. */
public class LivelyConfig {
	public boolean speechBubbles = true;
	public boolean greetings = true;
	public double greetRadius = 7.0;
	/** Seconds before the same villager may greet the same player again. */
	public int greetCooldownSeconds = 90;
	/** Seconds before a villager that chose not to say hi gets another chance. */
	public int greetRetrySeconds = 15;
	/** Scales every personality's chance to say hi (1.0 = cheerful always, shy half the time). */
	public double greetChanceMultiplier = 1.0;
	public boolean blockReactions = true;
	public double blockReactRadius = 7.0;
	public boolean gifts = true;
	/** Reputation (minor positive gossip) from the first gift each player gives a villager per Minecraft day. */
	public int giftReputation = 5;
	public int lovedGiftReputation = 15;
	/** Villagers also flee creepers, skeletons, spiders, witches and similar, and run from lit TNT. */
	public boolean extraDangers = true;
	public boolean dangerShouts = true;

	/** Bumped when a default changes, so untouched old values can be migrated. */
	public int configVersion = 2;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static LivelyConfig instance = new LivelyConfig();

	public static LivelyConfig get() {
		return instance;
	}

	private static void migrate(String json) {
		if (!json.contains("configVersion")) {
			// v1 shipped a 5-block greeting radius, which made walk-by greetings rare.
			if (instance.greetRadius == 5.0) {
				instance.greetRadius = 7.0;
			}
			instance.configVersion = 2;
		}
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("lively-villagers.json");
		try {
			if (Files.exists(path)) {
				LivelyConfig loaded = GSON.fromJson(Files.readString(path), LivelyConfig.class);
				if (loaded != null) {
					instance = loaded;
					migrate(Files.readString(path));
				}
			}
			// Rewrite so new options show up in older files.
			Files.writeString(path, GSON.toJson(instance));
		} catch (IOException | RuntimeException e) {
			LivelyVillagers.LOGGER.warn("Could not read {}, using defaults", path, e);
		}
	}
}
