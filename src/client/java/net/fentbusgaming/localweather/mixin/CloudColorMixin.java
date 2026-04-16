package net.fentbusgaming.localweather.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ClientWorld.class)
public abstract class CloudColorMixin {

    @Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true)
    private void localweather$darkenStormClouds(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        float rain = ClientWeatherHandler.getRainDarkening();
        float thunder = ClientWeatherHandler.getThunderDarkening();
        float proximity = ClientWeatherHandler.getStormProximityDarkening();
        float factor = Math.max(rain, proximity);
        if (factor < 0.01f) return;

        Vec3d original = cir.getReturnValue();

        // Rain clouds: dark grey. Thunder clouds: very dark.
        double stormR = 0.35 - thunder * 0.15;
        double stormG = 0.37 - thunder * 0.17;
        double stormB = 0.40 - thunder * 0.15;

        double r = original.x + (stormR - original.x) * factor;
        double g = original.y + (stormG - original.y) * factor;
        double b = original.z + (stormB - original.z) * factor;

        cir.setReturnValue(new Vec3d(r, g, b));
    }
}
