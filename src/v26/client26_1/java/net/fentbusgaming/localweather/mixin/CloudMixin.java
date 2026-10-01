package net.fentbusgaming.localweather.mixin;

import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.minecraft.client.renderer.CloudRenderer;
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
 * This is the 26.1/26.2 shape of {@code CloudRenderer.render}. 26.3 rewrote it
 * against the new render-pass API and overloaded the name, so it has its own
 * copy in {@code src/v26/client26_3} — hence the explicit descriptor here: the
 * bare method name would silently match whatever the next release calls
 * {@code render}.
 */
@Mixin(CloudRenderer.class)
public abstract class CloudMixin {

    @Inject(
        method = "render(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void localweather$hideCloudsUnderStorm(CallbackInfo ci) {
        if (ClientWeatherHandler.isHidingVanillaClouds()) {
            ci.cancel();
        }
    }
}
