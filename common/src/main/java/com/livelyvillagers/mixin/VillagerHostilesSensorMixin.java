package com.livelyvillagers.mixin;

import com.google.common.collect.ImmutableMap;
import com.livelyvillagers.LivelyConfig;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla villagers only fear zombies and illagers. Teach them a few more monsters. */
@Mixin(VillagerHostilesSensor.class)
public abstract class VillagerHostilesSensorMixin {
	@Unique
	private static final ImmutableMap<EntityType<?>, Float> LIVELY_EXTRA_DANGERS = ImmutableMap.<EntityType<?>, Float>builder()
		.put(EntityType.CREEPER, 8.0F)
		.put(EntityType.SKELETON, 8.0F)
		.put(EntityType.STRAY, 8.0F)
		.put(EntityType.BOGGED, 8.0F)
		.put(EntityType.WITHER_SKELETON, 8.0F)
		.put(EntityType.SPIDER, 6.0F)
		.put(EntityType.CAVE_SPIDER, 6.0F)
		.put(EntityType.WITCH, 10.0F)
		.put(EntityType.BLAZE, 10.0F)
		.put(EntityType.WARDEN, 16.0F)
		.put(EntityType.WITHER, 20.0F)
		.build();

	@Inject(method = "isMatchingEntity", at = @At("HEAD"), cancellable = true)
	private void livelyvillagers$extraDangers(LivingEntity villager, LivingEntity other, CallbackInfoReturnable<Boolean> cir) {
		if (!LivelyConfig.get().extraDangers) {
			return;
		}
		Float distance = LIVELY_EXTRA_DANGERS.get(other.getType());
		if (distance != null && other.distanceToSqr(villager) <= distance * distance) {
			cir.setReturnValue(true);
		}
	}
}
