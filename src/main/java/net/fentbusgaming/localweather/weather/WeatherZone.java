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
        SNOW
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
