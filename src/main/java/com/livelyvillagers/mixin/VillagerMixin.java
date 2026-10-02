package com.livelyvillagers.mixin;

import com.livelyvillagers.LivelyConfig;
import com.livelyvillagers.Gifts;
import com.livelyvillagers.VillagerBrain;
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
		VillagerBrain.tick((Villager) (Object) this);
	}

	/**
	 * Gift items are "wanted" so the brain walks over to them (the item sensor then drops any that no
	 * player threw). They never go into the inventory.
	 */
	@Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
	private void livelyvillagers$wantGifts(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (LivelyConfig.get().gifts && Gifts.isGift(stack)) {
			cir.setReturnValue(Gifts.acceptsGiftNow((Villager) (Object) this));
		}
	}

	@Inject(method = "pickUpItem", at = @At("HEAD"), cancellable = true)
	private void livelyvillagers$receiveGift(ItemEntity itemEntity, CallbackInfo ci) {
		if (!LivelyConfig.get().gifts || !Gifts.isGift(itemEntity.getItem())) {
			return;
		}
		// Only player-thrown gifts, one at a time; anything else stays on the ground.
		if (Gifts.isPlayerGift(itemEntity) && Gifts.acceptsGiftNow((Villager) (Object) this)) {
			Gifts.receive((Villager) (Object) this, itemEntity);
		}
		ci.cancel();
	}
}
