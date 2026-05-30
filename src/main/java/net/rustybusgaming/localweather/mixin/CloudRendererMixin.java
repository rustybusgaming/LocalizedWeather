package net.rustybusgaming.localweather.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.rustybusgaming.localweather.render.StormCloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Replace or append vanilla cloud rendering
@Mixin(WorldRenderer.class)
public abstract class CloudRendererMixin {

    // Intercept the vanilla cloud rendering method
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void overrideClouds(MatrixStack matrices, float tickDelta, double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        // 1. Cancel default cloud rendering if custom clouds should override
        ci.cancel();

        // 2. Render custom storm clouds
        StormCloudRenderer.renderCustomClouds(matrices, tickDelta, cameraX, cameraY, cameraZ);

        // 3. (Optional) Add vanilla-style clouds back if desired, or mix both systems.
        RenderSystem.enableBlend();  // Ensure blending for proper integration
        RenderSystem.defaultBlendFunc();
        // Vanilla rendering logic can be re-added here if needed, as a fallback.
    }
}