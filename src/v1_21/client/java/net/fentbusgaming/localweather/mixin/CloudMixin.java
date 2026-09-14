package net.fentbusgaming.localweather.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.minecraft.client.render.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides vanilla's cloud layer once the localized storm deck has taken over the
 * sky, so the two are not stacked on top of each other.
 *
 * Vanilla's clouds are global, so this is all-or-nothing for the player rather
 * than per-zone; it keys off the blended rain gradient at the player, which is
 * already the zone weather they are standing in.
 *
 * Targeted by name only: renderClouds gained a parameter between 1.21.10 and
 * 1.21.11, and there is just the one overload on either.
 */
@Environment(EnvType.CLIENT)
@Mixin(CloudRenderer.class)
public abstract class CloudMixin {

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void localweather$hideCloudsUnderStorm(CallbackInfo ci) {
        if (ClientWeatherHandler.isHidingVanillaClouds()) {
            ci.cancel();
        }
    }
}
