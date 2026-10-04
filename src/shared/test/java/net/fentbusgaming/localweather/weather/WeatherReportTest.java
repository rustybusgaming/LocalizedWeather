package net.fentbusgaming.localweather.weather;

import net.fentbusgaming.localweather.weather.WeatherZone.WeatherType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeatherReportTest {

    @Test
    void compassFollowsMinecraftAxes() {
        // North is -Z, east is +X.
        assertEquals("north", WeatherReport.bearing(0, -10));
        assertEquals("east", WeatherReport.bearing(10, 0));
        assertEquals("south", WeatherReport.bearing(0, 10));
        assertEquals("west", WeatherReport.bearing(-10, 0));
        assertEquals("north-east", WeatherReport.bearing(10, -10));
        assertEquals("south-west", WeatherReport.bearing(-10, 10));
    }

    @Test
    void windIsReportedByWhereItComesFrom() {
        // Angle 0 blows toward +X, east, so it comes from the west.
        assertEquals("west", WeatherReport.windFrom(0.0));
        // Toward +Z, south, so from the north.
        assertEquals("north", WeatherReport.windFrom(Math.PI / 2));
        assertEquals("east", WeatherReport.windFrom(Math.PI));
        assertEquals("east", WeatherReport.windFrom(-Math.PI));
    }

    @Test
    void timeLeftIsRounded() {
        assertEquals("changing soon", WeatherReport.timeLeft(0));
        assertEquals("under a minute left", WeatherReport.timeLeft(1199));
        assertEquals("about 4 min left", WeatherReport.timeLeft(4 * 1200 + 600));
        assertEquals("about 2 h left", WeatherReport.timeLeft(120 * 1200));
        assertEquals("about 2 h 5 min left", WeatherReport.timeLeft(125 * 1200));
    }

    @Test
    void fullReport() {
        WeatherZone zone = new WeatherZone(3, -2, WeatherType.CLEAR, 6000);
        zone.setTargetWeather(WeatherType.RAIN);
        for (int i = 0; i < 200; i++) zone.tickTransition();

        List<String> lines = WeatherReport.lines(zone, 3, -2, 0.0, 1.0, 12, 5, 340.4, 200, -200, false);

        assertEquals(List.of(
                "Weather here: Rain, about 5 min left (zone 3, -2)",
                "Changing from clear, 50% of the way",
                "Wind from the west, turning",
                "Nearest storm cell: 340 blocks north-east",
                "Tracking 12 zones here, 5 idle"), lines);
    }

    @Test
    void noZoneNoCellsSteadyWind() {
        List<String> lines = WeatherReport.lines(null, 0, 0, 0.5, 0.5, 1, 0, -1, 0, 0, false);

        assertEquals(List.of(
                "No weather here yet (zone 0, 0).",
                "Wind from the north-west",
                "Tracking 1 zone here, 0 idle"), lines);
    }

    @Test
    void underACell() {
        List<String> lines = WeatherReport.lines(null, 0, 0, 0, 0, 0, 0, 12, 1, 1, true);
        assertTrue(lines.contains("You are under a storm cell."));
    }
}
