package net.fentbusgaming.localweather.mixin;

import net.fentbusgaming.localweather.config.LocalWeatherConfig;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses vanilla's global weather tick so LocalWeather can fully control
 * precipitation per-zone rather than having a single world-wide weather state
 * fight our custom zones.
 */
@Mixin(ServerLevel.class)
public abstract class ServerWorldMixin {

    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void localweather$cancelVanillaWeatherTick(CallbackInfo ci) {
        if (LocalWeatherConfig.suppressVanillaWeather()) {
            ci.cancel();
            localweather$keepVanillaClear((ServerLevel) (Object) this);
        }
    }

    /**
     * Hold vanilla's own weather at clear while the zones run. Cancelling the
     * cycle also stops vanilla's rain from ever running out, so a world that
     * was raining when the mod was installed, or one given {@code /weather rain}
     * or {@code thunder}, stayed raining world-wide for good on the server:
     * {@code isRainingAt} said yes in every clear zone, and vanilla's thunder
     * lightning could strike anywhere.
     */
    private static void localweather$keepVanillaClear(ServerLevel level) {
        if (level.getLevelData().isRaining() || level.getLevelData().isThundering()) {
            level.setWeatherParameters(0, 0, false, false);
        }
        if (level.getRainLevel(1.0f) > 0.0f) level.setRainLevel(0.0f);
        if (level.getThunderLevel(1.0f) > 0.0f) level.setThunderLevel(0.0f);
    }

    /**
     * Snow layering and cauldron filling. Vanilla only does either while its
     * world-wide rain flag is up, which the zones keep down, so a snow zone
     * never laid any snow and rain never filled a cauldron. Ask the zone at
     * this column instead, keeping vanilla's own answer for a server that lets
     * vanilla rain run as well. The biome rules after this call are untouched:
     * snow still only settles where it is cold enough.
     */
    @Redirect(method = "tickPrecipitation(Lnet/minecraft/core/BlockPos;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isRaining()Z"))
    private boolean localweather$precipitationFromZone(ServerLevel level, BlockPos pos) {
        return level.isRaining() || WeatherZoneManager.isPrecipitatingAt(level, pos.getX() + 0.5, pos.getZ() + 0.5);
    }

    /**
     * Vanilla wakes every sleeper here once a night has been slept through, and
     * then clears its own weather, which the zones keep clear already. Clear
     * the zones around the sleepers instead, while they are still in bed.
     */
    @Inject(method = "wakeUpAllPlayers()V", at = @At("HEAD"))
    private void localweather$clearWeatherAfterSleep(CallbackInfo ci) {
        WeatherZoneManager.clearWeatherAfterSleep((ServerLevel) (Object) this);
    }
}
