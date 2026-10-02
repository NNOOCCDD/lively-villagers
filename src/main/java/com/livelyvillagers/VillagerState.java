package com.livelyvillagers;

import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Short-lived per-villager state; not saved. */
public class VillagerState {
	public final Map<UUID, Long> lastGreeted = new HashMap<>();
	public Set<UUID> playersInRange = new HashSet<>();
	public boolean wasPanicking;
	public long panicEndedAt = -1;
	public long nextPanicShout;
	public long nextBlockReaction;
	public long nextClickLine;
	public long nextChatter;
	/** A gift the villager shows off in its hand until clearHandAt (game time). */
	public Item heldGift;
	public long clearHandAt = -1;
}
