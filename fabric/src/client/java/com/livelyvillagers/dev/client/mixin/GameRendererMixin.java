package com.livelyvillagers.dev.client.mixin;

import com.livelyvillagers.dev.client.FilmClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dev film recorder hook; does nothing unless film mode is on. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void livelyvillagers$filmFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
		FilmClient.onFrameRendered();
	}
}
