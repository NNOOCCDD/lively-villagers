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
	/** Game time before which this villager ignores gifts (one gift at a time, never a whole stack). */
	public long nextGiftAt;
	/** When the current panic started, and how many loop shouts it has had (iron farms panic forever). */
	public long panicStartedAt;
	public int panicShouts;
	/** The raid this villager is reacting to (-1 = none), and what it has said during it. */
	public int raidId = -1;
	public int raidShouts;
	public boolean raidHeroSaid;
	public boolean wasResting;
	/** Game time to mumble a bedtime line, or -1. */
	public long bedtimeLineAt = -1;
	public long nextChatter;
	/** A gift the villager shows off in its hand until clearHandAt (game time). */
	public Item heldGift;
	public long clearHandAt = -1;
}
