package net.fentbusgaming.localweather.mixin;

import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The 26.3 shape of the sky tint.
 *
 * 26.3 changed {@code SkyRenderState.skyColor} from a packed ARGB {@code int}
 * to a JOML {@code Vector3fc} of linear 0..1 components, so the pack and unpack
 * either side of the tint go away and the maths runs on the floats directly.
 * The tint itself is identical to the 26.1/26.2 copy in
 * {@code src/v26/client26_1} — only the field's type differs.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyColorMixin {

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void localweather$darkenStormSky(ClientLevel world, float tickDelta,
                                              Camera camera, SkyRenderState state,
                                              CallbackInfo ci) {
        float rain = ClientWeatherHandler.getRainDarkening();
        float thunder = ClientWeatherHandler.getThunderDarkening();
        float proximity = ClientWeatherHandler.getStormProximityDarkening();
        WeatherZone.WeatherType currentWeather = ClientWeatherHandler.getCurrentZoneWeather();
        float factor = Math.max(rain, proximity);

        Vector3fc color = state.skyColor;
        float r = color.x();
        float g = color.y();
        float b = color.z();

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

        if (thunder > 0.15f || currentWeather == WeatherZone.WeatherType.HAIL) {
            float time = world.getGameTime() + tickDelta;
            float auroraWave = (float) ((Math.sin(time * 0.035f) + Math.sin(time * 0.012f + 1.7f)) * 0.5f);
            float auroraGlow = Math.max(0f, auroraWave) * (0.06f + thunder * 0.12f);
            g += auroraGlow;
            b += auroraGlow * 1.35f;

            if (currentWeather == WeatherZone.WeatherType.HAIL) {
                float hailGlow = 0.03f + (float) Math.max(0f, Math.sin(time * 0.02f + 0.9f)) * 0.025f;
                r += hailGlow * 0.45f;
                g += hailGlow * 0.65f;
                b += hailGlow;
            }
        }

        state.skyColor = new Vector3f(
                Math.clamp(r, 0.0f, 1.0f),
                Math.clamp(g, 0.0f, 1.0f),
                Math.clamp(b, 0.0f, 1.0f));
    }
}
