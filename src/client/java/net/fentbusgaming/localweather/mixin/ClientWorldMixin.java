package net.fentbusgaming.localweather.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side mixin that overrides Biome.getPrecipitation(BlockPos) so our
 * localized zone weather controls what precipitation type the renderer uses.
 *
 * Only overrides for SNOW zones (forces snow particles regardless of biome).
 * For CLEAR, we rely on rainGradient being 0 to naturally suppress particles.
 * For RAIN/THUNDER, vanilla decides based on biome temperature.
 */
@Environment(EnvType.CLIENT)
@Mixin(Biome.class)
public abstract class ClientWorldMixin {

    @Inject(
        method = "getPrecipitation",
        at = @At("HEAD"),
        cancellable = true
    )
    private void localweather$overridePrecipitation(
            BlockPos pos,
            CallbackInfoReturnable<Biome.Precipitation> cir) {

        WeatherZone.WeatherType zone = ClientWeatherHandler.getCurrentZoneWeather();

        // Only override for SNOW — force snow particles regardless of biome temp.
        // CLEAR is handled by rainGradient being 0 (no need to override here,
        // doing so would kill rain particles from neighboring zones too).
        // RAIN / THUNDER: let vanilla decide based on biome.
        if (zone == WeatherZone.WeatherType.SNOW) {
            cir.setReturnValue(Biome.Precipitation.SNOW);
        }
    }
}
