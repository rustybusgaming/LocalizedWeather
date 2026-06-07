package net.fentbusgaming.localweather.mixin;

import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.CloudRenderer;
import net.minecraft.util.math.Vec3d;
import net.fentbusgaming.localweather.render.StormCloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void overrideClouds(int skyLightmapUV, CloudRenderMode cloudMode, float tickDelta, Vec3d cameraPos, long worldTime,
                                float partialTicks, CallbackInfo ci) {
        ci.cancel();
        StormCloudRenderer.renderCustomClouds(cameraPos, partialTicks);
    }
}
