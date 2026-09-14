package net.fentbusgaming.localweather.weather;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

/**
 * Contains biome-aware logic for selecting appropriate weather types.
 * Deserts/hot biomes stay dry; cold/snowy biomes get snow instead of rain.
 */
public final class BiomeWeatherRules {

    private BiomeWeatherRules() {}

    /**
     * Given a biome and a desired "wet" weather event (RAIN, THUNDER, or HAIL),
     * return the weather type that actually makes sense for this biome.
     *
     * <ul>
     *   <li>Hot / dry biomes → CLEAR (biome blocks precipitation)</li>
     *   <li>Cold / snowy biomes → SNOW for rain, while hail can still occur</li>
     *   <li>Everything else → the requested type unchanged</li>
     * </ul>
     */
    public static WeatherZone.WeatherType resolveWeather(
            RegistryEntry<Biome> biomeEntry,
            WeatherZone.WeatherType requested) {

        Biome biome = biomeEntry.value();

        if (!biome.hasPrecipitation()) {
            return WeatherZone.WeatherType.CLEAR;
        }

        float temp = biome.getTemperature();

        // Snowy biomes: temperature ≤ 0.15 (same threshold Minecraft uses for snow)
        if (temp <= 0.15f) {
            // Map RAIN → SNOW, THUNDER stays THUNDER (thunderstorms still happen in cold biomes)
            if (requested == WeatherZone.WeatherType.RAIN) {
                return WeatherZone.WeatherType.SNOW;
            }
        }

        return requested;
    }

    /**
     * CLEAR weather is always valid regardless of biome.
     */
    public static boolean isClear(WeatherZone.WeatherType type) {
        return type == WeatherZone.WeatherType.CLEAR;
    }
}
