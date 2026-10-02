package com.livelyvillagers.dev.client.mixin;

import com.livelyvillagers.dev.FilmState;
import com.livelyvillagers.dev.client.FilmClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dev film camera: overrides the view every frame with the exact pose for that frame's timestamp. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Inject(method = "setup", at = @At("TAIL"))
	private void livelyvillagers$filmCamera(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
		FilmState.Pose p = FilmClient.framePose();
		if (p != null) {
			setRotation(p.yaw(), p.pitch());
			setPosition(p.x(), p.y(), p.z());
		}
	}
}
