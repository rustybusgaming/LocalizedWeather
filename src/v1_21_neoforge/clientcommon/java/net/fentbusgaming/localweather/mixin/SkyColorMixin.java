package net.fentbusgaming.localweather.mixin;

import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class SkyColorMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void localweather$darkenStormSky(Vec3 pos, float tickDelta, CallbackInfoReturnable<Vec3> cir) {
        Vec3 orig = cir.getReturnValue();
        if (orig == null) return;

        float rain = ClientWeatherHandler.getRainDarkening();
        float thunder = ClientWeatherHandler.getThunderDarkening();
        float proximity = ClientWeatherHandler.getStormProximityDarkening();
        WeatherZone.WeatherType currentWeather = ClientWeatherHandler.getCurrentZoneWeather();
        float factor = Math.max(rain, proximity);

        float r = (float) orig.x;
        float g = (float) orig.y;
        float b = (float) orig.z;

        if (factor >= 0.01f) {
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
            ClientLevel world = (ClientLevel) (Object) this;
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

        cir.setReturnValue(new Vec3(
                Math.clamp(r, 0.0f, 1.0f),
                Math.clamp(g, 0.0f, 1.0f),
                Math.clamp(b, 0.0f, 1.0f)
        ));
    }
}
