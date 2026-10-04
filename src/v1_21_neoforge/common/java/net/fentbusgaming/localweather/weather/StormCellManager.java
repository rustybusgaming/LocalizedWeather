package net.fentbusgaming.localweather.weather;

import net.fentbusgaming.localweather.config.LocalWeatherConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the moving single-cell thunderstorms that live inside thundery zones.
 */
public final class StormCellManager {

    private static final int ZONE_SIZE = WeatherZoneManager.CHUNKS_PER_ZONE * 16;

    /** Upper bound on simultaneous cells per world, so a stormy day stays cheap. */

    /** How often (in ticks) we look for a thundery zone that has no cell yet. */
    private static final int SPAWN_CHECK_INTERVAL = 100;

    /** Minimum spacing between two cell centres, so cells do not overlap. */
    private static final double MIN_CELL_SPACING = ZONE_SIZE * 1.5;

    public static final double SYNC_DISTANCE = 2048.0;

    /** Cells this far from every player are dropped. */
    private static final double DESPAWN_DISTANCE = 3072.0;

    private static final Map<ResourceKey<Level>, List<StormCell>> WORLD_CELLS = new ConcurrentHashMap<>();

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

        for (ServerLevel world : server.getAllLevels()) {
            List<StormCell> cells = WORLD_CELLS.get(world.dimension());
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

    private static boolean isAbandoned(ServerLevel world, StormCell cell) {
        double despawnSq = DESPAWN_DISTANCE * DESPAWN_DISTANCE;
        for (ServerPlayer player : world.players()) {
            if (cell.squaredDistanceTo(player.getX(), player.getZ()) <= despawnSq) {
                return false;
            }
        }
        return true;
    }

    private static void trySpawnCells(ServerLevel world) {
        List<StormCell> cells = cellsOf(world);
        if (!LocalWeatherConfig.stormCellsEnabled()) return;
        if (cells.size() >= LocalWeatherConfig.stormCellsMax()) return;

        for (ServerPlayer player : world.players()) {
            ChunkPos chunkPos = player.chunkPosition();
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
                    if (cells.size() >= LocalWeatherConfig.stormCellsMax()) return;
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

    public static void syncToPlayers(MinecraftServer server) {
        double syncSq = SYNC_DISTANCE * SYNC_DISTANCE;

        for (ServerLevel world : server.getAllLevels()) {
            List<StormCell> cells = WORLD_CELLS.get(world.dimension());
            if (cells == null || cells.isEmpty()) continue;

            List<StormCell> snapshot;
            synchronized (cells) {
                snapshot = new ArrayList<>(cells);
            }

            for (ServerPlayer player : world.players()) {
                for (StormCell cell : snapshot) {
                    if (cell.squaredDistanceTo(player.getX(), player.getZ()) <= syncSq) {
                        WeatherZoneManager.sync().sendStormCell(player, cell);
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    private static List<StormCell> cellsOf(ServerLevel world) {
        return WORLD_CELLS.computeIfAbsent(world.dimension(),
                k -> Collections.synchronizedList(new ArrayList<>()));
    }

    public static List<StormCell> getCells(ServerLevel world) {
        List<StormCell> cells = WORLD_CELLS.get(world.dimension());
        if (cells == null) return List.of();
        synchronized (cells) {
            return List.copyOf(cells);
        }
    }

    public static StormCell getCellAt(ServerLevel world, double x, double z) {
        List<StormCell> cells = WORLD_CELLS.get(world.dimension());
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

    public static void clearWorld(ResourceKey<Level> worldKey) {
        WORLD_CELLS.remove(worldKey);
    }
}
