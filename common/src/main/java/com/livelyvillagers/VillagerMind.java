package com.livelyvillagers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Saved with the villager: who they are and when each player last gave them a gift (Minecraft day number). */
public record VillagerMind(String name, Personality personality, Map<UUID, Long> lastGiftDay) {
	public static final Codec<VillagerMind> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("name").forGetter(VillagerMind::name),
		Personality.CODEC.fieldOf("personality").forGetter(VillagerMind::personality),
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("gift_days", Map.of()).forGetter(VillagerMind::lastGiftDay)
	).apply(i, VillagerMind::new));

	private static final String[] NAMES = {
		"Abe", "Agnes", "Barnaby", "Bea", "Cyril", "Dot", "Edmund", "Elsie", "Fern", "Gus", "Hattie", "Ivo",
		"Juniper", "Kit", "Lenny", "Mabel", "Ned", "Olive", "Percy", "Quill", "Rosa", "Silas", "Tilda", "Ugo",
		"Vera", "Wendell", "Wren", "Yara", "Zeb", "Moss", "Pip", "Clem", "Ottilie", "Bram", "Hazel", "Rufus"
	};

	public static VillagerMind random() {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		Personality[] all = Personality.values();
		return new VillagerMind(NAMES[r.nextInt(NAMES.length)], all[r.nextInt(all.length)], Map.of());
	}

	public VillagerMind withPersonality(Personality p) {
		return new VillagerMind(name, p, lastGiftDay);
	}

	public VillagerMind withGift(UUID player, long day) {
		Map<UUID, Long> days = new HashMap<>(lastGiftDay);
		days.put(player, day);
		return new VillagerMind(name, personality, Map.copyOf(days));
	}
}
