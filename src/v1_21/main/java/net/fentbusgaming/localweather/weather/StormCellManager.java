package net.fentbusgaming.localweather.weather;

import net.fentbusgaming.localweather.network.WeatherPackets;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the moving single-cell thunderstorms that live inside thundery zones.
 *
 * Zones stay responsible for ambient weather; a cell is the travelling core the
 * client renders a rain wall and rain bands around. Cells are simulated and
 * synced far past the zone grid so a storm that is still several zones away is
 * already drawn on the horizon.
 */
public final class StormCellManager {

    private static final int ZONE_SIZE = WeatherZoneManager.CHUNKS_PER_ZONE * 16;

    /** Upper bound on simultaneous cells per world, so a stormy day stays cheap. */
    private static final int MAX_CELLS_PER_WORLD = 4;

    /** How often (in ticks) we look for a thundery zone that has no cell yet. */
    private static final int SPAWN_CHECK_INTERVAL = 100;

    /** Minimum spacing between two cell centres, so cells do not overlap. */
    private static final double MIN_CELL_SPACING = ZONE_SIZE * 1.5;

    /**
     * Cells are sent to players well beyond the synced zone grid: a distant
     * thunderstorm has to be visible long before its zone reaches the player.
     */
    public static final double SYNC_DISTANCE = 2048.0;

    /** Cells this far from every player are dropped. */
    private static final double DESPAWN_DISTANCE = 3072.0;

    private static final Map<RegistryKey<World>, List<StormCell>> WORLD_CELLS = new ConcurrentHashMap<>();

    private static final Random RANDOM = new Random();

    private static int nextCellId = 1;
    private static int spawnTimer = 0;

    private StormCellManager() {}

    // -------------------------------------------------------------------------
    // Simulation
    // -------------------------------------------------------------------------

    /** Move every cell along the wind, retire dead ones, and seed new ones. */
    public static void tick(MinecraftServer server) {
        double windX = WindState.getWindDirX();
        double windZ = WindState.getWindDirZ();

        spawnTimer++;
        boolean trySpawn = spawnTimer >= SPAWN_CHECK_INTERVAL;
        if (trySpawn) {
            spawnTimer = 0;
        }

        for (ServerWorld world : server.getWorlds()) {
            List<StormCell> cells = WORLD_CELLS.get(world.getRegistryKey());
            if (cells != null && !cells.isEmpty()) {
                synchronized (cells) {
                    for (StormCell cell : cells) {
                        cell.tick(windX, windZ);
                    }
                    cells.removeIf(cell -> cell.isExpired() || isAbandoned(world, cell));
                }
            }
            if (trySpawn) {
                trySpawnCells(world);
            }
        }
    }

    private static boolean isAbandoned(ServerWorld world, StormCell cell) {
        double despawnSq = DESPAWN_DISTANCE * DESPAWN_DISTANCE;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (cell.squaredDistanceTo(player.getX(), player.getZ()) <= despawnSq) {
                return false;
            }
        }
        return true;
    }

    /**
     * Give every thundery zone near a player a storm cell, unless one is already
     * close enough to cover it.
     */
    private static void trySpawnCells(ServerWorld world) {
        List<StormCell> cells = cellsOf(world);
        if (cells.size() >= MAX_CELLS_PER_WORLD) return;

        for (ServerPlayerEntity player : world.getPlayers()) {
            ChunkPos chunkPos = player.getChunkPos();
            int centerZoneX = chunkPos.x >> 4;
            int centerZoneZ = chunkPos.z >> 4;

            for (int dx = -WeatherZoneManager.CLIENT_ZONE_RADIUS; dx <= WeatherZoneManager.CLIENT_ZONE_RADIUS; dx++) {
                for (int dz = -WeatherZoneManager.CLIENT_ZONE_RADIUS; dz <= WeatherZoneManager.CLIENT_ZONE_RADIUS; dz++) {
                    int zoneX = centerZoneX + dx;
                    int zoneZ = centerZoneZ + dz;
                    if (!isThundery(WeatherZoneManager.getZone(world, zoneX, zoneZ))) continue;

                    double spawnX = (zoneX + 0.15 + RANDOM.nextDouble() * 0.7) * ZONE_SIZE;
                    double spawnZ = (zoneZ + 0.15 + RANDOM.nextDouble() * 0.7) * ZONE_SIZE;
                    if (hasCellNear(cells, spawnX, spawnZ)) continue;

                    StormCell cell = StormCell.create(nextCellId++, spawnX, spawnZ,
                            WindState.getWindDirX(), WindState.getWindDirZ(), RANDOM);
                    synchronized (cells) {
                        cells.add(cell);
                    }
                    if (cells.size() >= MAX_CELLS_PER_WORLD) return;
                }
            }
        }
    }

    private static boolean isThundery(WeatherZone zone) {
        return zone != null
                && (zone.getCurrentWeather() == WeatherZone.WeatherType.THUNDER
                    || zone.getTargetWeather() == WeatherZone.WeatherType.THUNDER);
    }

    private static boolean hasCellNear(List<StormCell> cells, double x, double z) {
        double spacingSq = MIN_CELL_SPACING * MIN_CELL_SPACING;
        synchronized (cells) {
            for (StormCell cell : cells) {
                if (cell.squaredDistanceTo(x, z) < spacingSq) {
                    return true;
                }
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Network sync
    // -------------------------------------------------------------------------

    /**
     * Push every nearby cell to every player. Cells are re-sent on each sync so
     * the client can retire a cell it stops hearing about — no removal packet.
     */
    public static void syncToPlayers(MinecraftServer server) {
        double syncSq = SYNC_DISTANCE * SYNC_DISTANCE;

        for (ServerWorld world : server.getWorlds()) {
            List<StormCell> cells = WORLD_CELLS.get(world.getRegistryKey());
            if (cells == null || cells.isEmpty()) continue;

            List<StormCell> snapshot;
            synchronized (cells) {
                snapshot = new ArrayList<>(cells);
            }

            for (ServerPlayerEntity player : world.getPlayers()) {
                for (StormCell cell : snapshot) {
                    if (cell.squaredDistanceTo(player.getX(), player.getZ()) <= syncSq) {
                        WeatherPackets.sendStormCell(player, cell);
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    private static List<StormCell> cellsOf(ServerWorld world) {
        return WORLD_CELLS.computeIfAbsent(world.getRegistryKey(),
                k -> Collections.synchronizedList(new ArrayList<>()));
    }

    /** Immutable snapshot of the cells currently alive in a world. */
    public static List<StormCell> getCells(ServerWorld world) {
        List<StormCell> cells = WORLD_CELLS.get(world.getRegistryKey());
        if (cells == null) return List.of();
        synchronized (cells) {
            return List.copyOf(cells);
        }
    }

    /** The strongest cell whose core covers the position, or null. */
    public static StormCell getCellAt(ServerWorld world, double x, double z) {
        List<StormCell> cells = WORLD_CELLS.get(world.getRegistryKey());
        if (cells == null) return null;

        StormCell best = null;
        synchronized (cells) {
            for (StormCell cell : cells) {
                if (cell.contains(x, z) && (best == null || cell.getIntensity() > best.getIntensity())) {
                    best = cell;
                }
            }
        }
        return best;
    }

    public static void clearWorld(RegistryKey<World> worldKey) {
        WORLD_CELLS.remove(worldKey);
    }
}
