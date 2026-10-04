package net.fentbusgaming.localweather.mixin;

import net.fentbusgaming.localweather.weather.WeatherZone;
import net.fentbusgaming.localweather.weather.StormCellManager;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes localized rain physically real.
 *
 * Vanilla weather is suppressed, so {@code isRaining()} is permanently false and
 * vanilla's own {@code hasRain} could never return true — the zone weather was
 * visual only. A great deal of rain behaviour funnels through this one method:
 * mobs not burning in daylight, cauldrons filling, farmland hydrating, campfires
 * going out. Answering it from the zone at that position gives all of it back,
 * per-zone rather than world-wide.
 *
 * Only the server side is answered here. The client keeps vanilla's answer,
 * since its rain visuals already come from the blended zone gradients.
 */
@Mixin(World.class)
public abstract class RainAtMixin {

    @Inject(method = "hasRain", at = @At("HEAD"), cancellable = true)
    private void localweather$rainFromZone(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ServerWorld world)) return;

        if (!wetAt(world, pos)) {
            // Vanilla would answer false anyway; let it.
            return;
        }

        // Same exposure rules vanilla applies: open to the sky, and a biome that
        // rains rather than snows at this height.
        if (world.getTopY(Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ()) > pos.getY()) {
            cir.setReturnValue(false);
            return;
        }
        if (world.getBiome(pos).value().getPrecipitation(pos, world.getSeaLevel()) != Biome.Precipitation.RAIN) {
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
    private static boolean wetAt(ServerWorld world, BlockPos pos) {
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
