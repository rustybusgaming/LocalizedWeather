package net.fentbusgaming.localweather.api;

import net.fentbusgaming.localweather.weather.StormCell;
import net.fentbusgaming.localweather.weather.StormCellManager;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.fentbusgaming.localweather.weather.WindState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Public API for other mods to query localized weather information.
 */
public final class LocalWeatherAPI {

    private LocalWeatherAPI() {}

    public static WeatherZone.WeatherType getWeatherAt(ServerLevel world, BlockPos pos) {
        int zoneX = (pos.getX() >> 4) >> 4;
        int zoneZ = (pos.getZ() >> 4) >> 4;
        return getWeatherInZone(world, zoneX, zoneZ);
    }

    public static WeatherZone.WeatherType getWeatherInZone(ServerLevel world, int zoneX, int zoneZ) {
        WeatherZone zone = WeatherZoneManager.getZone(world, zoneX, zoneZ);
        if (zone == null) {
            return WeatherZone.WeatherType.CLEAR;
        }
        return zone.getCurrentWeather();
    }

    public static WeatherZone.WeatherType getTargetWeatherInZone(ServerLevel world, int zoneX, int zoneZ) {
        WeatherZone zone = WeatherZoneManager.getZone(world, zoneX, zoneZ);
        if (zone == null) {
            return WeatherZone.WeatherType.CLEAR;
        }
        return zone.getTargetWeather();
    }

    public static float getTransitionProgress(ServerLevel world, int zoneX, int zoneZ) {
        WeatherZone zone = WeatherZoneManager.getZone(world, zoneX, zoneZ);
        if (zone == null) {
            return 1.0f;
        }
        return zone.getTransitionProgress();
    }

    public static boolean isRainingAt(ServerLevel world, BlockPos pos) {
        WeatherZone.WeatherType weather = getWeatherAt(world, pos);
        return weather == WeatherZone.WeatherType.RAIN || weather == WeatherZone.WeatherType.THUNDER;
    }

    public static boolean isThunderingAt(ServerLevel world, BlockPos pos) {
        return getWeatherAt(world, pos) == WeatherZone.WeatherType.THUNDER;
    }

    public static boolean isHailingAt(ServerLevel world, BlockPos pos) {
        return getWeatherAt(world, pos) == WeatherZone.WeatherType.HAIL;
    }

    public static List<StormCell> getStormCells(ServerLevel world) {
        return StormCellManager.getCells(world);
    }

    public static StormCell getStormCellAt(ServerLevel world, BlockPos pos) {
        return StormCellManager.getCellAt(world, pos.getX(), pos.getZ());
    }

    public static boolean isInStormCell(ServerLevel world, BlockPos pos) {
        return getStormCellAt(world, pos) != null;
    }

    public static double getWindDirectionX() {
        return WindState.getWindDirX();
    }

    public static double getWindDirectionZ() {
        return WindState.getWindDirZ();
    }

    public static int[] toZoneCoords(int blockX, int blockZ) {
        return new int[]{
                (blockX >> 4) >> 4,
                (blockZ >> 4) >> 4
        };
    }

    public static int getZoneSizeBlocks() {
        return WeatherZoneManager.CHUNKS_PER_ZONE * 16;
    }
}
