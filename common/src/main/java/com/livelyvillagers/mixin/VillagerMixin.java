package com.livelyvillagers.mixin;

import com.livelyvillagers.Gifts;
import com.livelyvillagers.LivelyConfig;
import com.livelyvillagers.LivelyVillager;
import com.livelyvillagers.LivelyVillagers;
import com.livelyvillagers.VillagerBrain;
import com.livelyvillagers.VillagerMind;
import com.livelyvillagers.VillagerState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerMixin implements LivelyVillager {
	@Unique
	private static final String livelyvillagers$TAG = "LivelyVillagers";

	@Unique
	private VillagerMind livelyvillagers$mind;
	@Unique
	private final VillagerState livelyvillagers$state = new VillagerState();

	@Override
	public VillagerMind livelyvillagers$mind() {
		if (livelyvillagers$mind == null) {
			livelyvillagers$mind = VillagerMind.random();
		}
		return livelyvillagers$mind;
	}

	@Override
	public void livelyvillagers$setMind(VillagerMind mind) {
		livelyvillagers$mind = mind;
	}

	@Override
	public boolean livelyvillagers$hasMind() {
		return livelyvillagers$mind != null;
	}

	@Override
	public VillagerState livelyvillagers$state() {
		return livelyvillagers$state;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void livelyvillagers$save(CompoundTag tag, CallbackInfo ci) {
		if (livelyvillagers$mind != null) {
			VillagerMind.CODEC.encodeStart(NbtOps.INSTANCE, livelyvillagers$mind).result()
				.ifPresent(t -> tag.put(livelyvillagers$TAG, t));
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void livelyvillagers$load(CompoundTag tag, CallbackInfo ci) {
		if (tag.contains(livelyvillagers$TAG)) {
			VillagerMind.CODEC.parse(NbtOps.INSTANCE, tag.get(livelyvillagers$TAG))
				.resultOrPartial(e -> LivelyVillagers.LOGGER.warn("Unreadable villager data: {}", e))
				.ifPresent(m -> livelyvillagers$mind = m);
		}
	}

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
