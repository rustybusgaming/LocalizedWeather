package net.fentbusgaming.localweather.mixin;

import net.fentbusgaming.localweather.weather.WeatherZone;
import net.fentbusgaming.localweather.weather.StormCellManager;
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
 * Answering it from the zone at that position gives all rain behavior back,
 * per-zone rather than world-wide.
 */
@Mixin(Level.class)
public abstract class RainAtMixin {

    @Inject(method = "isRainingAt", at = @At("HEAD"), cancellable = true)
    private void localweather$rainFromZone(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ServerLevel world)) return;

        if (!wetAt(world, pos)) {
            return;
        }

        // Same exposure rules vanilla applies: open to the sky, and a biome that
        // rains rather than snows at this height.
        if (world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() > pos.getY()) {
            cir.setReturnValue(false);
            return;
        }
        if (world.getBiome(pos).value().getPrecipitationAt(pos) != Biome.Precipitation.RAIN) {
            cir.setReturnValue(false);
            return;
        }

        cir.setReturnValue(true);
    }

    /**
     * Rain, thunder or hail in the zone, or a storm cell's core overhead. Hail
     * falls with rain, and a cell carries its own rain across whatever zone it
     * passes over; the client already draws both as rain, and until now the
     * server disagreed — fires kept burning under a passing storm core.
     */
    private static boolean wetAt(ServerLevel world, BlockPos pos) {
        WeatherZone zone = WeatherZoneManager.getZone(world, (pos.getX() >> 4) >> 4, (pos.getZ() >> 4) >> 4);
        if (zone != null) {
            WeatherZone.WeatherType weather = zone.getCurrentWeather();
            if (weather == WeatherZone.WeatherType.RAIN
                    || weather == WeatherZone.WeatherType.THUNDER
                    || weather == WeatherZone.WeatherType.HAIL) {
                return true;
            }
        }
        return StormCellManager.getCellAt(world, pos.getX() + 0.5, pos.getZ() + 0.5) != null;
    }
}
