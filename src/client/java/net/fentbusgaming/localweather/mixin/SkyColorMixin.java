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
public abstract class SkyColorMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void localweather$darkenStormSky(Vec3d cameraPos, float tickDelta,
                                              CallbackInfoReturnable<Vec3d> cir) {
        float rain = ClientWeatherHandler.getRainDarkening();
        float thunder = ClientWeatherHandler.getThunderDarkening();
        float proximity = ClientWeatherHandler.getStormProximityDarkening();
        float factor = Math.max(rain, proximity);
        if (factor < 0.01f) return;

        Vec3d original = cir.getReturnValue();

        // Overcast sky: grey. Thunder sky: very dark grey.
        double stormR = 0.55 - thunder * 0.25;
        double stormG = 0.57 - thunder * 0.27;
        double stormB = 0.60 - thunder * 0.25;

        double r = original.x + (stormR - original.x) * factor;
        double g = original.y + (stormG - original.y) * factor;
        double b = original.z + (stormB - original.z) * factor;

        cir.setReturnValue(new Vec3d(r, g, b));
    }
}
