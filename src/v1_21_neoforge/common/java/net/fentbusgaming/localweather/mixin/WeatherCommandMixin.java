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
import com.mojang.brigadier.CommandDispatcher;
import net.fentbusgaming.localweather.api.LocalWeatherAPI;
import net.fentbusgaming.localweather.weather.StormCell;
import net.fentbusgaming.localweather.weather.StormCellManager;
import net.fentbusgaming.localweather.weather.WeatherReport;
import net.fentbusgaming.localweather.weather.WindState;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

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

    /**
     * {@code /localweather}: what the weather is doing where you stand. Read
     * only, so it is open to every player. Registered alongside vanilla's
     * {@code /weather} rather than through each loader's command event, so the
     * same hook works on all of them.
     */
    @Inject(method = "register(Lcom/mojang/brigadier/CommandDispatcher;)V", at = @At("TAIL"))
    private static void localweather$registerReport(CommandDispatcher<CommandSourceStack> dispatcher, CallbackInfo ci) {
        dispatcher.register(Commands.literal("localweather").executes(ctx -> localweather$report(ctx.getSource())));
    }

    private static int localweather$report(CommandSourceStack source) {
        ServerLevel world = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        int[] zone = LocalWeatherAPI.toZoneCoords(pos.getX(), pos.getZ());
        int[] counts = WeatherZoneManager.zoneCounts(world);

        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        StormCell nearest = null;
        double best = Double.MAX_VALUE;
        for (StormCell cell : StormCellManager.getCells(world)) {
            double d = cell.squaredDistanceTo(x, z);
            if (d < best) {
                best = d;
                nearest = cell;
            }
        }

        List<String> lines = WeatherReport.lines(
                WeatherZoneManager.getZone(world, zone[0], zone[1]), zone[0], zone[1],
                WindState.getWindAngle(), WindState.getTargetAngle(),
                counts[0], counts[1],
                nearest == null ? -1 : Math.sqrt(best),
                nearest == null ? 0 : nearest.getX() - x,
                nearest == null ? 0 : nearest.getZ() - z,
                nearest != null && nearest.contains(x, z));
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }
}
