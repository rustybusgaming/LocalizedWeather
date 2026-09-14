package net.fentbusgaming.localweather.mixin;

import net.minecraft.server.level.ServerLevel;
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
 * We cancel the body, leaving rain/thunder flags permanently at "not raining"
 * on the server side. Clients receive their individual zone weather via
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
        ci.cancel();
        // Vanilla weather is fully suppressed; LocalWeather handles everything.
    }
}
