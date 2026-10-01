package net.fentbusgaming.localweather.mixin;

import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

/**
 * Hides vanilla's cloud layer once the localized storm deck has taken over the
 * sky, so the two are not stacked on top of each other.
 */
@Mixin(LevelRenderer.class)
public abstract class CloudMixin {

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void localweather$hideCloudsUnderStorm(PoseStack poseStack, Matrix4f modelViewMatrix, Matrix4f projectionMatrix,
                                                  float partialTick, double camX, double camY, double camZ, CallbackInfo ci) {
        if (ClientWeatherHandler.isHidingVanillaClouds()) {
            ci.cancel();
        }
    }
}
