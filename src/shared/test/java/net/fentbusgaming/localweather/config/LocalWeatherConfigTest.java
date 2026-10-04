package net.fentbusgaming.localweather.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class LocalWeatherConfigTest {

    private static final int TICKS_PER_MINUTE = 1200;

    private static void use(String... keyValues) {
        Properties p = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            p.setProperty(keyValues[i], keyValues[i + 1]);
        }
        LocalWeatherConfig.useForTesting(p);
    }

    @AfterEach
    void restoreDefaults() {
        LocalWeatherConfig.useForTesting(new Properties());
    }

    @Test
    void emptyConfigReproducesTheShippedBehaviour() {
        use();
        assertEquals(10 * TICKS_PER_MINUTE, LocalWeatherConfig.clearTicksMin());
        assertEquals(150 * TICKS_PER_MINUTE, LocalWeatherConfig.clearTicksMax());
        assertEquals(10 * TICKS_PER_MINUTE, LocalWeatherConfig.wetTicksMin());
        assertEquals(20 * TICKS_PER_MINUTE, LocalWeatherConfig.wetTicksMax());
        assertTrue(LocalWeatherConfig.lightningEnabled());
        assertEquals(1200, LocalWeatherConfig.lightningRarity());
        assertTrue(LocalWeatherConfig.stormCellsEnabled());
        assertEquals(4, LocalWeatherConfig.stormCellsMax());
        assertTrue(LocalWeatherConfig.suppressVanillaWeather());
    }

    @Test
    void minutesAreConvertedToTicks() {
        use("clear-minutes-min", "2", "clear-minutes-max", "5");
        assertEquals(2 * TICKS_PER_MINUTE, LocalWeatherConfig.clearTicksMin());
        assertEquals(5 * TICKS_PER_MINUTE, LocalWeatherConfig.clearTicksMax());
    }

    @Test
    void maxBelowMinIsWidenedRatherThanRejected() {
        // The simulation feeds max - min to Random.nextInt, so a negative
        // spread would throw on the server thread.
        use("rain-minutes-min", "30", "rain-minutes-max", "3");
        assertEquals(LocalWeatherConfig.wetTicksMin(), LocalWeatherConfig.wetTicksMax());
        assertEquals(30 * TICKS_PER_MINUTE, LocalWeatherConfig.wetTicksMax());
    }

    @Test
    void booleansAcceptTheUsualSpellings() {
        use("lightning", "no", "storm-cells", "0", "suppress-vanilla-weather", "FALSE");
        assertFalse(LocalWeatherConfig.lightningEnabled());
        assertFalse(LocalWeatherConfig.stormCellsEnabled());
        assertFalse(LocalWeatherConfig.suppressVanillaWeather());

        use("lightning", "yes", "storm-cells", "1", "suppress-vanilla-weather", " True ");
        assertTrue(LocalWeatherConfig.lightningEnabled());
        assertTrue(LocalWeatherConfig.stormCellsEnabled());
        assertTrue(LocalWeatherConfig.suppressVanillaWeather());
    }

    @Test
    void malformedValuesFallBackToDefaults() {
        use("lightning-rarity", "banana", "storm-cells", "maybe", "clear-minutes-min", "");
        assertEquals(1200, LocalWeatherConfig.lightningRarity());
        assertTrue(LocalWeatherConfig.stormCellsEnabled());
        assertEquals(10 * TICKS_PER_MINUTE, LocalWeatherConfig.clearTicksMin());
    }

    @Test
    void outOfRangeValuesAreClamped() {
        use("storm-cells-max", "99", "lightning-rarity", "0", "clear-minutes-min", "-5");
        assertEquals(64, LocalWeatherConfig.stormCellsMax());
        assertEquals(1, LocalWeatherConfig.lightningRarity(), "a rarity of 0 would divide by zero in nextInt");
        assertEquals(TICKS_PER_MINUTE, LocalWeatherConfig.clearTicksMin());
    }

    @Test
    void zeroStormCellsIsAllowed() {
        use("storm-cells-max", "0");
        assertEquals(0, LocalWeatherConfig.stormCellsMax());
    }
}
