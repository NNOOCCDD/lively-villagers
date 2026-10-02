package com.livelyvillagers.mixin;

import com.livelyvillagers.BlockReactions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(method = "place", at = @At("RETURN"))
	private void livelyvillagers$afterPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.getReturnValue().consumesAction() && context.getPlayer() != null && context.getLevel() instanceof ServerLevel level) {
			BlockReactions.onBlockPlaced(level, context.getClickedPos(), level.getBlockState(context.getClickedPos()), context.getPlayer());
		}
	}
}
