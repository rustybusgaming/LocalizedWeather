package net.fentbusgaming.localweather.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brings in fog when a storm is approaching. Storms in neighboring zones
 * cause a light fog effect that thickens as the storm gets closer, giving
 * the feeling of an approaching weather front.
 */
@Environment(EnvType.CLIENT)
@Mixin(BackgroundRenderer.class)
public abstract class FogMixin {

    /**
     * After vanilla applies fog, tighten the fog end distance when near storms
     * to create a hazy, overcast look in the direction of an approaching storm.
     */
    @Inject(method = "applyFog", at = @At("RETURN"))
    private static void localweather$stormFog(Camera camera,
                                               BackgroundRenderer.FogType fogType,
                                               float viewDistance,
                                               boolean thickFog,
                                               float tickDelta,
                                               CallbackInfo ci) {
        float rain = ClientWeatherHandler.getRainDarkening();
        float thunder = ClientWeatherHandler.getThunderDarkening();
        float proximity = ClientWeatherHandler.getStormProximityDarkening();
        float factor = Math.max(rain, proximity);
        if (factor < 0.02f) return;

        float fogEnd = RenderSystem.getShaderFogEnd();
        float fogStart = RenderSystem.getShaderFogStart();

        // Rain: pull fog in by up to 40%. Thunder: up to 55%.
        float fogReduction = factor * (0.40f + thunder * 0.15f);
        float stormFogEnd = fogEnd * (1.0f - fogReduction);
        float stormFogStart = fogStart * (1.0f - factor * 0.25f);

        if (stormFogEnd < fogEnd) {
            RenderSystem.setShaderFogEnd(stormFogEnd);
            RenderSystem.setShaderFogStart(Math.min(stormFogStart, stormFogEnd - 10f));
        }
    }
}
