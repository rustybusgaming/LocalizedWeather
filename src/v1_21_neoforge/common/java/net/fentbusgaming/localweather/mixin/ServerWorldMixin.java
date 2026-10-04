package net.fentbusgaming.localweather.mixin;

import net.fentbusgaming.localweather.config.LocalWeatherConfig;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
        }
    }
}
