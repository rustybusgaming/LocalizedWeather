package net.fentbusgaming.localweather.mixin;

import net.fentbusgaming.localweather.config.LocalWeatherConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.WeatherData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses vanilla's global weather tick so LocalWeather can fully control
 * precipitation per-zone rather than having a single world-wide weather state
 * fight our custom zones.
 *
 * Vanilla weather tick is in ServerLevel#tickWeather() (Yarn mapped).
 * We cancel the body and hold the rain/thunder flags at "not raining" on the
 * server side. Clients receive their individual zone weather via
 * our custom S2C packet and the ClientWorldMixin applies it locally.
 */
@Mixin(ServerLevel.class)
public abstract class ServerWorldMixin {

    /**
     * Cancel vanilla weather ticking so it does not override our per-zone state.
     * The injection targets the private tickWeather() method inside ServerLevel.
     */
    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void localweather$cancelVanillaWeatherTick(CallbackInfo ci) {
        // Suppressed by default, so the zones are the only thing putting rain
        // in the sky. A server can turn this off in the config and let vanilla
        // run its cycle alongside them.
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
        WeatherData data = level.getWeatherData();
        if (data.isRaining() || data.isThundering()) {
            data.setRaining(false);
            data.setThundering(false);
            data.setRainTime(0);
            data.setThunderTime(0);
        }
        if (level.getRainLevel(1.0f) > 0.0f) level.setRainLevel(0.0f);
        if (level.getThunderLevel(1.0f) > 0.0f) level.setThunderLevel(0.0f);
    }
}
