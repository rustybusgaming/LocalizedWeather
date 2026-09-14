package net.fentbusgaming.localweather.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.AtmosphericFogModifier;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brings in fog when a storm is approaching. Storms in neighboring zones
 * cause a light fog effect that thickens as the storm gets closer, giving
 * the feeling of an approaching weather front.
 */
@Environment(EnvType.CLIENT)
@Mixin(AtmosphericFogModifier.class)
public abstract class FogMixin {

    /**
     * After vanilla applies atmospheric fog, tighten the fog end distance when near storms
     * to create a hazy, overcast look in the direction of an approaching storm.
     */
    /*
     * 1.21.11 replaced the (entity, pos) pair with a single Camera. This line
     * still takes the pair, and the injected method's descriptor has to match
     * the target exactly, so the two lines cannot share this signature.
     */
    @Inject(method = "applyStartEndModifier", at = @At("RETURN"))
    private void localweather$stormFog(FogData fogData,
                                       Entity cameraEntity,
                                       BlockPos cameraPos,
                                       ClientWorld world,
                                       float viewDistance,
                                       RenderTickCounter tickCounter,
                                       CallbackInfo ci) {
        float rain = ClientWeatherHandler.getRainDarkening();
        float thunder = ClientWeatherHandler.getThunderDarkening();
        float proximity = ClientWeatherHandler.getStormProximityDarkening();
        float factor = Math.max(rain, proximity);
        if (factor < 0.02f) return;

        // Rain: pull fog in by up to 40%. Thunder: up to 55%.
        float fogReduction = factor * (0.40f + thunder * 0.15f);
        float stormEnd = fogData.environmentalEnd * (1.0f - fogReduction);
        float stormStart = fogData.environmentalStart * (1.0f - factor * 0.25f);

        if (stormEnd < fogData.environmentalEnd) {
            fogData.environmentalEnd = stormEnd;
            fogData.environmentalStart = Math.min(stormStart, stormEnd - 10f);
        }
    }
}
