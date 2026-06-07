package net.fentbusgaming.localweather.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(SkyRendering.class)
public abstract class SkyColorMixin {

    @Inject(method = "updateRenderState", at = @At("RETURN"))
    private void localweather$darkenStormSky(ClientWorld world, float tickDelta,
                                              Camera camera, SkyRenderState state,
                                              CallbackInfo ci) {
        float rain = ClientWeatherHandler.getRainDarkening();
        float thunder = ClientWeatherHandler.getThunderDarkening();
        float proximity = ClientWeatherHandler.getStormProximityDarkening();
        float factor = Math.max(rain, proximity);
        
        int color = state.skyColor;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        if (factor >= 0.01f) {
            // Rain: blue-grey tint. Thunder: dark purple. Snow: pale white.
            float weatherR, weatherG, weatherB;
            if (thunder > 0.3f) {
                weatherR = 0.40f - thunder * 0.15f;
                weatherG = 0.38f - thunder * 0.20f;
                weatherB = 0.50f;
            } else if (rain > 0.2f) {
                weatherR = 0.50f - rain * 0.10f;
                weatherG = 0.53f - rain * 0.08f;
                weatherB = 0.60f + rain * 0.10f;
            } else {
                weatherR = 0.70f - rain * 0.15f;
                weatherG = 0.72f - rain * 0.12f;
                weatherB = 0.75f - rain * 0.10f;
            }
            r = r + (weatherR - r) * factor;
            g = g + (weatherG - g) * factor;
            b = b + (weatherB - b) * factor;
        }
        
        if (thunder > 0.15f) {
            float auroraGlow = (float) Math.sin(world.getTime() * 0.04f) * 0.08f;
            auroraGlow = Math.max(0f, auroraGlow) * thunder;
            g += auroraGlow;
            b += auroraGlow * 1.2f;
        }

        state.skyColor = (0xFF << 24)
                | (Math.clamp((int) (r * 255), 0, 255) << 16)
                | (Math.clamp((int) (g * 255), 0, 255) << 8)
                | Math.clamp((int) (b * 255), 0, 255);
    }
}
