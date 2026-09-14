package net.fentbusgaming.localweather.mixin;

import net.fentbusgaming.localweather.weather.WeatherZone;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.server.command.WeatherCommand")
public abstract class WeatherCommandMixin {

    @Inject(method = "executeClear", at = @At("RETURN"))
    private static void localweather$applyClear(ServerCommandSource source, int duration, CallbackInfoReturnable<Integer> cir) {
        applyLocalWeather(source, WeatherZone.WeatherType.CLEAR, duration);
    }

    @Inject(method = "executeRain", at = @At("RETURN"))
    private static void localweather$applyRain(ServerCommandSource source, int duration, CallbackInfoReturnable<Integer> cir) {
        applyLocalWeather(source, WeatherZone.WeatherType.RAIN, duration);
    }

    @Inject(method = "executeThunder", at = @At("RETURN"))
    private static void localweather$applyThunder(ServerCommandSource source, int duration, CallbackInfoReturnable<Integer> cir) {
        applyLocalWeather(source, WeatherZone.WeatherType.THUNDER, duration);
    }

    private static void applyLocalWeather(ServerCommandSource source, WeatherZone.WeatherType weather, int duration) {
        int ticks = duration >= 1000 ? duration : duration * 20;
        BlockPos pos = BlockPos.ofFloored(source.getPosition());
        int[] zoneCoords = net.fentbusgaming.localweather.api.LocalWeatherAPI.toZoneCoords(pos.getX(), pos.getZ());
        WeatherZoneManager.forceWeatherAt(source.getWorld(), zoneCoords[0], zoneCoords[1], weather, ticks);
    }
}
