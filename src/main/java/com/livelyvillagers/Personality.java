package com.livelyvillagers;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public enum Personality implements StringRepresentable {
	CHEERFUL(0.9F, 1.15F),
	CURIOUS(0.6F, 1.05F),
	SHY(0.25F, 1.2F),
	GRUMPY(0.35F, 0.85F);

	public static final Codec<Personality> CODEC = StringRepresentable.fromEnum(Personality::values);

	/** Chance to say hi when a player walks into range. */
	public final float greetChance;
	/** Voice pitch multiplier. */
	public final float pitch;

	Personality(float greetChance, float pitch) {
		this.greetChance = greetChance;
		this.pitch = pitch;
	}

	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String displayName() {
		return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
	}
}
