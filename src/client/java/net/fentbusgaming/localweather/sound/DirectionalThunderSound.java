package net.fentbusgaming.localweather.sound;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;

import java.util.Random;

/**
 * Plays thunder sounds from the direction of nearby thunder zones
 * rather than using vanilla's overhead-only thunder.
 */
@Environment(EnvType.CLIENT)
public class DirectionalThunderSound {

    private static final int ZONE_SIZE = WeatherZoneManager.CHUNKS_PER_ZONE * 16;
    private static final Random RANDOM = new Random();

    /** Min ticks between thunder sound attempts. */
    private static final int MIN_INTERVAL = 80;
    /** Max ticks between thunder sound attempts. */
    private static final int MAX_INTERVAL = 300;

    /** Max distance to play thunder from (blocks). */
    private static final float MAX_SOUND_DIST = ZONE_SIZE * 3f;

    private static int ticksUntilNext = MIN_INTERVAL + RANDOM.nextInt(MAX_INTERVAL - MIN_INTERVAL);

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(DirectionalThunderSound::onTick);
    }

    private static void onTick(Minecraft client) {
        if (client.level == null || client.player == null) return;
        if (client.isPaused()) return;

        if (--ticksUntilNext > 0) return;
        ticksUntilNext = MIN_INTERVAL + RANDOM.nextInt(MAX_INTERVAL - MIN_INTERVAL);

        double playerX = client.player.getX();
        double playerY = client.player.getY();
        double playerZ = client.player.getZ();

        // Find the strongest/closest thunder zone
        double bestX = 0, bestZ = 0;
        float bestScore = 0;
        boolean found = false;

        for (ClientWeatherHandler.ZoneState state : ClientWeatherHandler.getZoneStates().values()) {
            float thunderIntensity = state.getWeatherIntensity(WeatherZone.WeatherType.THUNDER);
            if (thunderIntensity < 0.3f) continue;

            double zoneCX = (state.zoneX + 0.5) * ZONE_SIZE;
            double zoneCZ = (state.zoneZ + 0.5) * ZONE_SIZE;
            double dx = zoneCX - playerX;
            double dz = zoneCZ - playerZ;
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (dist > MAX_SOUND_DIST) continue;

            // Score: closer and more intense = higher
            float score = thunderIntensity / (1f + (float) dist / ZONE_SIZE);
            if (score > bestScore) {
                bestScore = score;
                bestX = zoneCX;
                bestZ = zoneCZ;
                found = true;
            }
        }

        if (!found) return;

        // Random chance proportional to intensity — don't thunder every interval
        if (RANDOM.nextFloat() > bestScore * 2f) return;

        double dx = bestX - playerX;
        double dz = bestZ - playerZ;
        double dist = Math.sqrt(dx * dx + dz * dz);

        // Place the sound source in the direction of the storm, capped at 80 blocks
        // so it's audible but clearly directional
        double soundDist = Math.min(dist, 80.0);
        double soundX, soundZ;
        if (dist > 1.0) {
            soundX = playerX + (dx / dist) * soundDist + (RANDOM.nextDouble() - 0.5) * 40;
            soundZ = playerZ + (dz / dist) * soundDist + (RANDOM.nextDouble() - 0.5) * 40;
        } else {
            // Player is inside thunder zone — play overhead
            soundX = playerX + (RANDOM.nextDouble() - 0.5) * 60;
            soundZ = playerZ + (RANDOM.nextDouble() - 0.5) * 60;
        }
        double soundY = playerY + 30 + RANDOM.nextDouble() * 40;

        // Volume scales with proximity (louder when close)
        float volume = 0.5f + bestScore * 8f;
        volume = Math.min(volume, 10f);
        float pitch = 0.8f + RANDOM.nextFloat() * 0.4f;

        ClientLevel world = client.level;
        world.playLocalSound(soundX, soundY, soundZ,
                SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.WEATHER, volume, pitch, false);
    }
}
