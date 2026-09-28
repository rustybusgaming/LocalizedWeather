package net.fentbusgaming.localweather.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fentbusgaming.localweather.network.ClientStormCellHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the precipitation shaft of a moving single-cell thunderstorm:
 *
 * <ul>
 *   <li><b>Rain wall</b> — the curtain hanging under the storm core, leaning
 *       downwind and flaring out where it reaches the ground.</li>
 *   <li><b>Rain bands</b> — shallower arcs of precipitation trailing the core,
 *       fading out at their ends and slowly rotating around the cell.</li>
 * </ul>
 */
public class RainCurtainRenderer {

    private static final float CLOUD_BASE = 191.0f;
    private static final float THUNDER_CLOUD_OFFSET = -10.0f;

    private static final float WALL_TOP = CLOUD_BASE + THUNDER_CLOUD_OFFSET;
    private static final float WALL_BOTTOM = 46.0f;

    private static final int WALL_SEGMENTS = 24;
    private static final int WALL_STEPS = 5;

    private static final float WALL_TOP_SCALE = 0.92f;
    private static final float WALL_BOTTOM_SCALE = 1.16f;

    private static final float WALL_LEAN = 26.0f;

    private static final float WALL_ALPHA = 0.42f;

    private static final int BAND_COUNT = 3;
    private static final int BAND_SEGMENTS = 12;
    private static final float BAND_ALPHA_SCALE = 0.62f;
    private static final double BAND_HALF_SPAN = 0.72;
    private static final double BAND_SPIRAL = 0.30;
    private static final double BAND_SPIN_SPEED = 0.00015;
    private static final float BAND_BOTTOM_PROFILE = 0.30f;
    private static final float BAND_BOTTOM_TINT = 0.35f;
    private static final float BAND_LEAN_SCALE = 0.6f;

    private static final double SHEET_CYCLES = 3.0;
    private static final double SHEET_SPEED = 0.05;

    private static final int BOTTOM_R = 0x8A, BOTTOM_G = 0x93, BOTTOM_B = 0xA2;
    private static final int TOP_R = 0x46, TOP_G = 0x4E, TOP_B = 0x5C;

    private static final double MIN_HORIZON_DIST = 160.0;
    private static final double HORIZON_VIEW_FRACTION = 0.85;

    private static final float DISTANCE_BOOST_PER_BLOCK = 1.0f / 6000.0f;
    private static final float MAX_DISTANCE_BOOST = 0.35f;

    private static final RenderType CURTAIN_RENDER_LAYER = RenderType.debugFilledBox();

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cam, float tickDelta) {
        if (!ClientStormCellHandler.hasCells()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        double time = client.level.getGameTime() + tickDelta;
        double horizon = horizonDistance(client);

        VertexConsumer buffer = bufferSource.getBuffer(CURTAIN_RENDER_LAYER);
        Matrix4f mat = poseStack.last().pose();
        for (ClientStormCellHandler.StormCellState cell : ClientStormCellHandler.getCells()) {
            renderCell(mat, buffer, cell, cam, tickDelta, time, horizon);
        }
    }

    private static void renderCell(Matrix4f mat, VertexConsumer buffer,
                                   ClientStormCellHandler.StormCellState cell,
                                   Vec3 cam, float tickDelta, double time, double horizon) {
        float intensity = cell.getIntensity();
        if (intensity < 0.03f) return;

        double centerX = cell.getRenderX(tickDelta) - cam.x;
        double centerZ = cell.getRenderZ(tickDelta) - cam.z;
        double dist = Math.sqrt(centerX * centerX + centerZ * centerZ);

        float radius = cell.getRadius();
        float scale = dist > horizon ? (float) (horizon / dist) : 1.0f;

        float nearFade = clamp01((float) ((dist - radius * 0.25) / (radius * 0.75)));
        if (nearFade <= 0.0f) return;

        float distanceBoost = 1.0f + Math.min(MAX_DISTANCE_BOOST, (float) dist * DISTANCE_BOOST_PER_BLOCK);
        float baseAlpha = WALL_ALPHA * intensity * nearFade * distanceBoost * cell.getFade();
        if (baseAlpha < 0.01f) return;

        double heading = Math.atan2(cell.getVelZ(), cell.getVelX());
        float leanX = (float) (Math.cos(heading) * WALL_LEAN);
        float leanZ = (float) (Math.sin(heading) * WALL_LEAN);
        float cellPhase = (cell.id % 32) * 0.37f;

        renderRainWall(mat, buffer, centerX, centerZ, (float) (cam.y), radius, baseAlpha,
                leanX, leanZ, scale, time, cellPhase);
        renderRainBands(mat, buffer, centerX, centerZ, (float) (cam.y), radius, baseAlpha,
                leanX, leanZ, scale, time, heading, cellPhase);
    }

    // -------------------------------------------------------------------------
    // Rain wall
    // -------------------------------------------------------------------------

    private static void renderRainWall(Matrix4f mat, VertexConsumer buffer,
                                       double centerX, double centerZ, float camY,
                                       float radius, float baseAlpha,
                                       float leanX, float leanZ, float scale,
                                       double time, float cellPhase) {

        for (int step = 0; step < WALL_STEPS; step++) {
            float t0 = step / (float) WALL_STEPS;
            float t1 = (step + 1) / (float) WALL_STEPS;

            float y0 = (lerp(WALL_BOTTOM, WALL_TOP, t0) - camY) * scale;
            float y1 = (lerp(WALL_BOTTOM, WALL_TOP, t1) - camY) * scale;
            float r0 = radius * lerp(WALL_BOTTOM_SCALE, WALL_TOP_SCALE, t0);
            float r1 = radius * lerp(WALL_BOTTOM_SCALE, WALL_TOP_SCALE, t1);
            float profile0 = verticalProfile(t0);
            float profile1 = verticalProfile(t1);

            for (int seg = 0; seg < WALL_SEGMENTS; seg++) {
                double angA = seg * 2.0 * Math.PI / WALL_SEGMENTS;
                double angB = (seg + 1) * 2.0 * Math.PI / WALL_SEGMENTS;
                double angMid = (angA + angB) * 0.5;

                float facing = facingWeight(centerX, centerZ, radius, angMid);
                float sheet = 0.72f + 0.28f * (float) Math.sin(angMid * SHEET_CYCLES + time * SHEET_SPEED + cellPhase);
                float segAlpha = baseAlpha * facing * sheet;
                if (segAlpha < 0.008f) continue;

                float xA0 = (float) (centerX + Math.cos(angA) * r0 + leanX * t0) * scale;
                float zA0 = (float) (centerZ + Math.sin(angA) * r0 + leanZ * t0) * scale;
                float xB0 = (float) (centerX + Math.cos(angB) * r0 + leanX * t0) * scale;
                float zB0 = (float) (centerZ + Math.sin(angB) * r0 + leanZ * t0) * scale;
                float xA1 = (float) (centerX + Math.cos(angA) * r1 + leanX * t1) * scale;
                float zA1 = (float) (centerZ + Math.sin(angA) * r1 + leanZ * t1) * scale;
                float xB1 = (float) (centerX + Math.cos(angB) * r1 + leanX * t1) * scale;
                float zB1 = (float) (centerZ + Math.sin(angB) * r1 + leanZ * t1) * scale;

                int aBot = alphaByte(segAlpha * profile0);
                int aTop = alphaByte(segAlpha * profile1);

                emitCurtainQuad(mat, buffer,
                        xA0, zA0, xB0, zB0, y0,
                        xA1, zA1, xB1, zB1, y1,
                        t0, t1, aBot, aBot, aTop, aTop);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Rain bands
    // -------------------------------------------------------------------------

    private static void renderRainBands(Matrix4f mat, VertexConsumer buffer,
                                        double centerX, double centerZ, float camY,
                                        float radius, float baseAlpha,
                                        float leanX, float leanZ, float scale,
                                        double time, double heading, float cellPhase) {

        double spin = time * BAND_SPIN_SPEED + cellPhase;

        for (int band = 0; band < BAND_COUNT; band++) {
            float bandRadius = radius * (1.65f + 0.80f * band);
            float bandTopY = WALL_TOP - 6.0f - 5.0f * band;
            float bandBottomY = WALL_BOTTOM + 18.0f + 10.0f * band;
            float bandAlpha = baseAlpha * BAND_ALPHA_SCALE / (1.0f + band * 0.55f);
            if (bandAlpha < 0.008f) continue;

            double bandCenter = heading + Math.PI + BAND_SPIRAL * (band + 1) + spin;
            double halfSpan = BAND_HALF_SPAN - band * 0.10;

            float y0 = (bandBottomY - camY) * scale;
            float y1 = (bandTopY - camY) * scale;
            for (int seg = 0; seg < BAND_SEGMENTS; seg++) {
                float u0 = seg / (float) BAND_SEGMENTS;
                float u1 = (seg + 1) / (float) BAND_SEGMENTS;
                double angA = bandCenter + (u0 * 2.0 - 1.0) * halfSpan;
                double angB = bandCenter + (u1 * 2.0 - 1.0) * halfSpan;

                float taperA = (float) Math.sin(Math.PI * u0);
                float taperB = (float) Math.sin(Math.PI * u1);
                float facing = facingWeight(centerX, centerZ, bandRadius, (angA + angB) * 0.5);
                float sheet = 0.70f + 0.30f * (float) Math.sin(angA * SHEET_CYCLES + time * SHEET_SPEED + band + cellPhase);
                float segAlpha = bandAlpha * facing * sheet;

                float xA0 = (float) (centerX + Math.cos(angA) * bandRadius) * scale;
                float zA0 = (float) (centerZ + Math.sin(angA) * bandRadius) * scale;
                float xB0 = (float) (centerX + Math.cos(angB) * bandRadius) * scale;
                float zB0 = (float) (centerZ + Math.sin(angB) * bandRadius) * scale;
                float xA1 = (float) (centerX + Math.cos(angA) * bandRadius + leanX * BAND_LEAN_SCALE) * scale;
                float zA1 = (float) (centerZ + Math.sin(angA) * bandRadius + leanZ * BAND_LEAN_SCALE) * scale;
                float xB1 = (float) (centerX + Math.cos(angB) * bandRadius + leanX * BAND_LEAN_SCALE) * scale;
                float zB1 = (float) (centerZ + Math.sin(angB) * bandRadius + leanZ * BAND_LEAN_SCALE) * scale;

                int aBotA = alphaByte(segAlpha * taperA * BAND_BOTTOM_PROFILE);
                int aBotB = alphaByte(segAlpha * taperB * BAND_BOTTOM_PROFILE);
                int aTopA = alphaByte(segAlpha * taperA);
                int aTopB = alphaByte(segAlpha * taperB);
                if (aTopA + aTopB == 0) continue;

                emitCurtainQuad(mat, buffer,
                        xA0, zA0, xB0, zB0, y0,
                        xA1, zA1, xB1, zB1, y1,
                        BAND_BOTTOM_TINT, 1.0f, aBotA, aBotB, aTopA, aTopB);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Geometry helpers
    // -------------------------------------------------------------------------

    private static void emitCurtainQuad(Matrix4f mat, VertexConsumer buffer,
                                        float xA0, float zA0, float xB0, float zB0, float y0,
                                        float xA1, float zA1, float xB1, float zB1, float y1,
                                        float tBot, float tTop,
                                        int aBotA, int aBotB, int aTopA, int aTopB) {
        int rBot = (int) lerp(BOTTOM_R, TOP_R, tBot);
        int gBot = (int) lerp(BOTTOM_G, TOP_G, tBot);
        int bBot = (int) lerp(BOTTOM_B, TOP_B, tBot);
        int rTop = (int) lerp(BOTTOM_R, TOP_R, tTop);
        int gTop = (int) lerp(BOTTOM_G, TOP_G, tTop);
        int bTop = (int) lerp(BOTTOM_B, TOP_B, tTop);

        buffer.addVertex(mat, xA0, y0, zA0).setColor(rBot, gBot, bBot, aBotA);
        buffer.addVertex(mat, xB0, y0, zB0).setColor(rBot, gBot, bBot, aBotB);
        buffer.addVertex(mat, xB1, y1, zB1).setColor(rTop, gTop, bTop, aTopB);
        buffer.addVertex(mat, xA1, y1, zA1).setColor(rTop, gTop, bTop, aTopA);

        buffer.addVertex(mat, xA1, y1, zA1).setColor(rTop, gTop, bTop, aTopA);
        buffer.addVertex(mat, xB1, y1, zB1).setColor(rTop, gTop, bTop, aTopB);
        buffer.addVertex(mat, xB0, y0, zB0).setColor(rBot, gBot, bBot, aBotB);
        buffer.addVertex(mat, xA0, y0, zA0).setColor(rBot, gBot, bBot, aBotA);
    }

    private static float facingWeight(double centerX, double centerZ, float radius, double angle) {
        double px = centerX + Math.cos(angle) * radius;
        double pz = centerZ + Math.sin(angle) * radius;
        double len = Math.sqrt(px * px + pz * pz);
        if (len < 1.0e-4) return 1.0f;
        double dot = (-px / len) * Math.cos(angle) + (-pz / len) * Math.sin(angle);
        return 0.42f + 0.58f * (float) Math.max(0.0, dot);
    }

    private static float verticalProfile(float t) {
        float profile = 0.28f + 0.72f * t;
        if (t < 0.15f) {
            profile *= t / 0.15f;
        }
        return profile;
    }

    private static double horizonDistance(Minecraft client) {
        double viewDistanceBlocks = client.options.getEffectiveRenderDistance() * 16.0;
        return Math.max(MIN_HORIZON_DIST, viewDistanceBlocks * HORIZON_VIEW_FRACTION);
    }

    private static int alphaByte(float alpha) {
        return (int) (clamp01(alpha) * 255);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
