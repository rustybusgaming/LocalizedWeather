package net.fentbusgaming.localweather.mixin;

import net.fentbusgaming.localweather.weather.WeatherZone;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.WeatherCommand;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WeatherCommand.class)
public abstract class WeatherCommandMixin {

    @Inject(method = "setClear", at = @At("RETURN"))
    private static void localweather$applyClear(CommandSourceStack source, int duration, CallbackInfoReturnable<Integer> cir) {
        applyLocalWeather(source, WeatherZone.WeatherType.CLEAR, duration);
    }

    @Inject(method = "setRain", at = @At("RETURN"))
    private static void localweather$applyRain(CommandSourceStack source, int duration, CallbackInfoReturnable<Integer> cir) {
        applyLocalWeather(source, WeatherZone.WeatherType.RAIN, duration);
    }

    @Inject(method = "setThunder", at = @At("RETURN"))
    private static void localweather$applyThunder(CommandSourceStack source, int duration, CallbackInfoReturnable<Integer> cir) {
        applyLocalWeather(source, WeatherZone.WeatherType.THUNDER, duration);
    }

    private static void applyLocalWeather(CommandSourceStack source, WeatherZone.WeatherType weather, int duration) {
        int ticks = duration >= 1000 ? duration : duration * 20;
        BlockPos pos = BlockPos.containing(source.getPosition());
        int[] zoneCoords = net.fentbusgaming.localweather.api.LocalWeatherAPI.toZoneCoords(pos.getX(), pos.getZ());
        WeatherZoneManager.forceWeatherAt(source.getLevel(), zoneCoords[0], zoneCoords[1], weather, ticks);
    }
}
