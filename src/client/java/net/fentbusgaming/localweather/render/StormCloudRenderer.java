package net.fentbusgaming.localweather.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Map;

/**
 * Renders a localized storm tint over vanilla clouds. The overlay uses the
 * same 12-block pixel scale as Minecraft's cloud texture, while vanilla keeps
 * ownership of the global cloud renderer for maximum renderer compatibility.
 */
@Environment(EnvType.CLIENT)
public class StormCloudRenderer {

    private static final int ZONE_SIZE = WeatherZoneManager.CHUNKS_PER_ZONE * 16;

    /** Size of each cloud "pixel" in blocks. */
    private static final int CELL_SIZE = 12;
    /** Vertical thickness of cloud boxes in blocks. */
    private static final float CLOUD_THICKNESS = 4.0f;
    /** Base height of the cloud layer bottom. */
    private static final float CLOUD_BASE = 191.0f;
    /** How far away clouds are visible (in blocks). */
    private static final float MAX_DIST = ZONE_SIZE * 4.5f;
    /** Clouds drift speed (blocks per tick). */
    private static final float DRIFT_SPEED = 0.03f;

    private static final int CELLS_PER_ZONE = (int) Math.ceil(ZONE_SIZE / (float) CELL_SIZE);

    private static final float COVERAGE_RAIN = 0.68f;
    private static final float COVERAGE_THUNDER = 0.80f;
    private static final float COVERAGE_SNOW = 0.62f;
    private static final float COVERAGE_HAIL = 0.72f;

    private static final float ALPHA_RAIN = 0.48f;
    private static final float ALPHA_THUNDER = 0.66f;
    private static final float ALPHA_SNOW = 0.42f;
    private static final float ALPHA_HAIL = 0.56f;

    private static final int CLOUD_LAYERS = 1;
    private static final float[] CLOUD_LAYER_HEIGHT = {0f};
    private static final float[] CLOUD_LAYER_COVERAGE_ADJUST = {0f};
    private static final float[] CLOUD_LAYER_ALPHA_SCALE = {1f};
    private static final float[] CLOUD_LAYER_WIND_SCALE = {1.0f};

    private static final RenderLayer CLOUD_RENDER_LAYER = RenderLayers.translucentMovingBlock();

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(StormCloudRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        Camera camera = client.gameRenderer.getCamera();
        Vec3d cam = camera.getCameraPos();
        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        float tickDelta = client.getRenderTickCounter().getTickProgress(false);
        renderInternal(client, context.matrices(), tickDelta, cam, consumers);
    }

    private static void renderInternal(MinecraftClient client, MatrixStack matrices, float tickDelta, Vec3d cam, VertexConsumerProvider consumers) {
        Map<Long, ClientWeatherHandler.ZoneState> zones = ClientWeatherHandler.getZoneStates();
        if (zones.isEmpty()) return;

        long worldTime = client.world != null ? client.world.getTime() : 0;
        float driftMag = (worldTime + tickDelta) * DRIFT_SPEED;
        Vec3d windDir = normalizedWind(ClientWeatherHandler.getWindDirX(), ClientWeatherHandler.getWindDirZ());
        float driftX = (float) (driftMag * windDir.x);
        float driftZ = (float) (driftMag * windDir.z);

        boolean anyVisible = false;
        for (ClientWeatherHandler.ZoneState s : zones.values()) {
            WeatherZone.WeatherType weather = s.getRenderableWeather();
            if (weather == WeatherZone.WeatherType.CLEAR || s.getWeatherIntensity(weather) < 0.05f) continue;
            double dx = (s.zoneX + 0.5) * ZONE_SIZE - cam.x;
            double dz = (s.zoneZ + 0.5) * ZONE_SIZE - cam.z;
            if (dx * dx + dz * dz < MAX_DIST * MAX_DIST) {
                anyVisible = true;
                break;
            }
        }
        if (!anyVisible) return;

        matrices.push();
        Matrix4f mat = matrices.peek().getPositionMatrix();
        VertexConsumer buffer = consumers.getBuffer(CLOUD_RENDER_LAYER);

        for (ClientWeatherHandler.ZoneState s : zones.values()) {
            WeatherZone.WeatherType weather = s.getRenderableWeather();
            float weatherIntensity = s.getWeatherIntensity(weather);
            if (weather == WeatherZone.WeatherType.CLEAR || weatherIntensity < 0.05f) continue;

            double zoneCX = (s.zoneX + 0.5) * ZONE_SIZE;
            double zoneCZ = (s.zoneZ + 0.5) * ZONE_SIZE;
            double zDistX = zoneCX - cam.x;
            double zDistZ = zoneCZ - cam.z;
            double zoneDist = Math.sqrt(zDistX * zDistX + zDistZ * zDistZ);
            if (zoneDist > MAX_DIST + ZONE_SIZE) continue;

            WeatherRenderConfig config = getWeatherConfig(weather);
            float thunderPulse = getThunderPulse(worldTime, tickDelta, weatherIntensity, weather);
            float weatherHeightOffset = switch (weather) {
                case THUNDER -> -10f;
                case SNOW -> 10f;
                case HAIL -> -4f;
                default -> 0f;
            };

            float zoneWorldX = s.zoneX * ZONE_SIZE;
            float zoneWorldZ = s.zoneZ * ZONE_SIZE;

            for (int cx = 0; cx < CELLS_PER_ZONE; cx++) {
                for (int cz = 0; cz < CELLS_PER_ZONE; cz++) {
                    int worldCellX = s.zoneX * CELLS_PER_ZONE + cx;
                    int worldCellZ = s.zoneZ * CELLS_PER_ZONE + cz;

                    if (cellNoise(worldCellX, worldCellZ, 0) > config.coverage) continue;

                    double cdx = zoneWorldX + cx * CELL_SIZE - cam.x;
                    double cdz = zoneWorldZ + cz * CELL_SIZE - cam.z;
                    double cellDist = Math.sqrt(cdx * cdx + cdz * cdz);
                    if (cellDist > MAX_DIST) continue;

                    float distFade = cellDist < MAX_DIST * 0.6f ? 1f :
                            Math.max(0f, 1f - (float) ((cellDist - MAX_DIST * 0.6f) / (MAX_DIST * 0.4f)));
                    float edgeFade = zoneEdgeFade(cx, cz);
                    float baseCellShape = cellNoise(worldCellX, worldCellZ, 3);

                    for (int layer = 0; layer < CLOUD_LAYERS; layer++) {
                        float layerCoverage = config.coverage + CLOUD_LAYER_COVERAGE_ADJUST[layer];
                        if (cellNoise(worldCellX, worldCellZ, layer) > layerCoverage) continue;

                        float layerHeight = CLOUD_LAYER_HEIGHT[layer];
                        float layerWindScale = CLOUD_LAYER_WIND_SCALE[layer];
                        float layerDriftX = driftX * layerWindScale;
                        float layerDriftZ = driftZ * layerWindScale;

                        float cellWX = zoneWorldX + cx * CELL_SIZE + layerDriftX;
                        float cellWZ = zoneWorldZ + cz * CELL_SIZE + layerDriftZ;
                        float shapeOffset = 0f;
                        float thickness = CLOUD_THICKNESS;
                        float alpha = config.baseAlpha * weatherIntensity * CLOUD_LAYER_ALPHA_SCALE[layer] * distFade * edgeFade;
                        alpha = Math.min(1f, alpha + thunderPulse);
                        if (alpha < 0.01f) continue;

                        int topAi = (int) (alpha * 255);
                        int sideAi = (int) (alpha * 0.85f * 255);
                        int botAi = (int) (alpha * 0.75f * 255);

                        float brightnessBoost = thunderPulse * 0.75f + baseCellShape * 0.08f;
                        int topR = applyPulse(config.r, brightnessBoost);
                        int topG = applyPulse(config.g, brightnessBoost);
                        int topB = applyPulse(config.b, brightnessBoost);
                        int sideR = (int) (config.r * 0.70f);
                        int sideG = (int) (config.g * 0.70f);
                        int sideB = (int) (config.b * 0.70f);
                        int botR = (int) (config.r * 0.55f);
                        int botG = (int) (config.g * 0.55f);
                        int botB = (int) (config.b * 0.55f);

                        float x1 = (float) (cellWX - cam.x);
                        float z1 = (float) (cellWZ - cam.z);
                        float x2 = x1 + CELL_SIZE;
                        float z2 = z1 + CELL_SIZE;
                        float yBot = (float) (CLOUD_BASE + weatherHeightOffset + layerHeight + shapeOffset - cam.y);
                        float yTop = yBot + thickness;

                        boolean drawNorth = cellNoise(worldCellX, worldCellZ - 1, layer) > layerCoverage;
                        boolean drawSouth = cellNoise(worldCellX, worldCellZ + 1, layer) > layerCoverage;
                        boolean drawWest = cellNoise(worldCellX - 1, worldCellZ, layer) > layerCoverage;
                        boolean drawEast = cellNoise(worldCellX + 1, worldCellZ, layer) > layerCoverage;

                        buffer.vertex(mat, x1, yTop, z1).color(topR, topG, topB, topAi);
                        buffer.vertex(mat, x1, yTop, z2).color(topR, topG, topB, topAi);
                        buffer.vertex(mat, x2, yTop, z2).color(topR, topG, topB, topAi);
                        buffer.vertex(mat, x2, yTop, z1).color(topR, topG, topB, topAi);

                        buffer.vertex(mat, x2, yBot, z1).color(botR, botG, botB, botAi);
                        buffer.vertex(mat, x2, yBot, z2).color(botR, botG, botB, botAi);
                        buffer.vertex(mat, x1, yBot, z2).color(botR, botG, botB, botAi);
                        buffer.vertex(mat, x1, yBot, z1).color(botR, botG, botB, botAi);

                        if (drawNorth) {
                            buffer.vertex(mat, x1, yBot, z1).color(sideR, sideG, sideB, sideAi);
                            buffer.vertex(mat, x1, yTop, z1).color(sideR, sideG, sideB, sideAi);
                            buffer.vertex(mat, x2, yTop, z1).color(sideR, sideG, sideB, sideAi);
                            buffer.vertex(mat, x2, yBot, z1).color(sideR, sideG, sideB, sideAi);
                        }
                        if (drawSouth) {
                            buffer.vertex(mat, x2, yBot, z2).color(sideR, sideG, sideB, sideAi);
                            buffer.vertex(mat, x2, yTop, z2).color(sideR, sideG, sideB, sideAi);
                            buffer.vertex(mat, x1, yTop, z2).color(sideR, sideG, sideB, sideAi);
                            buffer.vertex(mat, x1, yBot, z2).color(sideR, sideG, sideB, sideAi);
                        }
                        if (drawWest) {
                            buffer.vertex(mat, x1, yBot, z2).color(sideR, sideG, sideB, sideAi);
                            buffer.vertex(mat, x1, yTop, z2).color(sideR, sideG, sideB, sideAi);
                            buffer.vertex(mat, x1, yTop, z1).color(sideR, sideG, sideB, sideAi);
                            buffer.vertex(mat, x1, yBot, z1).color(sideR, sideG, sideB, sideAi);
                        }
                        if (drawEast) {
                            buffer.vertex(mat, x2, yBot, z1).color(sideR, sideG, sideB, sideAi);
                            buffer.vertex(mat, x2, yTop, z1).color(sideR, sideG, sideB, sideAi);
                            buffer.vertex(mat, x2, yTop, z2).color(sideR, sideG, sideB, sideAi);
                            buffer.vertex(mat, x2, yBot, z2).color(sideR, sideG, sideB, sideAi);
                        }
                    }
                }
            }

            if (weather == WeatherZone.WeatherType.THUNDER && thunderPulse > 0.15f) {
                renderLightningArcs(mat, buffer, s.zoneX, s.zoneZ, worldTime, tickDelta, cam, thunderPulse);
            }
        }

        matrices.pop();
    }

    private static WeatherRenderConfig getWeatherConfig(WeatherZone.WeatherType weather) {
        return switch (weather) {
            case THUNDER -> new WeatherRenderConfig(0x3C, 0x3E, 0x45, COVERAGE_THUNDER, ALPHA_THUNDER);
            case SNOW -> new WeatherRenderConfig(0xA7, 0xAD, 0xB6, COVERAGE_SNOW, ALPHA_SNOW);
            case HAIL -> new WeatherRenderConfig(0x65, 0x6D, 0x76, COVERAGE_HAIL, ALPHA_HAIL);
            default -> new WeatherRenderConfig(0x7E, 0x83, 0x8B, COVERAGE_RAIN, ALPHA_RAIN);
        };
    }

    private static float getThunderPulse(long worldTime, float tickDelta, float transitionProgress, WeatherZone.WeatherType weather) {
        if (weather != WeatherZone.WeatherType.THUNDER) return 0f;
        double phase = (worldTime + tickDelta) * 0.60;
        float flash = (float) (Math.sin(phase) * Math.sin(phase * 1.31) * Math.sin(phase * 0.77));
        float pulse = Math.max(0f, flash) * 0.22f + 0.08f;
        return pulse * transitionProgress;
    }

    private static void renderLightningArcs(Matrix4f mat, VertexConsumer buffer, int zoneX, int zoneZ, long worldTime,
                                             float tickDelta, Vec3d cam, float thunderIntensity) {
        float arcPhase = (worldTime + tickDelta) * 0.8f;
        int arcCount = Math.max(1, (int) (2 + thunderIntensity * 3));

        for (int arc = 0; arc < arcCount; arc++) {
            float arcSeed = cellHash(zoneX * 31 + arc, zoneZ * 57 + arc, arc) / (float) Integer.MAX_VALUE;
            float arcX = (zoneX * ZONE_SIZE) + arcSeed * ZONE_SIZE;
            float arcZ = (zoneZ * ZONE_SIZE) + (cellHash(zoneX + arc, zoneZ - arc, arc) / (float) Integer.MAX_VALUE) * ZONE_SIZE;
            float arcBright = (float) Math.sin(arcPhase + arcSeed * 6.28f) * thunderIntensity;
            arcBright = Math.max(0f, arcBright);

            if (arcBright < 0.05f) continue;

            float x = (float) (arcX - cam.x);
            float z = (float) (arcZ - cam.z);
            float yTop = (float) (CLOUD_BASE + 15 - cam.y);
            float yBot = (float) (CLOUD_BASE - 5 - cam.y);

            int brightness = (int) (arcBright * 200);
            int alpha = (int) (arcBright * 240);

            buffer.vertex(mat, x - 2, yTop, z).color(255, 255, 255, alpha);
            buffer.vertex(mat, x + 2, yTop, z).color(255, 255, 255, alpha);
            buffer.vertex(mat, x + 2, yBot, z).color(brightness, brightness, 255, alpha);
            buffer.vertex(mat, x - 2, yBot, z).color(brightness, brightness, 255, alpha);
        }
    }

    private static float zoneEdgeFade(int cx, int cz) {
        int edge = Math.min(Math.min(cx, cz), Math.min(CELLS_PER_ZONE - 1 - cx, CELLS_PER_ZONE - 1 - cz));
        if (edge >= 3) return 1f;
        return 0.55f + edge * 0.15f;
    }

    private static Vec3d normalizedWind(double x, double z) {
        double length = Math.sqrt(x * x + z * z);
        return length < 1e-4 ? new Vec3d(1.0, 0.0, 0.0) : new Vec3d(x / length, 0.0, z / length);
    }

    private static int applyPulse(int channel, float pulse) {
        return Math.min(255, (int) (channel * (1f + pulse)));
    }

    private static int cellHash(int cx, int cz, int layer) {
        int h = cx * 374761393 + cz * 668265263 + layer * 2147483647;
        h = (h ^ (h >> 13)) * 1274126177;
        h = h ^ (h >> 16);
        return h;
    }

    private static float cellNoise(int cx, int cz, int layer) {
        return (cellHash(cx, cz, layer) & 0x7FFFFFFF) / (float) Integer.MAX_VALUE;
    }

    private record WeatherRenderConfig(int r, int g, int b, float coverage, float baseAlpha) {}
}
