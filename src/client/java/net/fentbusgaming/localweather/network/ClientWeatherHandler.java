package net.fentbusgaming.localweather.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fentbusgaming.localweather.LocalWeatherMod;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles incoming weather update packets on the client side.
 *
 * Stores weather data for multiple zones (current + neighbors) and blends
 * rain/thunder gradients based on the player's position relative to zone
 * boundaries. Also computes a "storm direction" vector so cloud/sky/fog
 * mixins can darken toward approaching storms.
 */
@Environment(EnvType.CLIENT)
public final class ClientWeatherHandler {

    /** Zone size in blocks (must match server). */
    private static final int ZONE_SIZE_BLOCKS = WeatherZoneManager.CHUNKS_PER_ZONE * 16; // 256

    /** How fast the client gradient chases its target per tick. */
    private static final float GRADIENT_SPEED = 0.03f;

    /** Zone data received from the server, keyed by packed(zoneX, zoneZ). */
    private static final Map<Long, ZoneState> ZONE_STATES = new ConcurrentHashMap<>();

    /** The blended weather type the mixin should use for precipitation type. */
    private static WeatherZone.WeatherType currentZoneWeather = WeatherZone.WeatherType.CLEAR;

    /** Smooth gradient targets computed from zone blending. */
    private static float targetRainGradient = 0f;
    private static float targetThunderGradient = 0f;

    /**
     * Normalized direction (XZ) pointing toward the nearest/strongest storm.
     * (0,0) if no storm nearby or if the player is inside the storm.
     */
    private static double stormDirX = 0;
    private static double stormDirZ = 0;

    /** Storm intensity in the storm direction (0 = no storm nearby, 1 = heavy). */
    private static float stormIntensity = 0f;

    /** Smoothed versions of the above for rendering. */
    private static double smoothStormDirX = 0;
    private static double smoothStormDirZ = 0;
    private static float smoothStormIntensity = 0f;

    private ClientWeatherHandler() {}

    // -------------------------------------------------------------------------
    // State per zone
    // -------------------------------------------------------------------------

    static final class ZoneState {
        WeatherZone.WeatherType weather;
        float transitionProgress;
        int zoneX, zoneZ;

        ZoneState(WeatherZone.WeatherType weather, float transitionProgress, int zoneX, int zoneZ) {
            this.weather = weather;
            this.transitionProgress = transitionProgress;
            this.zoneX = zoneX;
            this.zoneZ = zoneZ;
        }
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                WeatherPackets.WeatherUpdatePayload.ID,
                (payload, context) -> {
                    WeatherZone.WeatherType[] values = WeatherZone.WeatherType.values();
                    int ordinal = payload.weatherOrdinal();
                    if (ordinal < 0 || ordinal >= values.length) {
                        LocalWeatherMod.LOGGER.warn("[LocalWeather] Invalid weather ordinal: {}", ordinal);
                        return;
                    }
                    WeatherZone.WeatherType weather = values[ordinal];
                    float progress = payload.transitionProgress();
                    int zx = payload.zoneX();
                    int zz = payload.zoneZ();

                    long key = pack(zx, zz);
                    ZONE_STATES.put(key, new ZoneState(weather, progress, zx, zz));
                }
        );

        ClientTickEvents.END_CLIENT_TICK.register(ClientWeatherHandler::onClientTick);
        LocalWeatherMod.LOGGER.info("[LocalWeather] Client weather handler registered.");
    }

    // -------------------------------------------------------------------------
    // Per-tick smooth blending
    // -------------------------------------------------------------------------

    private static void onClientTick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world == null || client.player == null) return;

        double playerX = client.player.getX();
        double playerZ = client.player.getZ();

        int playerZoneX = floorDiv((int) Math.floor(playerX), ZONE_SIZE_BLOCKS);
        int playerZoneZ = floorDiv((int) Math.floor(playerZ), ZONE_SIZE_BLOCKS);

        float fracX = (float) ((playerX - (double) playerZoneX * ZONE_SIZE_BLOCKS) / ZONE_SIZE_BLOCKS);
        float fracZ = (float) ((playerZ - (double) playerZoneZ * ZONE_SIZE_BLOCKS) / ZONE_SIZE_BLOCKS);

        // Bilinear blend for rain/thunder gradients (immediate zone + neighbors)
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

        // Update current zone weather type for precipitation mixin
        ZoneState center = ZONE_STATES.get(pack(playerZoneX, playerZoneZ));
        currentZoneWeather = (center != null) ? center.weather : WeatherZone.WeatherType.CLEAR;

        // Compute storm direction: weighted average direction toward all nearby stormy zones
        computeStormDirection(playerX, playerZ, playerZoneX, playerZoneZ);

        // Smoothly chase gradients
        float currentRain = world.getRainGradient(1.0f);
        float currentThunder = world.getThunderGradient(1.0f);
        world.setRainGradient(smoothStep(currentRain, targetRainGradient, GRADIENT_SPEED));
        world.setThunderGradient(smoothStep(currentThunder, targetThunderGradient, GRADIENT_SPEED));
    }

    /**
     * Scan all known zones in a radius and compute a weighted direction
     * vector pointing toward storms. Closer/stronger storms have more weight.
     */
    private static void computeStormDirection(double playerX, double playerZ,
                                               int playerZoneX, int playerZoneZ) {
        double dirX = 0, dirZ = 0;
        float totalWeight = 0;
        float maxIntensity = 0;

        // Scan all known zones (server sends up to 5x5 = 25 zones)
        for (ZoneState state : ZONE_STATES.values()) {
            float rain = zoneRainLevelRaw(state);
            if (rain <= 0) continue;

            // Center of this zone in world coords
            double zoneCenterX = (state.zoneX + 0.5) * ZONE_SIZE_BLOCKS;
            double zoneCenterZ = (state.zoneZ + 0.5) * ZONE_SIZE_BLOCKS;

            double dx = zoneCenterX - playerX;
            double dz = zoneCenterZ - playerZ;
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (dist < 1.0) {
                // Player is inside this storm zone
                maxIntensity = Math.max(maxIntensity, rain);
                continue;
            }

            // Weight: stronger and closer storms pull harder.
            // Falls off with distance, maxes out at ~2 zones away.
            float weight = rain / (float) (1.0 + dist / ZONE_SIZE_BLOCKS);
            dirX += (dx / dist) * weight;
            dirZ += (dz / dist) * weight;
            totalWeight += weight;
            maxIntensity = Math.max(maxIntensity, rain * Math.max(0f, 1f - (float)(dist / (ZONE_SIZE_BLOCKS * 3.0))));
        }

        // Normalize direction
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len > 0.001 && totalWeight > 0) {
            stormDirX = dirX / len;
            stormDirZ = dirZ / len;
            stormIntensity = Math.min(1f, maxIntensity);
        } else {
            stormDirX = 0;
            stormDirZ = 0;
            stormIntensity = maxIntensity; // inside storm, no direction
        }

        // Smooth the direction and intensity
        smoothStormDirX += (stormDirX - smoothStormDirX) * 0.05;
        smoothStormDirZ += (stormDirZ - smoothStormDirZ) * 0.05;
        smoothStormIntensity += (stormIntensity - smoothStormIntensity) * 0.04f;
    }

    // -------------------------------------------------------------------------
    // Zone weather → gradient values
    // -------------------------------------------------------------------------

    private static float zoneRainLevelRaw(ZoneState state) {
        if (state == null) return 0f;
        boolean wet = state.weather == WeatherZone.WeatherType.RAIN
                || state.weather == WeatherZone.WeatherType.THUNDER
                || state.weather == WeatherZone.WeatherType.SNOW;
        return wet ? state.transitionProgress : 0f;
    }

    private static float zoneRainLevel(int zoneX, int zoneZ) {
        return zoneRainLevelRaw(ZONE_STATES.get(pack(zoneX, zoneZ)));
    }

    private static float zoneThunderLevel(int zoneX, int zoneZ) {
        ZoneState state = ZONE_STATES.get(pack(zoneX, zoneZ));
        if (state == null) return 0f;
        return (state.weather == WeatherZone.WeatherType.THUNDER) ? state.transitionProgress : 0f;
    }

    // -------------------------------------------------------------------------
    // Directional darkening for cloud/sky/fog mixins
    // -------------------------------------------------------------------------

    /**
     * Returns a darkening factor (0.0 = no darkening, up to ~0.6 = very dark)
     * for a given view direction, based on how much that direction faces toward
     * the approaching storm. Used by cloud/sky/fog mixins.
     *
     * @param viewDirX normalized X component of view direction (in world XZ plane)
     * @param viewDirZ normalized Z component of view direction
     */
    public static float getDirectionalDarkening(double viewDirX, double viewDirZ) {
        if (smoothStormIntensity < 0.01f) return 0f;

        // Dot product: how much the view direction faces the storm
        double dot = viewDirX * smoothStormDirX + viewDirZ * smoothStormDirZ;

        // Only darken when looking toward the storm (dot > 0)
        // Use a wide cone: (dot+0.3) so some darkening even at 90 degrees
        float facing = Math.max(0f, (float) (dot + 0.3) / 1.3f);

        return facing * smoothStormIntensity * 0.55f;
    }

    /**
     * Returns an overall storm proximity darkening factor (non-directional)
     * for when the player is near or inside a storm. Used for ambient sky tint.
     */
    public static float getStormProximityDarkening() {
        return smoothStormIntensity * 0.3f;
    }

    /**
     * Returns how dark the sky/clouds should be based on the actual rain the
     * player is experiencing (blended across zone boundaries). 0 = clear, 1 = full storm.
     * This drives the primary sky override — much stronger than proximity darkening.
     */
    public static float getRainDarkening() {
        return targetRainGradient;
    }

    /**
     * Returns the thunder level for extra-dark sky during thunderstorms.
     */
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
}
