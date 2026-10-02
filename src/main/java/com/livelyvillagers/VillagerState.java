package com.livelyvillagers;

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
	public long nextChatter;
	/** Game time at which to take a gifted flower back out of the villager's hand. */
	public long clearHandAt = -1;
}
