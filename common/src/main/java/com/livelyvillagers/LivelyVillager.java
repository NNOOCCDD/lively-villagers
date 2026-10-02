package com.livelyvillagers;

/**
 * Added to every Villager by {@code VillagerMixin}: the mod's per-villager data, without any
 * loader-specific data-attachment API, so the same save format works on Fabric and NeoForge.
 */
public interface LivelyVillager {
	VillagerMind livelyvillagers$mind();

	void livelyvillagers$setMind(VillagerMind mind);

	/** Whether a mind has been loaded or created yet (used to migrate old Fabric data). */
	boolean livelyvillagers$hasMind();

	VillagerState livelyvillagers$state();
}
