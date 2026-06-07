package net.fentbusgaming.localweather.api;

import net.fentbusgaming.localweather.weather.WeatherZone;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.fentbusgaming.localweather.weather.WindState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Public API for other mods to query localized weather information.
 *
 * <p>All methods are thread-safe and can be called from any context that has
 * access to a {@link ServerWorld}.</p>
 *
 * <h2>Usage example:</h2>
 * <pre>{@code
 * WeatherZone.WeatherType weather = LocalWeatherAPI.getWeatherAt(world, pos);
 * if (weather == WeatherZone.WeatherType.THUNDER) {
 *     // lightning protection logic
 * }
 * }</pre>
 */
public final class LocalWeatherAPI {

    private LocalWeatherAPI() {}

    /**
     * Get the current weather type at a specific block position.
     *
     * @param world the server world
     * @param pos   the block position to check
     * @return the current weather type at that position
     */
    public static WeatherZone.WeatherType getWeatherAt(ServerWorld world, BlockPos pos) {
        int zoneX = (pos.getX() >> 4) >> 4; // blockX -> chunkX -> zoneX
        int zoneZ = (pos.getZ() >> 4) >> 4;
        return getWeatherInZone(world, zoneX, zoneZ);
    }

    /**
     * Get the current weather type for a specific zone.
     *
     * @param world the server world
     * @param zoneX the zone X coordinate
     * @param zoneZ the zone Z coordinate
     * @return the current weather type, or CLEAR if the zone hasn't been loaded
     */
    public static WeatherZone.WeatherType getWeatherInZone(ServerWorld world, int zoneX, int zoneZ) {
        WeatherZone zone = WeatherZoneManager.getZone(world, zoneX, zoneZ);
        if (zone == null) {
            return WeatherZone.WeatherType.CLEAR;
        }
        return zone.getCurrentWeather();
    }

    /**
     * Get the target weather a zone is transitioning toward.
     *
     * @param world the server world
     * @param zoneX the zone X coordinate
     * @param zoneZ the zone Z coordinate
     * @return the target weather type, or CLEAR if the zone hasn't been loaded
     */
    public static WeatherZone.WeatherType getTargetWeatherInZone(ServerWorld world, int zoneX, int zoneZ) {
        WeatherZone zone = WeatherZoneManager.getZone(world, zoneX, zoneZ);
        if (zone == null) {
            return WeatherZone.WeatherType.CLEAR;
        }
        return zone.getTargetWeather();
    }

    /**
     * Get the weather transition progress for a zone (0.0 = start, 1.0 = complete).
     *
     * @param world the server world
     * @param zoneX the zone X coordinate
     * @param zoneZ the zone Z coordinate
     * @return the transition progress, or 1.0 if the zone hasn't been loaded
     */
    public static float getTransitionProgress(ServerWorld world, int zoneX, int zoneZ) {
        WeatherZone zone = WeatherZoneManager.getZone(world, zoneX, zoneZ);
        if (zone == null) {
            return 1.0f;
        }
        return zone.getTransitionProgress();
    }

    /**
     * Check if it is raining at a specific position (includes thunderstorms).
     *
     * @param world the server world
     * @param pos   the block position
     * @return true if rain or thunder is active at this position
     */
    public static boolean isRainingAt(ServerWorld world, BlockPos pos) {
        WeatherZone.WeatherType weather = getWeatherAt(world, pos);
        return weather == WeatherZone.WeatherType.RAIN
                || weather == WeatherZone.WeatherType.THUNDER
                || weather == WeatherZone.WeatherType.SNOW
                || weather == WeatherZone.WeatherType.HAIL;
    }

    /**
     * Check if there is a thunderstorm at a specific position.
     *
     * @param world the server world
     * @param pos   the block position
     * @return true if thunder is active at this position
     */
    public static boolean isThunderingAt(ServerWorld world, BlockPos pos) {
        return getWeatherAt(world, pos) == WeatherZone.WeatherType.THUNDER;
    }

    /**
     * Check if there is hail at a specific position.
     *
     * @param world the server world
     * @param pos   the block position
     * @return true if hail is active at this position
     */
    public static boolean isHailingAt(ServerWorld world, BlockPos pos) {
        return getWeatherAt(world, pos) == WeatherZone.WeatherType.HAIL;
    }

    /**
     * Get the current global wind direction X component (unit vector).
     * Wind direction controls which way weather fronts drift.
     *
     * @return X component of the wind direction
     */
    public static double getWindDirectionX() {
        return WindState.getWindDirX();
    }

    /**
     * Get the current global wind direction Z component (unit vector).
     *
     * @return Z component of the wind direction
     */
    public static double getWindDirectionZ() {
        return WindState.getWindDirZ();
    }

    /**
     * Convert block coordinates to zone coordinates.
     *
     * @param blockX the block X coordinate
     * @param blockZ the block Z coordinate
     * @return int array [zoneX, zoneZ]
     */
    public static int[] toZoneCoords(int blockX, int blockZ) {
        int zoneX = (blockX >> 4) >> 4; // blockX -> chunkX -> zoneX
        int zoneZ = (blockZ >> 4) >> 4;
        return new int[]{zoneX, zoneZ};
    }

    /**
     * Get the size of a weather zone in blocks.
     *
     * @return zone size in blocks (256 = 16 chunks * 16 blocks)
     */
    public static int getZoneSizeBlocks() {
        return WeatherZoneManager.CHUNKS_PER_ZONE * 16;
    }
}
