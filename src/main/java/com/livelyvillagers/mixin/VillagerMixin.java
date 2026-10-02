package com.livelyvillagers.mixin;

import com.livelyvillagers.LivelyConfig;
import com.livelyvillagers.Reactions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerMixin {
	@Inject(method = "customServerAiStep", at = @At("TAIL"))
	private void livelyvillagers$think(CallbackInfo ci) {
		Reactions.tick((Villager) (Object) this);
	}

	/** Gifts are wanted, so the brain walks over to them. They never go into the inventory. */
	@Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
	private void livelyvillagers$wantGifts(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (LivelyConfig.get().gifts && Reactions.isGift(stack) && !((Villager) (Object) this).isSleeping()) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "pickUpItem", at = @At("HEAD"), cancellable = true)
	private void livelyvillagers$receiveGift(ItemEntity itemEntity, CallbackInfo ci) {
		if (LivelyConfig.get().gifts && Reactions.isGift(itemEntity.getItem())) {
			Reactions.onGift((Villager) (Object) this, itemEntity);
			ci.cancel();
		}
	}
}
