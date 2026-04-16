package net.fentbusgaming.localweather.weather;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;

/**
 * Contains biome-aware logic for selecting appropriate weather types.
 * Deserts/hot biomes stay dry; cold/snowy biomes get snow instead of rain.
 */
public final class BiomeWeatherRules {

    private BiomeWeatherRules() {}

    /**
     * Given a biome and a desired "wet" weather event (RAIN or THUNDER),
     * return the weather type that actually makes sense for this biome.
     *
     * <ul>
     *   <li>Hot / dry biomes → CLEAR (biome blocks precipitation)</li>
     *   <li>Cold / snowy biomes → SNOW</li>
     *   <li>Everything else → the requested type unchanged</li>
     * </ul>
     */
    public static WeatherZone.WeatherType resolveWeather(
            RegistryEntry<Biome> biomeEntry,
            WeatherZone.WeatherType requested) {

        Biome biome = biomeEntry.value();

        // Vanillas hasPrecipitation() already handles frozen/snowy logic.
        // We tap into temperature for hot/dry biomes.
        float temp = biome.getTemperature();

        if (!biome.hasPrecipitation()) {
            // Biome explicitly has no precipitation (desert, badlands, etc.)
            return WeatherZone.WeatherType.CLEAR;
        }

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
