package net.fentbusgaming.localweather.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fentbusgaming.localweather.network.ClientStormCellHandler;
import net.fentbusgaming.localweather.weather.StormCellManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
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
 *   <li><b>Cloud cap</b> — the anvil over the core, drawn only once the storm
 *       is past the reach of the zone-grid cloud deck, so a distant rain wall
 *       is not left hanging under clear sky.</li>
 * </ul>
 *
 * All three are anchored to the cell rather than to a zone, so they travel with
 * the storm. A storm beyond the fog horizon is not culled: its geometry is scaled
 * uniformly about the camera onto the horizon instead, which keeps every angle
 * (and therefore the apparent size) intact while pulling it back inside the
 * fog envelope. A thunderstorm several zones away still shows its rain wall.
 */
public class RainCurtainRenderer {

    /** Cloud layer bottom, matching {@link StormCloudRenderer}. */
    private static final float CLOUD_BASE = 191.0f;
    /** Thunder clouds hang lower than the rest — same offset the cloud renderer uses. */
    private static final float THUNDER_CLOUD_OFFSET = -10.0f;

    /** Top of the curtain (the cloud base) and where it dies out near the ground. */
    private static final float WALL_TOP = CLOUD_BASE + THUNDER_CLOUD_OFFSET;
    private static final float WALL_BOTTOM = 46.0f;

    /** Curtain tessellation: segments around the core, slices up the shaft. */
    private static final int WALL_SEGMENTS = 24;
    private static final int WALL_STEPS = 5;

    /** The shaft is narrow at the cloud base and spreads where it hits the ground. */
    private static final float WALL_TOP_SCALE = 0.92f;
    private static final float WALL_BOTTOM_SCALE = 1.16f;

    /** How far (blocks) the top of the shaft leads its base, along the cell's heading. */
    private static final float WALL_LEAN = 26.0f;

    private static final float WALL_ALPHA = 0.42f;

    /** Trailing rain bands. */
    private static final int BAND_COUNT = 3;
    private static final int BAND_SEGMENTS = 12;
    private static final float BAND_ALPHA_SCALE = 0.62f;
    /** Half the angular width of the innermost band, in radians. */
    private static final double BAND_HALF_SPAN = 0.72;
    /** Each successive band is swept further around the core. */
    private static final double BAND_SPIRAL = 0.30;
    /** Bands rotate slowly around the cell (radians per tick). */
    private static final double BAND_SPIN_SPEED = 0.00015;
    /** Bands are wispier at their base than the core curtain is. */
    private static final float BAND_BOTTOM_PROFILE = 0.30f;
    /** Where along the ground→cloud colour ramp a band's base is tinted. */
    private static final float BAND_BOTTOM_TINT = 0.35f;
    /** Bands lean with the storm, but less than the core curtain does. */
    private static final float BAND_LEAN_SCALE = 0.6f;

    /** Sheets of rain travelling around the curtain: cycles per turn, and speed. */
    private static final double SHEET_CYCLES = 3.0;
    private static final double SHEET_SPEED = 0.05;

    /** Colour of the curtain: hazy grey at the ground, storm-dark under the cloud. */
    private static final int BOTTOM_R = 0x8A, BOTTOM_G = 0x93, BOTTOM_B = 0xA2;
    private static final int TOP_R = 0x46, TOP_G = 0x4E, TOP_B = 0x5C;

    /** Curtains nearer than this never get pushed onto the horizon. */
    private static final double MIN_HORIZON_DIST = 160.0;
    /** Fraction of the view distance the horizon projection sits at. */
    private static final double HORIZON_VIEW_FRACTION = 0.85;

    /** Looking through more air thickens a distant curtain slightly. */
    private static final float DISTANCE_BOOST_PER_BLOCK = 1.0f / 6000.0f;
    private static final float MAX_DISTANCE_BOOST = 0.35f;

    /**
     * Reach of the zone-grid cloud deck. {@link StormCloudRenderer} builds the
     * deck out of the 5x5 zone grid the client caches, so it runs out a little
     * past two zones, while cells keep syncing to
     * {@link StormCellManager#SYNC_DISTANCE}. Past that reach a cell draws its
     * own cloud instead, faded in between the two distances below so the deck
     * and the cap cross over rather than stacking.
     */
    private static final double DECK_REACH = 560.0;
    private static final double CAP_FULL_AT = 900.0;

    /**
     * Cells stop being sent past {@link StormCellManager#SYNC_DISTANCE}, so the
     * storm thins out over the approach to it rather than reaching that point at
     * full strength.
     */
    private static final double FAR_FADE_END = StormCellManager.SYNC_DISTANCE;
    private static final double FAR_FADE_START = FAR_FADE_END * 0.78;

    /** The cap: an anvil sitting on the cloud base and spreading as it rises. */
    private static final float CAP_BOTTOM = WALL_TOP - 4.0f;
    private static final float CAP_TOP = WALL_TOP + 30.0f;
    private static final int CAP_SEGMENTS = 20;
    private static final int CAP_STEPS = 3;
    private static final int CAP_RINGS = 3;
    private static final float CAP_BOTTOM_SCALE = 1.20f;
    private static final float CAP_TOP_SCALE = 2.05f;
    /** The anvil blows further downwind than the curtain below it leans. */
    private static final float CAP_LEAN_SCALE = 1.4f;
    /** How far the middle of the top domes above its rim. */
    private static final float CAP_DOME = 7.0f;
    private static final float CAP_ALPHA = 0.70f;

    /** Anvil colour: thunder-deck grey underneath, lighter where the top catches light. */
    private static final int CAP_BOTTOM_R = 0x2E, CAP_BOTTOM_G = 0x30, CAP_BOTTOM_B = 0x37;
    private static final int CAP_TOP_R = 0x53, CAP_TOP_G = 0x57, CAP_TOP_B = 0x61;

    /**
     * Position + colour only, which is exactly what this renderer emits.
     *
     * {@code translucentMovingBlock()} is a textured block layer: its vertex
     * format also wants UV0 and UV2, and 26.x's BufferBuilder throws
     * "Missing elements in vertex: UV0, UV2" rather than defaulting them.
     * {@code debugFilledBox()} is built from a POSITION_COLOR / QUADS snippet
     * with translucent blending and culling left on, so it matches this
     * geometry without inventing texture or lightmap coordinates.
     */
    private static final RenderType CURTAIN_RENDER_LAYER = RenderTypes.debugFilledBox();

    /** Submits the rain wall and rain bands for this frame. See {@link StormCloudRenderer#render}. */
    public static void render(LevelRenderState levelState, SubmitNodeCollector collector, PoseStack poseStack) {
        if (!ClientStormCellHandler.hasCells()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 cam = levelState.cameraRenderState.pos;
        double time = client.level.getGameTime() + tickDelta;
        double horizon = horizonDistance(client);

        // 26.x renders the level from submitted nodes rather than from an
        // immediate-mode buffer, so the curtain geometry is handed over as one
        // custom-geometry node and built when the translucent pass runs.
        collector.submitCustomGeometry(
                poseStack,
                CURTAIN_RENDER_LAYER,
                (pose, buffer) -> {
                    Matrix4f mat = pose.pose();
                    for (ClientStormCellHandler.StormCellState cell : ClientStormCellHandler.getCells()) {
                        renderCell(mat, buffer, cell, cam, tickDelta, time, horizon);
                    }
                });
    }

    private static void renderCell(Matrix4f mat, VertexConsumer buffer,
                                   ClientStormCellHandler.StormCellState cell,
                                   Vec3 cam, float tickDelta, double time, double horizon) {
        float intensity = cell.getIntensity();
        if (intensity < 0.03f) return;

        // Camera-relative centre. Everything below is built in this space so a
        // distant cell can be scaled onto the horizon with a single multiply.
        double centerX = cell.getRenderX(tickDelta) - cam.x;
        double centerZ = cell.getRenderZ(tickDelta) - cam.z;
        double dist = Math.sqrt(centerX * centerX + centerZ * centerZ);

        float radius = cell.getRadius();
        float scale = dist > horizon ? (float) (horizon / dist) : 1.0f;

        // Fade the curtain out as the camera moves into the core — from inside a
        // storm you are in the rain, not looking at a wall of it.
        float nearFade = clamp01((float) ((dist - radius * 0.25) / (radius * 0.75)));
        if (nearFade <= 0.0f) return;

        float farFade = farFade(dist);
        if (farFade <= 0.0f) return;

        double heading = Math.atan2(cell.getVelZ(), cell.getVelX());
        float leanX = (float) (Math.cos(heading) * WALL_LEAN);
        float leanZ = (float) (Math.sin(heading) * WALL_LEAN);
        float cellPhase = (cell.id % 32) * 0.37f;

        float distanceBoost = 1.0f + Math.min(MAX_DISTANCE_BOOST, (float) dist * DISTANCE_BOOST_PER_BLOCK);
        float baseAlpha = WALL_ALPHA * intensity * nearFade * distanceBoost * farFade * cell.getFade();
        if (baseAlpha >= 0.01f) {
            renderRainWall(mat, buffer, centerX, centerZ, (float) (cam.y), radius, baseAlpha,
                    leanX, leanZ, scale, time, cellPhase);
            renderRainBands(mat, buffer, centerX, centerZ, (float) (cam.y), radius, baseAlpha,
                    leanX, leanZ, scale, time, heading, cellPhase);
        }

        // Past the zone grid the deck cannot reach the storm, and the wall would
        // otherwise be left hanging on the horizon under clear sky.
        float capFade = clamp01((float) ((dist - DECK_REACH) / (CAP_FULL_AT - DECK_REACH)));
        float capAlpha = CAP_ALPHA * intensity * capFade * farFade * cell.getFade();
        if (capAlpha >= 0.01f) {
            renderCloudCap(mat, buffer, centerX, centerZ, (float) (cam.y), radius, capAlpha,
                    leanX, leanZ, scale);
        }
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
                // Slow-moving sheets of rain travelling around the shaft. Driven by
                // the angle rather than the segment index so the pattern wraps cleanly.
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
            // Bands hang below the cloud deck but do not reach the ground.
            float bandTopY = WALL_TOP - 6.0f - 5.0f * band;
            float bandBottomY = WALL_BOTTOM + 18.0f + 10.0f * band;
            float bandAlpha = baseAlpha * BAND_ALPHA_SCALE / (1.0f + band * 0.55f);
            if (bandAlpha < 0.008f) continue;

            // Bands trail the core, each swept a little further around it.
            double bandCenter = heading + Math.PI + BAND_SPIRAL * (band + 1) + spin;
            double halfSpan = BAND_HALF_SPAN - band * 0.10;

            float y0 = (bandBottomY - camY) * scale;
            float y1 = (bandTopY - camY) * scale;
            for (int seg = 0; seg < BAND_SEGMENTS; seg++) {
                float u0 = seg / (float) BAND_SEGMENTS;
                float u1 = (seg + 1) / (float) BAND_SEGMENTS;
                double angA = bandCenter + (u0 * 2.0 - 1.0) * halfSpan;
                double angB = bandCenter + (u1 * 2.0 - 1.0) * halfSpan;

                // Taper both ends of the arc so bands trail off instead of stopping dead.
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
    // Cloud cap
    // -------------------------------------------------------------------------

    /**
     * The storm's own cloud, built from the cell instead of from the zone grid.
     *
     * {@link StormCloudRenderer} draws its deck over the 5x5 zone grid the
     * client caches, which runs out a little past two zones, while cells sync
     * out to {@link StormCellManager#SYNC_DISTANCE}. Over the far part of that
     * range the rain wall was the only thing left, standing on the horizon
     * under clear sky. The cap is an anvil — a shell widening as it rises,
     * closed by a domed top — and it takes the same horizon scaling the wall
     * does, so the cloud and the curtain under it stay one object however far
     * away the storm is.
     */
    private static void renderCloudCap(Matrix4f mat, VertexConsumer buffer,
                                       double centerX, double centerZ, float camY,
                                       float radius, float alpha,
                                       float leanX, float leanZ, float scale) {
        int alphaByte = alphaByte(alpha);
        if (alphaByte <= 0) return;

        for (int step = 0; step < CAP_STEPS; step++) {
            float t0 = step / (float) CAP_STEPS;
            float t1 = (step + 1) / (float) CAP_STEPS;

            float y0 = (lerp(CAP_BOTTOM, CAP_TOP, t0) - camY) * scale;
            float y1 = (lerp(CAP_BOTTOM, CAP_TOP, t1) - camY) * scale;
            float r0 = radius * lerp(CAP_BOTTOM_SCALE, CAP_TOP_SCALE, t0);
            float r1 = radius * lerp(CAP_BOTTOM_SCALE, CAP_TOP_SCALE, t1);
            float lx0 = leanX * CAP_LEAN_SCALE * t0;
            float lz0 = leanZ * CAP_LEAN_SCALE * t0;
            float lx1 = leanX * CAP_LEAN_SCALE * t1;
            float lz1 = leanZ * CAP_LEAN_SCALE * t1;

            for (int seg = 0; seg < CAP_SEGMENTS; seg++) {
                double angA = seg * 2.0 * Math.PI / CAP_SEGMENTS;
                double angB = (seg + 1) * 2.0 * Math.PI / CAP_SEGMENTS;

                float xA0 = (float) (centerX + Math.cos(angA) * r0 + lx0) * scale;
                float zA0 = (float) (centerZ + Math.sin(angA) * r0 + lz0) * scale;
                float xB0 = (float) (centerX + Math.cos(angB) * r0 + lx0) * scale;
                float zB0 = (float) (centerZ + Math.sin(angB) * r0 + lz0) * scale;
                float xA1 = (float) (centerX + Math.cos(angA) * r1 + lx1) * scale;
                float zA1 = (float) (centerZ + Math.sin(angA) * r1 + lz1) * scale;
                float xB1 = (float) (centerX + Math.cos(angB) * r1 + lx1) * scale;
                float zB1 = (float) (centerZ + Math.sin(angB) * r1 + lz1) * scale;

                emitCapQuad(mat, buffer,
                        xA0, y0, zA0, xB0, y0, zB0,
                        xB1, y1, zB1, xA1, y1, zA1,
                        t0, t1, alphaByte);
            }
        }

        // Close the top, domed rather than flat so the anvil reads as a cloud
        // and not as a plate laid over the storm.
        float topRadius = radius * CAP_TOP_SCALE;
        float lxTop = leanX * CAP_LEAN_SCALE;
        float lzTop = leanZ * CAP_LEAN_SCALE;
        for (int ring = 0; ring < CAP_RINGS; ring++) {
            float f0 = ring / (float) CAP_RINGS;
            float f1 = (ring + 1) / (float) CAP_RINGS;

            float r0 = topRadius * f0;
            float r1 = topRadius * f1;
            float y0 = (CAP_TOP + CAP_DOME * (1.0f - f0 * f0) - camY) * scale;
            float y1 = (CAP_TOP + CAP_DOME * (1.0f - f1 * f1) - camY) * scale;

            for (int seg = 0; seg < CAP_SEGMENTS; seg++) {
                double angA = seg * 2.0 * Math.PI / CAP_SEGMENTS;
                double angB = (seg + 1) * 2.0 * Math.PI / CAP_SEGMENTS;

                float xA0 = (float) (centerX + Math.cos(angA) * r0 + lxTop) * scale;
                float zA0 = (float) (centerZ + Math.sin(angA) * r0 + lzTop) * scale;
                float xB0 = (float) (centerX + Math.cos(angB) * r0 + lxTop) * scale;
                float zB0 = (float) (centerZ + Math.sin(angB) * r0 + lzTop) * scale;
                float xA1 = (float) (centerX + Math.cos(angA) * r1 + lxTop) * scale;
                float zA1 = (float) (centerZ + Math.sin(angA) * r1 + lzTop) * scale;
                float xB1 = (float) (centerX + Math.cos(angB) * r1 + lxTop) * scale;
                float zB1 = (float) (centerZ + Math.sin(angB) * r1 + lzTop) * scale;

                // The top is all at the lit end of the colour ramp.
                emitCapQuad(mat, buffer,
                        xA0, y0, zA0, xB0, y0, zB0,
                        xB1, y1, zB1, xA1, y1, zA1,
                        1.0f, 1.0f, alphaByte);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Geometry helpers
    // -------------------------------------------------------------------------

    /**
     * Emit one curtain quad, twice, so it reads as a translucent sheet from both
     * sides. The bottom edge runs A→B and the top edge sits directly above it.
     * {@code tBot}/{@code tTop} pick the colour along the ground→cloud ramp.
     */
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

    /**
     * Emit one cap quad, twice, so the anvil reads as solid from underneath as
     * well as from above. {@code tLow} and {@code tHigh} pick the colour along
     * the underside-to-top ramp for the first and the second edge.
     */
    private static void emitCapQuad(Matrix4f mat, VertexConsumer buffer,
                                    float x0, float y0, float z0,
                                    float x1, float y1, float z1,
                                    float x2, float y2, float z2,
                                    float x3, float y3, float z3,
                                    float tLow, float tHigh, int alpha) {
        int rLow = (int) lerp(CAP_BOTTOM_R, CAP_TOP_R, tLow);
        int gLow = (int) lerp(CAP_BOTTOM_G, CAP_TOP_G, tLow);
        int bLow = (int) lerp(CAP_BOTTOM_B, CAP_TOP_B, tLow);
        int rHigh = (int) lerp(CAP_BOTTOM_R, CAP_TOP_R, tHigh);
        int gHigh = (int) lerp(CAP_BOTTOM_G, CAP_TOP_G, tHigh);
        int bHigh = (int) lerp(CAP_BOTTOM_B, CAP_TOP_B, tHigh);

        buffer.addVertex(mat, x0, y0, z0).setColor(rLow, gLow, bLow, alpha);
        buffer.addVertex(mat, x1, y1, z1).setColor(rLow, gLow, bLow, alpha);
        buffer.addVertex(mat, x2, y2, z2).setColor(rHigh, gHigh, bHigh, alpha);
        buffer.addVertex(mat, x3, y3, z3).setColor(rHigh, gHigh, bHigh, alpha);

        buffer.addVertex(mat, x3, y3, z3).setColor(rHigh, gHigh, bHigh, alpha);
        buffer.addVertex(mat, x2, y2, z2).setColor(rHigh, gHigh, bHigh, alpha);
        buffer.addVertex(mat, x1, y1, z1).setColor(rLow, gLow, bLow, alpha);
        buffer.addVertex(mat, x0, y0, z0).setColor(rLow, gLow, bLow, alpha);
    }

    /**
     * Fade over the approach to the distance cells stop being sent at. Without
     * it a storm is at full strength right up to that point and only thins out
     * once the server has already gone quiet about it, which reads as the storm
     * dissolving a beat after it should have.
     */
    private static float farFade(double dist) {
        if (dist <= FAR_FADE_START) return 1.0f;
        return clamp01((float) ((FAR_FADE_END - dist) / (FAR_FADE_END - FAR_FADE_START)));
    }

    /**
     * How strongly a segment of the shell contributes: the side facing the
     * camera carries the curtain, the far side only adds depth behind it.
     */
    private static float facingWeight(double centerX, double centerZ, float radius, double angle) {
        double px = centerX + Math.cos(angle) * radius;
        double pz = centerZ + Math.sin(angle) * radius;
        double len = Math.sqrt(px * px + pz * pz);
        if (len < 1.0e-4) return 1.0f;
        double dot = (-px / len) * Math.cos(angle) + (-pz / len) * Math.sin(angle);
        return 0.42f + 0.58f * (float) Math.max(0.0, dot);
    }

    /** Density up the shaft: ragged where it evaporates, solid under the cloud. */
    private static float verticalProfile(float t) {
        float profile = 0.28f + 0.72f * t;
        if (t < 0.15f) {
            profile *= t / 0.15f;
        }
        return profile;
    }

    /**
     * Distance the horizon projection sits at. Anything further than this is
     * scaled onto it so it stays inside the fog envelope and keeps rendering.
     */
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
