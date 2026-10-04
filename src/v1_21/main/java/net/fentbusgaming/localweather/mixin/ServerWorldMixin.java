package net.fentbusgaming.localweather.mixin;

import net.fentbusgaming.localweather.config.LocalWeatherConfig;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses vanilla's global weather tick so LocalWeather can fully control
 * precipitation per-zone rather than having a single world-wide weather state
 * fight our custom zones.
 *
 * Vanilla weather tick is in ServerWorld#tickWeather() (Yarn mapped).
 * We cancel the body and hold the rain/thunder flags at "not raining" on the
 * server side. Clients receive their individual zone weather via
 * our custom S2C packet and the ClientWorldMixin applies it locally.
 */
@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {

    /**
     * Cancel vanilla weather ticking so it does not override our per-zone state.
     * The injection targets the private tickWeather() method inside ServerWorld.
     */
    @Inject(method = "tickWeather", at = @At("HEAD"), cancellable = true)
    private void localweather$cancelVanillaWeatherTick(CallbackInfo ci) {
        // Suppressed by default, so the zones are the only thing putting rain
        // in the sky. A server can turn this off in the config and let vanilla
        // run its cycle alongside them.
        if (LocalWeatherConfig.suppressVanillaWeather()) {
            ci.cancel();
            localweather$keepVanillaClear((ServerWorld) (Object) this);
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
    private static void localweather$keepVanillaClear(ServerWorld world) {
        if (world.getLevelProperties().isRaining() || world.getLevelProperties().isThundering()) {
            world.setWeather(0, 0, false, false);
        }
        if (world.getRainGradient(1.0f) > 0.0f) world.setRainGradient(0.0f);
        if (world.getThunderGradient(1.0f) > 0.0f) world.setThunderGradient(0.0f);
    }
}
