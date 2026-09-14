package net.fentbusgaming.localweather.weather;

import java.util.Random;

/**
 * Global wind state that controls the direction weather fronts drift.
 * Wind direction slowly rotates over time. Used by the server to propagate
 * weather between zones, and by clients for cloud drift direction.
 */
public class WindState {

    private static final Random RANDOM = new Random();

    /** Current wind direction (unit vector in XZ plane). */
    private static double windDirX = 1.0;
    private static double windDirZ = 0.0;

    /** Wind angle in radians. */
    private static double windAngle = 0.0;

    /** Target angle the wind is rotating toward. */
    private static double targetAngle = 0.0;

    /** Ticks until we pick a new target wind direction. */
    private static int ticksUntilShift = 6000;

    /** How fast the angle chases target (radians per tick). */
    private static final double ROTATION_SPEED = 0.0002;

    /** Min/max ticks between wind direction changes. */
    private static final int MIN_SHIFT_TICKS = 3000;  // 2.5 min
    private static final int MAX_SHIFT_TICKS = 12000; // 10 min

    public static void tick() {
        ticksUntilShift--;
        if (ticksUntilShift <= 0) {
            // Pick a new target direction — limited turn so it doesn't whip around
            targetAngle = windAngle + (RANDOM.nextDouble() - 0.5) * Math.PI * 0.8;
            ticksUntilShift = MIN_SHIFT_TICKS + RANDOM.nextInt(MAX_SHIFT_TICKS - MIN_SHIFT_TICKS);
        }

        // Smoothly rotate toward target
        double diff = targetAngle - windAngle;
        // Normalize to [-PI, PI]
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;

        if (Math.abs(diff) > ROTATION_SPEED) {
            windAngle += Math.signum(diff) * ROTATION_SPEED;
        } else {
            windAngle = targetAngle;
        }

        windDirX = Math.cos(windAngle);
        windDirZ = Math.sin(windAngle);
    }

    /** Wind direction X component (unit vector). */
    public static double getWindDirX() {
        return windDirX;
    }

    /** Wind direction Z component (unit vector). */
    public static double getWindDirZ() {
        return windDirZ;
    }

    /** Wind angle in radians. */
    public static double getWindAngle() {
        return windAngle;
    }

    /**
     * Get the upwind zone coordinates from a given zone.
     * Returns [zoneX, zoneZ] of the zone weather is blowing FROM.
     */
    public static int[] getUpwindZone(int zoneX, int zoneZ) {
        // Wind blows FROM upwind TO downwind.
        // The upwind neighbor is in the opposite direction of the wind.
        int upX = zoneX - (int) Math.round(windDirX);
        int upZ = zoneZ - (int) Math.round(windDirZ);
        return new int[]{upX, upZ};
    }
}
