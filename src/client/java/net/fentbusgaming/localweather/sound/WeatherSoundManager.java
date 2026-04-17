package net.fentbusgaming.localweather.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.Random;

/**
 * Manages directional ambient weather sounds to provide stereo separation.
 *
 * <p>Rain ambient sounds are positioned toward the nearest rain/storm zones,
 * creating a natural stereo effect where approaching storms can be heard
 * from their direction before they arrive. When the player is inside a
 * rain zone, sounds surround the player evenly.</p>
 *
 * <p>Also plays distant storm rumbles from far-away thunder zones at low
 * pitch and volume, letting players hear approaching thunderstorms.</p>
 *
 * <p>Works alongside {@link DirectionalThunderSound} which handles close
 * thunder cracks.</p>
 */
@Environment(EnvType.CLIENT)
public class WeatherSoundManager {

    private static final int ZONE_SIZE = WeatherZoneManager.CHUNKS_PER_ZONE * 16;
    private static final Random RANDOM = new Random();

    // --- Rain ambient ---

    /** Ticks between rain ambient sound checks. */
    private static final int RAIN_INTERVAL = 5;
    /** Max block distance for rain ambient sounds. */
    private static final float MAX_RAIN_DIST = ZONE_SIZE * 2f;
    /** Max sounds to play per rain tick to prevent audio overload. */
    private static final int MAX_RAIN_SOUNDS_PER_TICK = 3;

    private static int rainTicks = 0;

    // --- Distant storm ambient ---

    /** Min ticks between distant storm rumble attempts. */
    private static final int DISTANT_STORM_MIN_INTERVAL = 140;
    /** Max ticks between distant storm rumble attempts. */
    private static final int DISTANT_STORM_MAX_INTERVAL = 450;
    /** Min zone distance (blocks) for a storm to count as "distant". */
    private static final float MIN_DISTANT_DIST = ZONE_SIZE * 0.8f;
    /** Max zone distance (blocks) for distant storm sounds. */
    private static final float MAX_DISTANT_DIST = ZONE_SIZE * 3.5f;

    private static int distantStormTicks = DISTANT_STORM_MIN_INTERVAL
            + RANDOM.nextInt(DISTANT_STORM_MAX_INTERVAL - DISTANT_STORM_MIN_INTERVAL);

    private WeatherSoundManager() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(WeatherSoundManager::onTick);
    }

    private static void onTick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        if (client.isPaused()) return;

        tickRainAmbient(client);
        tickDistantStorm(client);
    }

    // -------------------------------------------------------------------------
    // Directional rain ambient
    // -------------------------------------------------------------------------

    /**
     * Plays rain sounds positioned toward nearby rain/thunder zones.
     *
     * <ul>
     *   <li>Inside a rain zone: sounds play from random directions around the
     *       player (surround).</li>
     *   <li>Rain zone nearby: sounds are positioned in the direction of the
     *       zone, creating clear left/right stereo separation.</li>
     * </ul>
     */
    private static void tickRainAmbient(MinecraftClient client) {
        if (++rainTicks < RAIN_INTERVAL) return;
        rainTicks = 0;

        double playerX = client.player.getX();
        double playerY = client.player.getY();
        double playerZ = client.player.getZ();
        ClientWorld world = client.world;

        int soundsPlayed = 0;

        for (ClientWeatherHandler.ZoneState state : ClientWeatherHandler.getZoneStates().values()) {
            if (soundsPlayed >= MAX_RAIN_SOUNDS_PER_TICK) break;
            if (state.weather == WeatherZone.WeatherType.CLEAR) continue;
            if (state.weather == WeatherZone.WeatherType.SNOW) continue;
            if (state.transitionProgress < 0.1f) continue;

            double zoneCX = (state.zoneX + 0.5) * ZONE_SIZE;
            double zoneCZ = (state.zoneZ + 0.5) * ZONE_SIZE;
            double dx = zoneCX - playerX;
            double dz = zoneCZ - playerZ;
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (dist > MAX_RAIN_DIST) continue;

            double soundX, soundZ, soundY;
            float volume;

            if (dist < ZONE_SIZE * 0.4) {
                // --- Player is inside this zone: surround sound ---
                double angle = RANDOM.nextDouble() * Math.PI * 2;
                double radius = 5 + RANDOM.nextDouble() * 15;
                soundX = playerX + Math.cos(angle) * radius;
                soundZ = playerZ + Math.sin(angle) * radius;
                soundY = playerY + RANDOM.nextDouble() * 8;
                volume = 0.15f + state.transitionProgress * 0.25f;
            } else {
                // --- Zone is to one side: directional stereo ---
                double dirX = dx / dist;
                double dirZ = dz / dist;
                double soundDist = Math.min(dist * 0.3, 25.0);

                // Add perpendicular spread so it's not a single point source
                double perpX = -dirZ;
                double perpZ = dirX;
                double spread = (RANDOM.nextDouble() - 0.5) * soundDist * 0.4;

                soundX = playerX + dirX * soundDist + perpX * spread;
                soundZ = playerZ + dirZ * soundDist + perpZ * spread;
                soundY = playerY + 5 + RANDOM.nextDouble() * 10;

                float distFactor = 1.0f - (float) (dist / MAX_RAIN_DIST);
                volume = (0.05f + distFactor * 0.2f) * state.transitionProgress;
            }

            if (volume < 0.02f) continue;

            // Random chance to thin out sounds for a natural feel
            if (RANDOM.nextFloat() > 0.35f + volume) continue;

            float pitch = 0.85f + RANDOM.nextFloat() * 0.25f;

            world.playSoundClient(soundX, soundY, soundZ,
                    SoundEvents.WEATHER_RAIN, SoundCategory.WEATHER,
                    volume, pitch, false);
            soundsPlayed++;
        }
    }

    // -------------------------------------------------------------------------
    // Distant storm rumbles
    // -------------------------------------------------------------------------

    /**
     * Occasionally plays a low-pitched, quiet thunder rumble from the direction
     * of a far-away thunder zone. This lets players hear distant storms
     * approaching with clear directional audio.
     *
     * <p>Only considers thunder zones beyond {@link #MIN_DISTANT_DIST} so it
     * does not overlap with {@link DirectionalThunderSound}'s close thunder.</p>
     */
    private static void tickDistantStorm(MinecraftClient client) {
        if (--distantStormTicks > 0) return;
        distantStormTicks = DISTANT_STORM_MIN_INTERVAL
                + RANDOM.nextInt(DISTANT_STORM_MAX_INTERVAL - DISTANT_STORM_MIN_INTERVAL);

        double playerX = client.player.getX();
        double playerY = client.player.getY();
        double playerZ = client.player.getZ();

        double bestDirX = 0, bestDirZ = 0;
        float bestScore = 0;
        double bestDist = 0;
        boolean found = false;

        for (ClientWeatherHandler.ZoneState state : ClientWeatherHandler.getZoneStates().values()) {
            if (state.weather != WeatherZone.WeatherType.THUNDER) continue;
            if (state.transitionProgress < 0.2f) continue;

            double zoneCX = (state.zoneX + 0.5) * ZONE_SIZE;
            double zoneCZ = (state.zoneZ + 0.5) * ZONE_SIZE;
            double dx = zoneCX - playerX;
            double dz = zoneCZ - playerZ;
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (dist < MIN_DISTANT_DIST || dist > MAX_DISTANT_DIST) continue;

            float score = state.transitionProgress / (1f + (float) dist / ZONE_SIZE);
            if (score > bestScore) {
                bestScore = score;
                bestDist = dist;
                bestDirX = dx / dist;
                bestDirZ = dz / dist;
                found = true;
            }
        }

        if (!found) return;

        // Random chance proportional to score — not every interval produces sound
        if (RANDOM.nextFloat() > bestScore * 3f) return;

        // Place the rumble in the storm direction, capped at 60 blocks
        double soundDist = Math.min(bestDist, 60.0);
        double soundX = playerX + bestDirX * soundDist + (RANDOM.nextDouble() - 0.5) * 30;
        double soundZ = playerZ + bestDirZ * soundDist + (RANDOM.nextDouble() - 0.5) * 30;
        double soundY = playerY + 40 + RANDOM.nextDouble() * 30;

        // Quiet and low-pitched for a distant rumble feel
        float distFactor = (float) (bestDist / MAX_DISTANT_DIST);
        float volume = (0.15f + bestScore * 2f) * (1f - distFactor * 0.5f);
        volume = Math.min(volume, 3f);
        float pitch = 0.5f + RANDOM.nextFloat() * 0.25f;

        ClientWorld world = client.world;
        world.playSoundClient(soundX, soundY, soundZ,
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.WEATHER, volume, pitch, false);
    }
}
