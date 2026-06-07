package net.fentbusgaming.localweather.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Map;

/**
 * Renders hail particles (ice crystals) during hail weather.
 * Uses deterministic hash-based spawning so particles are consistent across frames.
 */
@Environment(EnvType.CLIENT)
public class HailParticleRenderer {

    private static final int ZONE_SIZE = WeatherZoneManager.CHUNKS_PER_ZONE * 16;
    private static final int PARTICLE_GRID_SIZE = 48;
    private static final float PARTICLE_SIZE = 0.12f;
    private static final float PARTICLE_FALL_SPEED = 1.1f;
    private static final float PARTICLE_LENGTH = 0.55f;
    private static final float PARTICLE_DRIFT = 0.06f;
    private static final float PARTICLE_SHIMMER = 0.05f;
    private static final float MAX_DIST = ZONE_SIZE * 3.5f;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(HailParticleRenderer::render);
    }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        Map<Long, ClientWeatherHandler.ZoneState> zones = ClientWeatherHandler.getZoneStates();
        boolean anyHail = zones.values().stream()
                .anyMatch(z -> z.weather == WeatherZone.WeatherType.HAIL && z.transitionProgress > 0.1f);
        if (!anyHail) return;

        Vec3d cam = client.gameRenderer.getCamera().getCameraPos();
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        matrices.push();
        Matrix4f mat = matrices.peek().getPositionMatrix();
        VertexConsumer buffer = consumers.getBuffer(RenderLayers.debugQuads());

        for (ClientWeatherHandler.ZoneState zone : zones.values()) {
            if (zone.weather != WeatherZone.WeatherType.HAIL || zone.transitionProgress < 0.1f) continue;

            renderHailInZone(mat, buffer, zone, cam, client.world.getTime());
        }

        matrices.pop();
    }

    private static void renderHailInZone(Matrix4f mat, VertexConsumer buffer, ClientWeatherHandler.ZoneState zone,
                                          Vec3d cam, long worldTime) {
        float zoneX = zone.zoneX * ZONE_SIZE;
        float zoneZ = zone.zoneZ * ZONE_SIZE;
        float intensity = zone.transitionProgress;

        for (int px = 0; px < PARTICLE_GRID_SIZE; px++) {
            for (int pz = 0; pz < PARTICLE_GRID_SIZE; pz++) {
                long seed = ((long) zone.zoneX << 32) | (zone.zoneZ & 0xFFFFFFFFL);
                int hash = hashParticle(px, pz, (int) seed);

                float particleX = zoneX + (px / (float) PARTICLE_GRID_SIZE) * ZONE_SIZE;
                float particleZ = zoneZ + (pz / (float) PARTICLE_GRID_SIZE) * ZONE_SIZE;
                float baseY = 190 + (hash & 31);

                float dx = (float) (particleX - cam.x);
                float dz = (float) (particleZ - cam.z);
                double distSq = dx * dx + dz * dz;
                if (distSq > MAX_DIST * MAX_DIST) continue;

                float fallPhase = (worldTime * PARTICLE_FALL_SPEED + hash * 0.001f) % 80f;
                float yOffset = fallPhase * (0.8f + ((hash >> 8) & 7) * 0.04f);
                float y = (float) (baseY - yOffset - cam.y);

                float drift = (float) Math.sin(worldTime * 0.025f + hash * 0.0015f) * PARTICLE_DRIFT;
                float shimmer = (float) Math.sin(worldTime * 0.05f + hash * 0.001f) * PARTICLE_SHIMMER;
                float xShimmer = (float) Math.cos(worldTime * 0.04f + hash * 0.002f) * PARTICLE_SHIMMER;

                float x1 = dx - PARTICLE_SIZE + xShimmer;
                float z1 = dz - PARTICLE_SIZE + drift;
                float x2 = dx + PARTICLE_SIZE + xShimmer;
                float z2 = dz + PARTICLE_SIZE + drift;
                float yBot = y;
                float yTop = y + PARTICLE_LENGTH + shimmer;

                int alpha = (int) (170 * intensity);
                int bright = 210 + (hash & 31);

                buffer.vertex(mat, x1, yBot, z1).color(bright, bright, 255, alpha);
                buffer.vertex(mat, x1, yTop, z1).color(bright, bright, 255, alpha);
                buffer.vertex(mat, x2, yTop, z1).color(bright, bright, 255, alpha);
                buffer.vertex(mat, x2, yBot, z1).color(bright, bright, 255, alpha);
            }
        }
    }

    private static int hashParticle(int x, int z, int seed) {
        int h = x * 73856093 ^ z * 19349663 ^ seed * 83492791;
        h = (h ^ (h >> 13)) * 0x9e3779b9;
        h = h ^ (h >> 16);
        return h;
    }
}
