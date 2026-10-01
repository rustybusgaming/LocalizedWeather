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
 * 26.3 rewrote {@code CloudRenderer.render} against the new render-pass API:
 * where 26.1 and 26.2 took the cloud position, colour and ticks directly, it
 * now takes a {@code CloudStatus} and a {@code RenderPass} and does its own
 * bookkeeping. It also overloaded the name — there is a second, private
 * {@code render(RenderPass, RenderPipeline)} — so the descriptor is spelled out
 * rather than left to match by name, which would inject into both.
 */
@Mixin(CloudRenderer.class)
public abstract class CloudMixin {

    @Inject(
        method = "render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void localweather$hideCloudsUnderStorm(CallbackInfo ci) {
        if (ClientWeatherHandler.isHidingVanillaClouds()) {
            ci.cancel();
        }
    }
}
