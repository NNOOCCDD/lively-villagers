package com.livelyvillagers;

import com.mojang.math.Transformation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Speech bubbles are vanilla text_display entities that follow the villager, so players see them
 * without installing anything.
 */
public final class SpeechBubbles {
	public static final String TAG = "livelyvillagers_bubble";
	private static final int TEXT_COLOR = 0x3B2A1A;
	private static final int BACKGROUND = 0xE8F4EBD9;

	private record Bubble(Display.TextDisplay display, Villager villager, long expiresAt) {
	}

	private static final Map<UUID, Bubble> ACTIVE = new HashMap<>();

	public static void show(Villager villager, String text) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		remove(villager.getUUID());

		Component component = Component.literal(text).withStyle(s -> s.withColor(TextColor.fromRgb(TEXT_COLOR)));
		float scale = villager.isBaby() ? 0.45F : 0.6F;
		CompoundTag tag = new CompoundTag();
		tag.putString("id", "minecraft:text_display");
		tag.putString("text", Component.Serializer.toJson(component, level.registryAccess()));
		tag.putString("billboard", "center");
		// Vanilla decodes "alignment" even when absent and logs an error, so always set it.
		tag.putString("alignment", "center");
		tag.putInt("background", BACKGROUND);
		tag.putInt("line_width", 150);
		tag.putInt("teleport_duration", 2);
		tag.putFloat("view_range", 0.5F);
		tag.put("transformation", Transformation.EXTENDED_CODEC
			.encodeStart(NbtOps.INSTANCE, new Transformation(new Vector3f(), null, new Vector3f(scale), null))
			.getOrThrow());

		Entity entity = EntityType.loadEntityRecursive(tag, level, e -> {
			e.moveTo(villager.getX(), bubbleY(villager), villager.getZ());
			return e;
		});
		if (!(entity instanceof Display.TextDisplay display)) {
			return;
		}
		display.addTag(TAG);
		int ticks = Math.min(140, 50 + text.length() * 2);
		// Register before adding: adding fires ENTITY_LOAD, which discards untracked bubbles.
		ACTIVE.put(villager.getUUID(), new Bubble(display, villager, level.getGameTime() + ticks));
		level.addFreshEntity(display);
	}

	private static double bubbleY(Villager villager) {
		return villager.getY() + villager.getBbHeight() + (villager.isBaby() ? 0.25 : 0.4);
	}

	public static void tick(MinecraftServer server) {
		Iterator<Bubble> it = ACTIVE.values().iterator();
		while (it.hasNext()) {
			Bubble b = it.next();
			if (b.villager.isRemoved() || b.display.isRemoved() || b.villager.level() != b.display.level()
				|| b.display.level().getGameTime() >= b.expiresAt) {
				b.display.discard();
				it.remove();
				continue;
			}
			b.display.setPos(b.villager.getX(), bubbleY(b.villager), b.villager.getZ());
		}
	}

	private static void remove(UUID villager) {
		Bubble old = ACTIVE.remove(villager);
		if (old != null) {
			old.display.discard();
		}
	}

	/** A bubble that was saved to disk mid-sentence has nobody following it any more. */
	public static void onEntityLoad(Entity entity) {
		if (entity instanceof Display.TextDisplay && entity.getTags().contains(TAG)
			&& ACTIVE.values().stream().noneMatch(b -> b.display == entity)) {
			entity.discard();
		}
	}

	public static void clearAll() {
		ACTIVE.values().forEach(b -> b.display.discard());
		ACTIVE.clear();
	}

	private SpeechBubbles() {
	}
}
