package net.fentbusgaming.localweather.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fentbusgaming.localweather.render.HailParticleRenderer;
import net.fentbusgaming.localweather.render.RainCurtainRenderer;
import net.fentbusgaming.localweather.render.StormCloudRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the storm clouds, hail and rain wall on 1.21.9.
 *
 * Fabric API for 1.21.9 has no world-render event: WorldRenderEvents only
 * returns with the 1.21.10-era builds. So vanilla's main pass is hooked
 * directly, at the end of renderBlockDamage. That is the same place the Forge
 * port hooks, for the same reasons. It runs every frame inside the main pass,
 * after entities. It is handed the frame's MatrixStack and the entity
 * vertex-consumer buffer, which is what these renderers draw into on 1.21.10.
 * Vanilla flushes that buffer straight after this call, so nothing here needs
 * flushing by hand. It is a named method, not a compiler-generated lambda,
 * so the target does not depend on how the main pass happens to be compiled.
 */
@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Inject(method = "renderBlockDamage", at = @At("RETURN"))
    private void localweather$drawWeather(MatrixStack matrices,
                                          VertexConsumerProvider.Immediate consumers,
                                          WorldRenderState state,
                                          CallbackInfo ci) {
        StormCloudRenderer.render(matrices, consumers);
        HailParticleRenderer.render(matrices, consumers);
        RainCurtainRenderer.render(matrices, consumers);
    }
}
