package net.fentbusgaming.localweather.weather;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The text {@code /localweather} prints. Each version's command gathers the
 * numbers and sends these lines; the wording lives here so it is written once
 * and stays the same on every loader.
 */
public final class WeatherReport {

    private static final String[] COMPASS = {
            "north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"
    };

    private WeatherReport() {}

    /**
     * @param zone        the zone the player is in, or null if it has none yet
     * @param tracked     zones held for this dimension
     * @param idle        how many of those are dormant
     * @param nearestCell distance in blocks to the nearest storm cell centre, or a negative value for none
     * @param cellDx      x offset from the player to that cell
     * @param cellDz      z offset from the player to that cell
     * @param insideCell  whether the player is under that cell
     */
    public static List<String> lines(WeatherZone zone, int zoneX, int zoneZ,
                                     double windAngle, double windTarget,
                                     int tracked, int idle,
                                     double nearestCell, double cellDx, double cellDz, boolean insideCell) {
        List<String> out = new ArrayList<>();

        if (zone == null) {
            out.add("No weather here yet (zone " + zoneX + ", " + zoneZ + ").");
        } else {
            out.add("Weather here: " + name(zone.getTargetWeather()) + ", " + timeLeft(zone.getWeatherDuration())
                    + " (zone " + zoneX + ", " + zoneZ + ")");
            if (zone.getTransitionProgress() < 1.0f) {
                out.add("Changing from " + name(zone.getCurrentWeather()).toLowerCase(Locale.ROOT) + ", "
                        + Math.round(zone.getTransitionProgress() * 100) + "% of the way");
            }
        }

        boolean turning = Math.abs(normalize(windTarget - windAngle)) > 0.05;
        out.add("Wind from the " + windFrom(windAngle) + (turning ? ", turning" : ""));

        if (insideCell) {
            out.add("You are under a storm cell.");
        } else if (nearestCell >= 0) {
            out.add("Nearest storm cell: " + Math.round(nearestCell) + " blocks " + bearing(cellDx, cellDz));
        }

        out.add("Tracking " + tracked + " zone" + (tracked == 1 ? "" : "s") + " here, " + idle + " idle");
        return out;
    }

    static String name(WeatherZone.WeatherType type) {
        String n = type.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    /** Ticks to rough wall-clock time; the duration is a random draw, so minutes are plenty. */
    static String timeLeft(int ticks) {
        if (ticks <= 0) return "changing soon";
        int minutes = ticks / 1200;
        if (minutes < 1) return "under a minute left";
        if (minutes < 60) return "about " + minutes + " min left";
        int hours = minutes / 60;
        int rest = minutes % 60;
        return "about " + hours + " h " + (rest > 0 ? rest + " min " : "") + "left";
    }

    /**
     * The compass point the wind blows from. {@code windAngle} is the direction
     * it blows toward, measured from +X (east) toward +Z (south).
     */
    static String windFrom(double windAngle) {
        return bearing(-Math.cos(windAngle), -Math.sin(windAngle));
    }

    /** Compass point of an XZ offset, with north as -Z. */
    static String bearing(double dx, double dz) {
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        if (degrees < 0) degrees += 360.0;
        int index = (int) Math.round(degrees / 45.0) % 8;
        return COMPASS[index];
    }

    private static double normalize(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }
}
