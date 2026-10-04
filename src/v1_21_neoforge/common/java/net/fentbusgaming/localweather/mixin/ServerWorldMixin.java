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
}
