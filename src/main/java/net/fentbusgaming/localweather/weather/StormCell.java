package net.fentbusgaming.localweather.weather;

import java.util.Random;

/**
 * A single-cell thunderstorm: a discrete storm core that travels across the
 * world instead of sitting still inside a zone.
 *
 * A {@link WeatherZone} describes the ambient weather of a 256x256 block area;
 * a storm cell is the localized core living inside a thundery zone. The cell
 * carries its own position, drift velocity and life cycle, which is what lets
 * the client draw a rain wall and trailing rain bands that move with the storm
 * and stay visible from far away.
 */
public class StormCell {

    /** Fraction of the life span spent spinning up to full strength. */
    private static final float GROW_FRACTION = 0.18f;
    /** Fraction of the life span spent dissipating. */
    private static final float DECAY_FRACTION = 0.30f;

    /** Core radius range in blocks. */
    private static final float MIN_RADIUS = 70.0f;
    private static final float MAX_RADIUS = 130.0f;

    /** Cell life span range in ticks (4–10 minutes). */
    private static final int MIN_LIFETIME = 4800;
    private static final int MAX_LIFETIME = 12000;

    /** Travel speed range in blocks per tick (~2.8–6 blocks/second). */
    private static final float MIN_SPEED = 0.14f;
    private static final float MAX_SPEED = 0.30f;

    /** How far a cell may track away from the prevailing wind (radians). */
    private static final double MAX_HEADING_OFFSET = 0.35;

    /** Slow side-to-side wander so cells do not travel in perfect lines. */
    private static final double WOBBLE_SPEED = 0.0011;
    private static final double WOBBLE_AMPLITUDE = 0.22;

    private final int id;
    private final float radius;
    private final int lifetime;
    private final float speed;
    private final double headingOffset;
    private final double wobblePhase;

    private double x;
    private double z;
    private double velX;
    private double velZ;
    private int age;
    private float intensity;

    private StormCell(int id, double x, double z, float radius, int lifetime,
                      float speed, double headingOffset, double wobblePhase) {
        this.id = id;
        this.x = x;
        this.z = z;
        this.radius = radius;
        this.lifetime = lifetime;
        this.speed = speed;
        this.headingOffset = headingOffset;
        this.wobblePhase = wobblePhase;
    }

    /**
     * Roll a new storm cell centred on the given world position, already heading
     * downwind so its first sync carries a usable direction.
     */
    public static StormCell create(int id, double x, double z, double windDirX, double windDirZ, Random random) {
        float radius = MIN_RADIUS + random.nextFloat() * (MAX_RADIUS - MIN_RADIUS);
        int lifetime = MIN_LIFETIME + random.nextInt(MAX_LIFETIME - MIN_LIFETIME);
        float speed = MIN_SPEED + random.nextFloat() * (MAX_SPEED - MIN_SPEED);
        double headingOffset = (random.nextDouble() - 0.5) * 2.0 * MAX_HEADING_OFFSET;
        double wobblePhase = random.nextDouble() * Math.PI * 2.0;

        StormCell cell = new StormCell(id, x, z, radius, lifetime, speed, headingOffset, wobblePhase);
        double heading = Math.atan2(windDirZ, windDirX) + headingOffset + Math.sin(wobblePhase) * WOBBLE_AMPLITUDE;
        cell.velX = Math.cos(heading) * speed;
        cell.velZ = Math.sin(heading) * speed;
        return cell;
    }

    /**
     * Advance the cell one tick along the prevailing wind.
     * The cell tracks slightly off-wind and wanders, so several cells born from
     * the same front still take visibly different paths.
     */
    public void tick(double windDirX, double windDirZ) {
        age++;

        double windAngle = Math.atan2(windDirZ, windDirX);
        double wobble = Math.sin(age * WOBBLE_SPEED + wobblePhase) * WOBBLE_AMPLITUDE;
        double heading = windAngle + headingOffset + wobble;

        velX = Math.cos(heading) * speed;
        velZ = Math.sin(heading) * speed;
        x += velX;
        z += velZ;

        intensity = computeIntensity();
    }

    /** Ramp up, hold, then fade out over the cell's life span. */
    private float computeIntensity() {
        float life = Math.min(1.0f, age / (float) lifetime);
        if (life < GROW_FRACTION) {
            return life / GROW_FRACTION;
        }
        if (life > 1.0f - DECAY_FRACTION) {
            return Math.max(0.0f, (1.0f - life) / DECAY_FRACTION);
        }
        return 1.0f;
    }

    public boolean isExpired() {
        return age >= lifetime;
    }

    /** Squared distance from the cell centre to a world position. */
    public double squaredDistanceTo(double worldX, double worldZ) {
        double dx = worldX - x;
        double dz = worldZ - z;
        return dx * dx + dz * dz;
    }

    /** True when the position sits inside the cell's precipitation core. */
    public boolean contains(double worldX, double worldZ) {
        return squaredDistanceTo(worldX, worldZ) <= radius * radius;
    }

    public int getId() {
        return id;
    }

    public double getX() {
        return x;
    }

    public double getZ() {
        return z;
    }

    public double getVelX() {
        return velX;
    }

    public double getVelZ() {
        return velZ;
    }

    public float getRadius() {
        return radius;
    }

    public float getIntensity() {
        return intensity;
    }

    public int getAge() {
        return age;
    }

    public int getLifetime() {
        return lifetime;
    }

    @Override
    public String toString() {
        return "StormCell[" + id + " @ " + (int) x + "," + (int) z
                + " r=" + (int) radius + " i=" + (int) (intensity * 100) + "%]";
    }
}
