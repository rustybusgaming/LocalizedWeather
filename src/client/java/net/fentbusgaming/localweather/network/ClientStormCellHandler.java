package net.fentbusgaming.localweather.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fentbusgaming.localweather.LocalWeatherMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of the moving single-cell thunderstorms sent by the server.
 *
 * The server only syncs a cell once a second, so each cell is extrapolated along
 * its own velocity every tick and nudged back toward the authoritative position
 * when a packet lands. The rain wall and rain bands then travel smoothly with
 * the storm instead of jumping once per sync.
 */
@Environment(EnvType.CLIENT)
public final class ClientStormCellHandler {

    /** A cell that has not been re-sent for this long is retired. */
    private static final int STALE_TICKS = 80;

    /** Once a cell goes quiet for this long it starts fading rather than popping out. */
    private static final int FADE_AFTER_TICKS = 40;

    /** Fraction of the remaining position error closed per tick. */
    private static final double CORRECTION_RATE = 0.12;

    /** Errors above this (blocks) are snapped instead of eased away. */
    private static final double SNAP_DISTANCE = 96.0;

    private static final Map<Integer, StormCellState> CELLS = new ConcurrentHashMap<>();

    private static ClientLevel activeWorld;

    private ClientStormCellHandler() {}

    // -------------------------------------------------------------------------
    // State per cell
    // -------------------------------------------------------------------------

    public static final class StormCellState {
        public final int id;

        /** Authoritative position, itself extrapolated between packets. */
        private double serverX, serverZ;
        /** Rendered position and its value last tick, for frame interpolation. */
        private double x, z, prevX, prevZ;

        private double velX, velZ;
        private float radius;
        private float intensity;
        private int ticksSinceUpdate;

        StormCellState(int id, double x, double z, double velX, double velZ, float radius, float intensity) {
            this.id = id;
            this.serverX = x;
            this.serverZ = z;
            this.x = x;
            this.z = z;
            this.prevX = x;
            this.prevZ = z;
            this.velX = velX;
            this.velZ = velZ;
            this.radius = radius;
            this.intensity = intensity;
        }

        void update(double newX, double newZ, double newVelX, double newVelZ, float newRadius, float newIntensity) {
            this.serverX = newX;
            this.serverZ = newZ;
            this.velX = newVelX;
            this.velZ = newVelZ;
            this.radius = newRadius;
            this.intensity = newIntensity;
            this.ticksSinceUpdate = 0;
        }

        void tick() {
            ticksSinceUpdate++;

            // The server position keeps moving between packets too.
            serverX += velX;
            serverZ += velZ;

            prevX = x;
            prevZ = z;

            double errorX = serverX - x;
            double errorZ = serverZ - z;
            if (errorX * errorX + errorZ * errorZ > SNAP_DISTANCE * SNAP_DISTANCE) {
                x = serverX;
                z = serverZ;
            } else {
                x += velX + errorX * CORRECTION_RATE;
                z += velZ + errorZ * CORRECTION_RATE;
            }
        }

        boolean isStale() {
            return ticksSinceUpdate > STALE_TICKS;
        }

        public double getRenderX(float tickDelta) {
            return prevX + (x - prevX) * tickDelta;
        }

        public double getRenderZ(float tickDelta) {
            return prevZ + (z - prevZ) * tickDelta;
        }

        public double getVelX() {
            return velX;
        }

        public double getVelZ() {
            return velZ;
        }

        public float getRadius() {
            return radius;
        }

        public float getIntensity() {
            return intensity;
        }

        /**
         * Fades a cell out once the server stops mentioning it — a storm that
         * drifts out of sync range dissolves instead of vanishing in one frame.
         */
        public float getFade() {
            if (ticksSinceUpdate <= FADE_AFTER_TICKS) return 1.0f;
            return 1.0f - (ticksSinceUpdate - FADE_AFTER_TICKS) / (float) (STALE_TICKS - FADE_AFTER_TICKS);
        }
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                WeatherPackets.StormCellPayload.ID,
                (payload, context) -> {
                    StormCellState existing = CELLS.get(payload.cellId());
                    if (existing != null) {
                        existing.update(payload.x(), payload.z(), payload.velX(), payload.velZ(),
                                payload.radius(), payload.intensity());
                    } else {
                        CELLS.put(payload.cellId(), new StormCellState(payload.cellId(),
                                payload.x(), payload.z(), payload.velX(), payload.velZ(),
                                payload.radius(), payload.intensity()));
                    }
                }
        );

        ClientTickEvents.END_CLIENT_TICK.register(ClientStormCellHandler::onClientTick);
        LocalWeatherMod.LOGGER.info("[LocalWeather] Client storm cell handler registered.");
    }

    private static void onClientTick(Minecraft client) {
        ClientLevel world = client.level;
        if (world != activeWorld) {
            activeWorld = world;
            CELLS.clear();
        }
        if (world == null) return;

        CELLS.values().forEach(StormCellState::tick);
        CELLS.values().removeIf(StormCellState::isStale);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public static Collection<StormCellState> getCells() {
        return CELLS.values();
    }

    public static boolean hasCells() {
        return !CELLS.isEmpty();
    }

    /**
     * Rain strength contributed by a storm cell core covering a position, with a
     * soft edge over the outer quarter of the core. Returns 0 when no cell is
     * overhead, so a travelling cell brings its rain with it and takes it away
     * again once the core has passed.
     */
    public static float getCoreIntensityAt(double x, double z) {
        float strongest = 0.0f;
        for (StormCellState cell : CELLS.values()) {
            double dx = x - cell.x;
            double dz = z - cell.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            float radius = cell.radius;
            if (dist >= radius) continue;

            float edge = (float) Math.min(1.0, (radius - dist) / (radius * 0.25));
            strongest = Math.max(strongest, cell.intensity * edge * cell.getFade());
        }
        return strongest;
    }
}
