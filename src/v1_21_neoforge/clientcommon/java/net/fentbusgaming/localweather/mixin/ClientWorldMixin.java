package net.fentbusgaming.localweather.mixin;

import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side mixin that overrides Biome.getPrecipitationAt(BlockPos) so our
 * localized zone weather controls what precipitation type the renderer uses.
 */
@Mixin(Biome.class)
public abstract class ClientWorldMixin {

    @Inject(
        method = "getPrecipitationAt",
        at = @At("HEAD"),
        cancellable = true
    )
    private void localweather$overridePrecipitation(
            BlockPos pos,
            CallbackInfoReturnable<Biome.Precipitation> cir) {

        // Biome is shared with the integrated server in singleplayer, and this is
        // the local player's zone. Overriding off the client thread made every
        // server-side precipitation check in the world answer snow while the
        // player stood in a snow zone — rain checks failed everywhere else, and
        // cauldrons in rain filled with powder snow.
        Minecraft client = Minecraft.getInstance();
        if (client == null || !client.isSameThread()) return;

        WeatherZone.WeatherType zone = ClientWeatherHandler.getCurrentZoneWeather();

        if (zone == WeatherZone.WeatherType.SNOW) {
            cir.setReturnValue(Biome.Precipitation.SNOW);
        }
    }
}
