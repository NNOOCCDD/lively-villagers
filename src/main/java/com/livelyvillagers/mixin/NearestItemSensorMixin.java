package com.livelyvillagers.mixin;

import com.livelyvillagers.LivelyConfig;
import com.livelyvillagers.Gifts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.NearestItemSensor;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/** Villagers don't walk over to gift items nobody threw (flower farms, item collectors). */
@Mixin(NearestItemSensor.class)
public abstract class NearestItemSensorMixin {
	@Inject(method = "doTick", at = @At("TAIL"))
	private void livelyvillagers$ignoreUnthrownGifts(ServerLevel level, Mob mob, CallbackInfo ci) {
		if (!(mob instanceof Villager) || !LivelyConfig.get().gifts) {
			return;
		}
		Optional<ItemEntity> wanted = mob.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
		if (wanted.isPresent() && Gifts.isGift(wanted.get().getItem()) && !Gifts.isPlayerGift(wanted.get())) {
			mob.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
		}
	}
}
