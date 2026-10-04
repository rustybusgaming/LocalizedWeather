package net.fentbusgaming.localweather.network;

import net.fentbusgaming.localweather.weather.WeatherZone;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles incoming weather update packets on the client side.
 *
 * Stores weather data for multiple zones (current + neighbors) and blends
 * rain/thunder gradients based on the player's position relative to zone
 * boundaries. Also computes a "storm direction" vector so cloud/sky/fog
 * effects can darken toward approaching storms.
 */
public final class ClientWeatherHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("localweather");

    private static final int ZONE_SIZE_BLOCKS = WeatherZoneManager.CHUNKS_PER_ZONE * 16; // 256

    private static final float GRADIENT_SPEED = 0.03f;

    private static final Map<Long, ZoneState> ZONE_STATES = new ConcurrentHashMap<>();

    private static WeatherZone.WeatherType currentZoneWeather = WeatherZone.WeatherType.CLEAR;

    private static float targetRainGradient = 0f;
    private static float targetThunderGradient = 0f;

    private static double stormDirX = 0;
    private static double stormDirZ = 0;

    private static float stormIntensity = 0f;

    private static double smoothStormDirX = 0;
    private static double smoothStormDirZ = 0;
    private static float smoothStormIntensity = 0f;

    private static boolean hidingVanillaClouds = false;
    private static final float HIDE_CLOUDS_ABOVE = 0.85f;
    private static final float SHOW_CLOUDS_BELOW = 0.75f;

    private static double windDirX = 1.0;
    private static double windDirZ = 0.0;

    private static ClientLevel activeWorld;

    private ClientWeatherHandler() {}

    // -------------------------------------------------------------------------
    // State per zone
    // -------------------------------------------------------------------------

    public static final class ZoneState {
        public final WeatherZone.WeatherType currentWeather;
        public final WeatherZone.WeatherType targetWeather;
        public float transitionProgress;
        public final int zoneX, zoneZ;

        ZoneState(WeatherZone.WeatherType currentWeather, WeatherZone.WeatherType targetWeather,
                  float transitionProgress, int zoneX, int zoneZ) {
            this.currentWeather = currentWeather;
            this.targetWeather = targetWeather;
            this.transitionProgress = transitionProgress;
            this.zoneX = zoneX;
            this.zoneZ = zoneZ;
        }

        void advanceTransition() {
            if (currentWeather != targetWeather && transitionProgress < 1.0f) {
                transitionProgress = Math.min(1.0f, transitionProgress + WeatherZone.TRANSITION_SPEED);
            }
        }

        public float getWeatherIntensity(WeatherZone.WeatherType weather) {
            float currentIntensity = currentWeather == weather ? 1.0f : 0.0f;
            float targetIntensity = targetWeather == weather ? 1.0f : 0.0f;
            return currentIntensity + (targetIntensity - currentIntensity) * transitionProgress;
        }

        public float getRainIntensity() {
            return isWet(currentWeather) * (1.0f - transitionProgress)
                    + isWet(targetWeather) * transitionProgress;
        }

        public float getThunderIntensity() {
            return getWeatherIntensity(WeatherZone.WeatherType.THUNDER);
        }

        public WeatherZone.WeatherType getRenderableWeather() {
            if (targetWeather != WeatherZone.WeatherType.CLEAR
                    && (currentWeather == WeatherZone.WeatherType.CLEAR || transitionProgress >= 0.5f)) {
                return targetWeather;
            }
            return currentWeather != WeatherZone.WeatherType.CLEAR ? currentWeather : targetWeather;
        }
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    public static void handleWeatherUpdate(WeatherPayloads.WeatherUpdatePayload payload) {
        followWorld(Minecraft.getInstance().level);
        WeatherZone.WeatherType[] values = WeatherZone.WeatherType.values();
        int currentOrdinal = payload.currentWeatherOrdinal();
        int targetOrdinal = payload.targetWeatherOrdinal();
        if (currentOrdinal < 0 || currentOrdinal >= values.length
                || targetOrdinal < 0 || targetOrdinal >= values.length) {
            LOGGER.warn("[LocalWeather] Invalid weather update: {} -> {}", currentOrdinal, targetOrdinal);
            return;
        }
        WeatherZone.WeatherType currentWeather = values[currentOrdinal];
        WeatherZone.WeatherType targetWeather = values[targetOrdinal];
        float progress = Math.clamp(payload.transitionProgress(), 0.0f, 1.0f);
        int zx = payload.zoneX();
        int zz = payload.zoneZ();

        ZONE_STATES.put(pack(zx, zz), new ZoneState(currentWeather, targetWeather, progress, zx, zz));
    }

    public static void handleWindUpdate(WeatherPayloads.WindUpdatePayload payload) {
        windDirX = payload.windDirX();
        windDirZ = payload.windDirZ();
    }

    /**
     * Drop the zone cache when the player is in a different world from the
     * one it was filled for: a join, a dimension change, a respawn into a new
     * level. Called from the update handler as well as the tick, because the
     * packet that creates the new level and the first zone updates for it can
     * be handled in the same frame, before any tick runs. Clearing only from
     * the tick then threw those updates away, and an unchanged zone is not
     * sent again until the player crosses into another zone.
     */
    private static void followWorld(ClientLevel world) {
        if (world == activeWorld) return;
        activeWorld = world;
        ZONE_STATES.clear();
        hidingVanillaClouds = false;
        currentZoneWeather = WeatherZone.WeatherType.CLEAR;
        targetRainGradient = 0.0f;
        targetThunderGradient = 0.0f;
    }

    // -------------------------------------------------------------------------
    // Per-tick smooth blending
    // -------------------------------------------------------------------------

    public static void clientTick(Minecraft client) {
        ClientLevel world = client.level;
        followWorld(world);
        if (world == null || client.player == null) return;

        ZONE_STATES.values().forEach(ZoneState::advanceTransition);

        double playerX = client.player.getX();
        double playerZ = client.player.getZ();

        int playerZoneX = floorDiv((int) Math.floor(playerX), ZONE_SIZE_BLOCKS);
        int playerZoneZ = floorDiv((int) Math.floor(playerZ), ZONE_SIZE_BLOCKS);

        float fracX = (float) ((playerX - (double) playerZoneX * ZONE_SIZE_BLOCKS) / ZONE_SIZE_BLOCKS);
        float fracZ = (float) ((playerZ - (double) playerZoneZ * ZONE_SIZE_BLOCKS) / ZONE_SIZE_BLOCKS);

        int baseX = (fracX < 0.5f) ? playerZoneX - 1 : playerZoneX;
        int baseZ = (fracZ < 0.5f) ? playerZoneZ - 1 : playerZoneZ;
        float tx = (fracX < 0.5f) ? fracX + 0.5f : fracX - 0.5f;
        float tz = (fracZ < 0.5f) ? fracZ + 0.5f : fracZ - 0.5f;

        targetRainGradient = bilerp(
                zoneRainLevel(baseX, baseZ), zoneRainLevel(baseX + 1, baseZ),
                zoneRainLevel(baseX, baseZ + 1), zoneRainLevel(baseX + 1, baseZ + 1), tx, tz);
        targetThunderGradient = bilerp(
                zoneThunderLevel(baseX, baseZ), zoneThunderLevel(baseX + 1, baseZ),
                zoneThunderLevel(baseX, baseZ + 1), zoneThunderLevel(baseX + 1, baseZ + 1), tx, tz);

        float cellRain = ClientStormCellHandler.getCoreIntensityAt(playerX, playerZ);
        if (cellRain > 0.0f) {
            targetRainGradient = Math.max(targetRainGradient, cellRain);
            targetThunderGradient = Math.max(targetThunderGradient, cellRain);
        }

        ZoneState center = ZONE_STATES.get(pack(playerZoneX, playerZoneZ));
        currentZoneWeather = (center != null) ? center.getRenderableWeather() : WeatherZone.WeatherType.CLEAR;

        ZONE_STATES.values().removeIf(state -> Math.abs(state.zoneX - playerZoneX) > 3
            || Math.abs(state.zoneZ - playerZoneZ) > 3);

        computeStormDirection(playerX, playerZ, playerZoneX, playerZoneZ);

        if (hidingVanillaClouds) {
            if (targetRainGradient < SHOW_CLOUDS_BELOW) hidingVanillaClouds = false;
        } else if (targetRainGradient > HIDE_CLOUDS_ABOVE) {
            hidingVanillaClouds = true;
        }

        float currentRain = world.getRainLevel(1.0f);
        float currentThunder = world.getThunderLevel(1.0f);
        world.setRainLevel(smoothStep(currentRain, targetRainGradient, GRADIENT_SPEED));
        world.setThunderLevel(smoothStep(currentThunder, targetThunderGradient, GRADIENT_SPEED));
    }

    private static void computeStormDirection(double playerX, double playerZ,
                                               int playerZoneX, int playerZoneZ) {
        double dirX = 0, dirZ = 0;
        float totalWeight = 0;
        float maxIntensity = 0;

        for (ZoneState state : ZONE_STATES.values()) {
            float rain = zoneRainLevelRaw(state);
            if (rain <= 0) continue;

            double zoneCenterX = (state.zoneX + 0.5) * ZONE_SIZE_BLOCKS;
            double zoneCenterZ = (state.zoneZ + 0.5) * ZONE_SIZE_BLOCKS;

            double dx = zoneCenterX - playerX;
            double dz = zoneCenterZ - playerZ;
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (dist < 1.0) {
                maxIntensity = Math.max(maxIntensity, rain);
                continue;
            }

            float weight = rain / (float) (1.0 + dist / ZONE_SIZE_BLOCKS);
            dirX += (dx / dist) * weight;
            dirZ += (dz / dist) * weight;
            totalWeight += weight;
            maxIntensity = Math.max(maxIntensity, rain * Math.max(0f, 1f - (float)(dist / (ZONE_SIZE_BLOCKS * 3.0))));
        }

        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len > 0.001 && totalWeight > 0) {
            stormDirX = dirX / len;
            stormDirZ = dirZ / len;
            stormIntensity = Math.min(1f, maxIntensity);
        } else {
            stormDirX = 0;
            stormDirZ = 0;
            stormIntensity = maxIntensity;
        }

        smoothStormDirX += (stormDirX - smoothStormDirX) * 0.05;
        smoothStormDirZ += (stormDirZ - smoothStormDirZ) * 0.05;
        smoothStormIntensity += (stormIntensity - smoothStormIntensity) * 0.04f;
    }

    // -------------------------------------------------------------------------
    // Zone weather → gradient values
    // -------------------------------------------------------------------------

    private static float zoneRainLevelRaw(ZoneState state) {
        return state != null ? state.getRainIntensity() : 0.0f;
    }

    private static float zoneRainLevel(int zoneX, int zoneZ) {
        return zoneRainLevelRaw(ZONE_STATES.get(pack(zoneX, zoneZ)));
    }

    private static float zoneThunderLevel(int zoneX, int zoneZ) {
        ZoneState state = ZONE_STATES.get(pack(zoneX, zoneZ));
        return state != null ? state.getThunderIntensity() : 0.0f;
    }

    private static float isWet(WeatherZone.WeatherType weather) {
        return weather == WeatherZone.WeatherType.RAIN
                || weather == WeatherZone.WeatherType.THUNDER
                || weather == WeatherZone.WeatherType.SNOW
                || weather == WeatherZone.WeatherType.HAIL ? 1.0f : 0.0f;
    }

    // -------------------------------------------------------------------------
    // Directional darkening for cloud/sky/fog
    // -------------------------------------------------------------------------

    public static float getDirectionalDarkening(double viewDirX, double viewDirZ) {
        if (smoothStormIntensity < 0.01f) return 0f;

        double dot = viewDirX * smoothStormDirX + viewDirZ * smoothStormDirZ;
        float facing = Math.max(0f, (float) (dot + 0.3) / 1.3f);

        return facing * smoothStormIntensity * 0.55f;
    }

    public static float getStormProximityDarkening() {
        return smoothStormIntensity * 0.3f;
    }

    public static float getRainDarkening() {
        return targetRainGradient;
    }

    public static float getThunderDarkening() {
        return targetThunderGradient;
    }

    // -------------------------------------------------------------------------
    // Math helpers
    // -------------------------------------------------------------------------

    private static float bilerp(float v00, float v10, float v01, float v11, float tx, float tz) {
        float top    = v00 + (v10 - v00) * tx;
        float bottom = v01 + (v11 - v01) * tx;
        return top + (bottom - top) * tz;
    }

    private static float smoothStep(float current, float target, float speed) {
        float diff = target - current;
        if (Math.abs(diff) < speed) return target;
        return current + Math.signum(diff) * speed;
    }

    private static int floorDiv(int a, int b) {
        return Math.floorDiv(a, b);
    }

    static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    // -------------------------------------------------------------------------
    // Public accessors
    // -------------------------------------------------------------------------

    public static Map<Long, ZoneState> getZoneStates() {
        return ZONE_STATES;
    }

    public static WeatherZone.WeatherType getCurrentZoneWeather() {
        return currentZoneWeather;
    }

    public static float getTargetRainGradient() {
        return targetRainGradient;
    }

    public static float getTargetThunderGradient() {
        return targetThunderGradient;
    }

    public static double getSmoothedStormDirX() {
        return smoothStormDirX;
    }

    public static double getSmoothedStormDirZ() {
        return smoothStormDirZ;
    }

    public static float getSmoothedStormIntensity() {
        return smoothStormIntensity;
    }

    public static boolean isHidingVanillaClouds() {
        return hidingVanillaClouds;
    }

    public static double getWindDirX() {
        return windDirX;
    }

    public static double getWindDirZ() {
        return windDirZ;
    }
}
