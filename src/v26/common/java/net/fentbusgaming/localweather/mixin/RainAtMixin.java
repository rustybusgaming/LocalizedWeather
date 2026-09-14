package net.fentbusgaming.localweather.mixin;

import net.fentbusgaming.localweather.weather.WeatherZone;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes localized rain physically real.
 *
 * Vanilla weather is suppressed, so {@code isRaining()} is permanently false and
 * vanilla's own {@code isRainingAt} could never return true — the zone weather
 * was visual only. A great deal of rain behaviour funnels through this one
 * method: mobs not burning in daylight, cauldrons filling, farmland hydrating,
 * campfires going out. Answering it from the zone at that position gives all of
 * it back, per-zone rather than world-wide.
 *
 * Only the server side is answered here. The client keeps vanilla's answer,
 * since its rain visuals already come from the blended zone gradients.
 */
@Mixin(Level.class)
public abstract class RainAtMixin {

    @Inject(method = "isRainingAt", at = @At("HEAD"), cancellable = true)
    private void localweather$rainFromZone(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ServerLevel world)) return;

        WeatherZone.WeatherType weather = zoneWeatherAt(world, pos);
        if (weather != WeatherZone.WeatherType.RAIN && weather != WeatherZone.WeatherType.THUNDER) {
            // Vanilla would answer false anyway; let it.
            return;
        }

        // Same exposure rules vanilla applies: open to the sky, and a biome that
        // rains rather than snows at this height.
        if (world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() > pos.getY()) {
            cir.setReturnValue(false);
            return;
        }
        if (world.getBiome(pos).value().getPrecipitationAt(pos, world.getSeaLevel()) != Biome.Precipitation.RAIN) {
            cir.setReturnValue(false);
            return;
        }

        cir.setReturnValue(true);
    }

    private static WeatherZone.WeatherType zoneWeatherAt(ServerLevel world, BlockPos pos) {
        WeatherZone zone = WeatherZoneManager.getZone(world, (pos.getX() >> 4) >> 4, (pos.getZ() >> 4) >> 4);
        return zone == null ? WeatherZone.WeatherType.CLEAR : zone.getCurrentWeather();
    }
}
