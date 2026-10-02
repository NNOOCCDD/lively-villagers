package com.livelyvillagers;

import com.livelyvillagers.Lines.Topic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.Map;

/** How villagers talk: picking and filling a line, the sound, the bubble. Shared by every behaviour. */
public final class Speech {
	// Entity events the client already knows how to draw for villagers.
	public static final byte HEARTS = 12;
	public static final byte ANGRY = 13;
	public static final byte HAPPY = 14;
	public static final byte SWEAT = 42;

	/** How far away a player can be and still hear villagers talk. */
	private static final double HEARING_RANGE = 32.0;

	/**
	 * Say a line from `topic`, filling {name}, {job} and any `vars`. Nothing happens unless a player is
	 * within hearing range: farms in spawn chunks or chunk-loaded bases would otherwise churn bubbles
	 * and sounds all day. `sound` may be null when vanilla already plays one.
	 */
	public static void say(Villager v, Topic topic, SoundEvent sound, Map<String, String> vars) {
		if (!(v.level() instanceof ServerLevel level) || !anyoneListening(level, v)) {
			TestHooks.nextLineOverride = null;
			return;
		}
		VillagerMind mind = LivelyVillagers.mind(v);
		String picked = TestHooks.nextLineOverride != null ? TestHooks.nextLineOverride : Lines.pick(topic, mind.personality(), v.getRandom());
		TestHooks.nextLineOverride = null;
		String line = picked.replace("{name}", displayName(v)).replace("{job}", jobName(v));
		for (Map.Entry<String, String> e : vars.entrySet()) {
			line = line.replace("{" + e.getKey() + "}", e.getValue());
		}
		float pitch = mind.personality().pitch * (v.isBaby() ? 1.4F : 1.0F) + (v.getRandom().nextFloat() - 0.5F) * 0.1F;
		if (sound != null) {
			v.playSound(sound, 1.0F, pitch);
		}
		if (LivelyConfig.get().speechBubbles) {
			SpeechBubbles.show(v, line);
		}
		LivelyVillagers.trace(topic.name(), v, line);
	}

	private static boolean anyoneListening(ServerLevel level, Villager v) {
		return level.getNearestPlayer(v.getX(), v.getY(), v.getZ(), HEARING_RANGE, p -> !p.isSpectator()) != null;
	}

	/** The name tag if it has one, otherwise the name the mod gave it. */
	public static String displayName(Villager v) {
		return v.hasCustomName() ? v.getCustomName().getString() : LivelyVillagers.mind(v).name();
	}

	public static String jobName(Villager v) {
		VillagerProfession p = v.getVillagerData().getProfession();
		if (p == VillagerProfession.NONE) {
			return "Villager";
		}
		String n = p.name();
		return Character.toUpperCase(n.charAt(0)) + n.substring(1);
	}

	public static void lookAt(Villager v, LivingEntity target) {
		BehaviorUtils.lookAtEntity(v, target);
		v.getLookControl().setLookAt(target, 30.0F, 30.0F);
	}

	private Speech() {
	}
}
