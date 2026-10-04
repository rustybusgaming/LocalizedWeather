package net.fentbusgaming.localweather.weather;

/**
 * Represents the weather state of a single localized zone.
 * A zone covers a 16x16 chunk region (256x256 blocks).
 */
public class WeatherZone {

    public enum WeatherType {
        CLEAR,
        RAIN,
        THUNDER,
        SNOW,
        HAIL
    }

    /** Zone grid coordinates (each unit = 16 chunks = 256 blocks). */
    private final int zoneX;
    private final int zoneZ;

    /** Current active weather in this zone. */
    private WeatherType currentWeather;

    /** Target weather (what we are transitioning toward). */
    private WeatherType targetWeather;

    /**
     * Transition progress: 0.0 = fully currentWeather, 1.0 = fully targetWeather.
     * Advances by TRANSITION_SPEED per tick until it reaches 1.0.
     */
    private float transitionProgress;

    /** Ticks remaining before this weather changes naturally. */
    private int weatherDuration;

    /**
     * Game time at which every player left this zone's range, or -1 while
     * someone is near it. A dormant zone is kept rather than thrown away, and
     * is not ticked; {@link #catchUp(long)} settles the time it missed when
     * someone comes back. See {@link ZoneRetention}.
     */
    private long dormantSince = -1;

    /** Ticks for the transition animation (20 ticks = 1 second). */
    public static final int TRANSITION_TICKS = 400; // 20 seconds

    /** Progress step per tick: 1 / TRANSITION_TICKS. */
    public static final float TRANSITION_SPEED = 1.0f / TRANSITION_TICKS;

    public WeatherZone(int zoneX, int zoneZ, WeatherType initial, int duration) {
        this.zoneX = zoneX;
        this.zoneZ = zoneZ;
        this.currentWeather = initial;
        this.targetWeather = initial;
        this.transitionProgress = 1.0f;
        this.weatherDuration = duration;
    }

    /**
     * Rebuild a zone exactly as it was saved. Package-private: only
     * {@link ZoneStore} needs to put a zone back mid-transition or dormant.
     */
    static WeatherZone restore(int zoneX, int zoneZ, WeatherType current, WeatherType target,
                               float progress, int duration, long dormantSince) {
        WeatherZone zone = new WeatherZone(zoneX, zoneZ, current, duration);
        zone.targetWeather = target;
        zone.transitionProgress = Math.max(0.0f, Math.min(1.0f, progress));
        if (zone.transitionProgress >= 1.0f) {
            zone.currentWeather = target;
        }
        zone.dormantSince = dormantSince;
        return zone;
    }

    /**
     * Begin a transition to a new weather type.
     * If we are mid-transition, snap the current to the target first.
     */
    public void setTargetWeather(WeatherType next) {
        if (next == targetWeather) return;
        // Snap previous transition
        if (transitionProgress < 1.0f) {
            currentWeather = targetWeather;
        }
        targetWeather = next;
        transitionProgress = 0.0f;
    }

    /**
     * Tick the transition. Returns true if the transition just completed.
     */
    public boolean tickTransition() {
        if (transitionProgress >= 1.0f) return false;
        transitionProgress = Math.min(1.0f, transitionProgress + TRANSITION_SPEED);
        if (transitionProgress >= 1.0f) {
            currentWeather = targetWeather;
            return true;
        }
        return false;
    }

    /** Decrement weather duration. Returns true if weather expired. */
    public boolean tickDuration() {
        if (weatherDuration > 0) {
            weatherDuration--;
            return weatherDuration == 0;
        }
        return false;
    }

    public WeatherType getCurrentWeather() {
        return currentWeather;
    }

    public WeatherType getTargetWeather() {
        return targetWeather;
    }

    public float getTransitionProgress() {
        return transitionProgress;
    }

    public int getZoneX() {
        return zoneX;
    }

    public int getZoneZ() {
        return zoneZ;
    }

    public int getWeatherDuration() {
        return weatherDuration;
    }

    public void setWeatherDuration(int ticks) {
        this.weatherDuration = ticks;
    }

    /**
     * Advance this zone by the time it spent with nobody near it, in one step.
     *
     * A transition that was in flight finishes, and the elapsed time is spent
     * against the remaining duration. If the duration ran out while the zone
     * was dormant it is left at one tick rather than zero, so the caller's
     * ordinary {@link #tickDuration()} is what reports the expiry — that path
     * already picks the next weather from the zone's biomes and its upwind
     * neighbour, which this class cannot see.
     *
     * Only one turnover is applied however long the zone sat empty. Weather
     * picks do not depend on how many came before, so a zone left for a day
     * and a zone left for an hour both come back to a fresh draw, which is
     * what several turnovers in a row would have produced anyway.
     */
    public void catchUp(long elapsedTicks) {
        if (elapsedTicks <= 0) return;

        if (transitionProgress < 1.0f) {
            float advanced = transitionProgress + elapsedTicks * TRANSITION_SPEED;
            if (advanced >= 1.0f) {
                transitionProgress = 1.0f;
                currentWeather = targetWeather;
            } else {
                transitionProgress = advanced;
            }
        }

        if (weatherDuration > 0) {
            long left = weatherDuration - elapsedTicks;
            weatherDuration = (int) Math.max(1L, left);
        }
    }

    public boolean isDormant() {
        return dormantSince >= 0;
    }

    public long getDormantSince() {
        return dormantSince;
    }

    public void markDormant(long gameTime) {
        dormantSince = gameTime;
    }

    public void wake() {
        dormantSince = -1;
    }

    /**
     * Instantly replace the zone weather and restart its duration timer.
     */
    public void forceWeather(WeatherType weather, int duration) {
        currentWeather = weather;
        targetWeather = weather;
        transitionProgress = 1.0f;
        weatherDuration = duration;
    }

    /**
     * For network sync: the "effective" weather type a client should render.
     * During a transition the target is sent so the client starts transitioning.
     */
    public WeatherType getEffectiveWeather() {
        return targetWeather;
    }

    @Override
    public String toString() {
        return "WeatherZone[" + zoneX + "," + zoneZ + " " + currentWeather
                + "->" + targetWeather + " (" + (int)(transitionProgress * 100) + "%)]";
    }
}
