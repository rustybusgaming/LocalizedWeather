package net.fentbusgaming.localweather.weather;

import net.fentbusgaming.localweather.config.LocalWeatherConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all WeatherZone instances across all server worlds.
 *
 * Zone grid: each zone covers a 16×16 chunk area (256×256 blocks).
 * Zone coordinates are derived by >> 4 from chunk coordinates.
 */
public class WeatherZoneManager {

    /** Chunks per zone side (16 chunks = 256 blocks). */
    public static final int CHUNKS_PER_ZONE = 16;
    private static final int BIOME_SAMPLES_PER_SIDE = 5;

    // How long a zone stays dry or wet is set in config/localweather.properties
    // and read through LocalWeatherConfig. The defaults mirror vanilla's weather
    // durations, applied per zone rather than per world.

    /**
     * Zones indexed by world key → zone key (packed long of zoneX,zoneZ).
     */
    private static final Map<ResourceKey<Level>, Map<Long, WeatherZone>> WORLD_ZONES =
            new ConcurrentHashMap<>();

    /** Tracks which zones changed this tick and need network sync. */
    private static final Map<ResourceKey<Level>, Set<Long>> DIRTY_ZONES =
            new ConcurrentHashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger("localweather");

    private static final Random RANDOM = new Random();

    // Whether lightning strikes at all, and how often, comes from the config.
    // The default works out to roughly one strike a minute nearby.
    /** Horizontal spread of strikes around the player, in blocks. */
    private static final int LIGHTNING_SPREAD = 48;

    private static final int SYNC_INTERVAL = 20; // every 1 second
    private static final int WIND_SYNC_INTERVAL = 100; // every 5 seconds
    public static final int CLIENT_ZONE_RADIUS = 2;
    private static int syncTimer = 0;
    private static int windSyncTimer = 0;

    /** Last zone sent to each player, used to avoid re-sending an unchanged view. */
    private static final Map<UUID, PlayerZonePosition> PLAYER_ZONE_POSITIONS = new ConcurrentHashMap<>();

    /** Where zone updates are sent. Set by whichever loader is running us. */
    private static WeatherSync sync = WeatherSync.NONE;

    static WeatherSync sync() {
        return sync;
    }

    public static void init(WeatherSync weatherSync) {
        sync = weatherSync;
        LOGGER.info("[LocalWeather] WeatherZoneManager registered. Config: {}", LocalWeatherConfig.summary());
    }

    // -------------------------------------------------------------------------
    // Tick Handler
    // -------------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        WindState.tick();

        for (ServerLevel world : server.getAllLevels()) {
            tickWorld(world);
        }

        // Moving single-cell thunderstorms drift on top of the zone grid.
        StormCellManager.tick(server);

        for (ServerLevel world : server.getAllLevels()) {
            tickLightning(world);
        }

        syncTimer++;
        windSyncTimer++;
        if (syncTimer >= SYNC_INTERVAL) {
            syncTimer = 0;
            boolean sendWind = windSyncTimer >= WIND_SYNC_INTERVAL;
            if (sendWind) {
                windSyncTimer = 0;
            }
            broadcastAllDirtyZones(server, sendWind);
            StormCellManager.syncToPlayers(server);
        }
    }

    private static void tickWorld(ServerLevel world) {
        ResourceKey<Level> key = world.dimension();
        Map<Long, WeatherZone> zones = WORLD_ZONES.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        Set<Long> activeZoneKeys = getActiveZoneKeys(world);

        // Zones outside every player's view do not need simulation or storage.
        zones.keySet().removeIf(zoneKey -> !activeZoneKeys.contains(zoneKey));

        for (long zoneKey : activeZoneKeys) {
            WeatherZone zone = zones.get(zoneKey);
            if (zone == null) continue;

            boolean transitionDone = zone.tickTransition();
            boolean durationExpired = zone.tickDuration();

            if (transitionDone || durationExpired) {
                if (durationExpired) {
                    // Pick new weather for this zone
                    int zoneX = unpackX(zoneKey);
                    int zoneZ = unpackZ(zoneKey);
                    WeatherZone.WeatherType next = pickNewWeather(world, zone, zoneX, zoneZ);
                    int duration = randomDuration(next);
                    zone.setTargetWeather(next);
                    zone.setWeatherDuration(duration);
                }
                markDirty(world.dimension(), zoneKey);
            }
        }
    }

    /**
     * Strike lightning inside thundery zones. Positions are filtered through
     * isRainingAt, so a strike only lands where the rain actually reaches.
     */
    private static void tickLightning(ServerLevel world) {
        for (ServerPlayer player : world.players()) {
            ChunkPos chunkPos = player.chunkPosition();
            WeatherZone zone = getZone(world, chunkPos.x >> 4, chunkPos.z >> 4);
            if (zone == null || zone.getCurrentWeather() != WeatherZone.WeatherType.THUNDER) continue;
            if (!LocalWeatherConfig.lightningEnabled()) continue;
            if (RANDOM.nextInt(LocalWeatherConfig.lightningRarity()) != 0) continue;

            BlockPos around = player.blockPosition().offset(
                    RANDOM.nextInt(LIGHTNING_SPREAD * 2 + 1) - LIGHTNING_SPREAD,
                    0,
                    RANDOM.nextInt(LIGHTNING_SPREAD * 2 + 1) - LIGHTNING_SPREAD);
            BlockPos target = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, around);
            if (!world.isLoaded(target) || !world.isRainingAt(target)) continue;

            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(world);
            if (bolt == null) continue;
            bolt.moveTo(Vec3.atBottomCenterOf(target));
            world.addFreshEntity(bolt);
        }
    }

    private static Set<Long> getActiveZoneKeys(ServerLevel world) {
        Set<Long> activeZoneKeys = new HashSet<>();
        for (ServerPlayer player : world.players()) {
            ChunkPos chunkPos = player.chunkPosition();
            int centerZoneX = chunkPos.x >> 4;
            int centerZoneZ = chunkPos.z >> 4;
            for (int dx = -CLIENT_ZONE_RADIUS; dx <= CLIENT_ZONE_RADIUS; dx++) {
                for (int dz = -CLIENT_ZONE_RADIUS; dz <= CLIENT_ZONE_RADIUS; dz++) {
                    activeZoneKeys.add(pack(centerZoneX + dx, centerZoneZ + dz));
                }
            }
        }
        return activeZoneKeys;
    }

    // -------------------------------------------------------------------------
    // Zone Access
    // -------------------------------------------------------------------------

    public static WeatherZone getOrCreateZoneForPlayer(ServerLevel world, ServerPlayer player) {
        ChunkPos chunkPos = player.chunkPosition();
        int zoneX = chunkPos.x >> 4;
        int zoneZ = chunkPos.z >> 4;
        return getOrCreateZone(world, zoneX, zoneZ);
    }

    public static WeatherZone getOrCreateZone(ServerLevel world, int zoneX, int zoneZ) {
        ResourceKey<Level> key = world.dimension();
        Map<Long, WeatherZone> zones = WORLD_ZONES.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        long packed = pack(zoneX, zoneZ);
        return zones.computeIfAbsent(packed, k -> createZone(world, zoneX, zoneZ));
    }

    public static WeatherZone getZone(ServerLevel world, int zoneX, int zoneZ) {
        ResourceKey<Level> key = world.dimension();
        Map<Long, WeatherZone> zones = WORLD_ZONES.get(key);
        if (zones == null) return null;
        return zones.get(pack(zoneX, zoneZ));
    }

    private static WeatherZone createZone(ServerLevel world, int zoneX, int zoneZ) {
        WeatherZone.WeatherType initial = pickInitialWeather(world, zoneX, zoneZ);
        int duration = randomDuration(initial);
        return new WeatherZone(zoneX, zoneZ, initial, duration);
    }

    // -------------------------------------------------------------------------
    // Weather Selection
    // -------------------------------------------------------------------------

    private static WeatherZone.WeatherType pickInitialWeather(ServerLevel world, int zoneX, int zoneZ) {
        if (RANDOM.nextFloat() < 0.70f) {
            return WeatherZone.WeatherType.CLEAR;
        }
        return applyBiomeRules(world, zoneX, zoneZ, randomWetWeather(0.15f, 0.12f));
    }

    private static WeatherZone.WeatherType pickNewWeather(
            ServerLevel world, WeatherZone zone, int zoneX, int zoneZ) {

        WeatherZone.WeatherType current = zone.getCurrentWeather();

        int[] upwind = WindState.getUpwindZone(zoneX, zoneZ);
        ResourceKey<Level> key = world.dimension();
        Map<Long, WeatherZone> zones = WORLD_ZONES.get(key);
        WeatherZone upwindZone = (zones != null) ? zones.get(pack(upwind[0], upwind[1])) : null;

        if (upwindZone != null && RANDOM.nextFloat() < 0.45f) {
            WeatherZone.WeatherType upwindWeather = upwindZone.getCurrentWeather();
            if (upwindWeather != WeatherZone.WeatherType.CLEAR) {
                return applyBiomeRules(world, zoneX, zoneZ, upwindWeather);
            }
            if (current != WeatherZone.WeatherType.CLEAR) {
                return WeatherZone.WeatherType.CLEAR;
            }
        }

        if (current == WeatherZone.WeatherType.CLEAR) {
            float roll = RANDOM.nextFloat();
            if (roll < 0.20f) {
                return applyBiomeRules(world, zoneX, zoneZ, randomWetWeather(0.20f, 0.10f));
            }
            return WeatherZone.WeatherType.CLEAR;
        } else {
            if (RANDOM.nextFloat() < 0.75f) {
                return WeatherZone.WeatherType.CLEAR;
            }
            return applyBiomeRules(world, zoneX, zoneZ, randomWetWeather(0.20f, 0.15f));
        }
    }

    private static WeatherZone.WeatherType randomWetWeather(float thunderChance, float hailChance) {
        float roll = RANDOM.nextFloat();
        if (roll < thunderChance) {
            return WeatherZone.WeatherType.THUNDER;
        }
        if (roll < thunderChance + hailChance) {
            return WeatherZone.WeatherType.HAIL;
        }
        return WeatherZone.WeatherType.RAIN;
    }

    private static WeatherZone.WeatherType applyBiomeRules(
            ServerLevel world, int zoneX, int zoneZ, WeatherZone.WeatherType requested) {

        if (requested == WeatherZone.WeatherType.CLEAR) {
            return WeatherZone.WeatherType.CLEAR;
        }

        int zoneSize = CHUNKS_PER_ZONE * 16;
        int zoneStartX = zoneX * zoneSize;
        int zoneStartZ = zoneZ * zoneSize;
        Map<WeatherZone.WeatherType, Integer> weatherCounts = new EnumMap<>(WeatherZone.WeatherType.class);

        for (int sampleX = 0; sampleX < BIOME_SAMPLES_PER_SIDE; sampleX++) {
            for (int sampleZ = 0; sampleZ < BIOME_SAMPLES_PER_SIDE; sampleZ++) {
                int blockX = zoneStartX + ((sampleX * 2 + 1) * zoneSize) / (BIOME_SAMPLES_PER_SIDE * 2);
                int blockZ = zoneStartZ + ((sampleZ * 2 + 1) * zoneSize) / (BIOME_SAMPLES_PER_SIDE * 2);
                int surfaceY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ) - 1;
                BlockPos samplePos = new BlockPos(blockX, Math.max(world.getMinBuildHeight(), surfaceY), blockZ);
                WeatherZone.WeatherType resolved = BiomeWeatherRules.resolveWeather(world.getBiome(samplePos), requested);
                weatherCounts.merge(resolved, 1, Integer::sum);
            }
        }

        return weatherCounts.entrySet().stream()
                .max(Comparator.<Map.Entry<WeatherZone.WeatherType, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(entry -> entry.getKey() == requested))
                .map(Map.Entry::getKey)
                .orElse(requested);
    }

    private static int randomDuration(WeatherZone.WeatherType type) {
        if (type == WeatherZone.WeatherType.CLEAR) {
            return spread(LocalWeatherConfig.clearTicksMin(), LocalWeatherConfig.clearTicksMax());
        }
        return spread(LocalWeatherConfig.wetTicksMin(), LocalWeatherConfig.wetTicksMax());
    }

    /**
     * A tick count somewhere in [min, max).
     *
     * The bounds come from the config, where a max equal to its min is a
     * legitimate way to ask for a fixed span — and {@code Random.nextInt}
     * rejects a bound of zero, so that case returns the minimum instead.
     */
    private static int spread(int min, int max) {
        return max > min ? min + RANDOM.nextInt(max - min) : min;
    }

    // -------------------------------------------------------------------------
    // Network Sync
    // -------------------------------------------------------------------------

    public static void markDirty(ResourceKey<Level> worldKey, long zoneKey) {
        DIRTY_ZONES.computeIfAbsent(worldKey, k -> Collections.synchronizedSet(new HashSet<>()))
                .add(zoneKey);
    }

    private static void broadcastAllDirtyZones(MinecraftServer server, boolean sendWind) {
        for (ServerLevel world : server.getAllLevels()) {
            ResourceKey<Level> worldKey = world.dimension();
            Set<Long> dirty = DIRTY_ZONES.remove(worldKey);
            Map<Long, WeatherZone> zones = WORLD_ZONES.get(worldKey);
            if (dirty == null || zones == null) continue;

            for (long zoneKey : dirty) {
                WeatherZone zone = zones.get(zoneKey);
                if (zone == null) continue;
                sendZoneToNearbyPlayers(world, zone);
            }
        }

        syncPlayersZones(server, sendWind);
    }

    private static void sendZoneToNearbyPlayers(ServerLevel world, WeatherZone zone) {
        for (ServerPlayer player : world.players()) {
            ChunkPos chunkPos = player.chunkPosition();
            int pZoneX = chunkPos.x >> 4;
            int pZoneZ = chunkPos.z >> 4;

            if (Math.abs(pZoneX - zone.getZoneX()) <= CLIENT_ZONE_RADIUS
                    && Math.abs(pZoneZ - zone.getZoneZ()) <= CLIENT_ZONE_RADIUS) {
                sync.sendZone(player, zone);
            }
        }
    }

    private static void syncPlayersZones(MinecraftServer server, boolean sendWind) {
        double windX = WindState.getWindDirX();
        double windZ = WindState.getWindDirZ();
        Set<UUID> onlinePlayers = new HashSet<>();

        for (ServerLevel world : server.getAllLevels()) {
            for (ServerPlayer player : world.players()) {
                UUID playerId = player.getUUID();
                onlinePlayers.add(playerId);
                ChunkPos chunkPos = player.chunkPosition();
                int centerZoneX = chunkPos.x >> 4;
                int centerZoneZ = chunkPos.z >> 4;
                PlayerZonePosition position = new PlayerZonePosition(world.dimension(), centerZoneX, centerZoneZ);
                boolean enteredNewZone = !position.equals(PLAYER_ZONE_POSITIONS.put(playerId, position));

                if (sendWind || enteredNewZone) {
                    sync.sendWind(player, windX, windZ);
                }

                if (enteredNewZone) {
                    sendNearbyZones(player, world, centerZoneX, centerZoneZ);
                }
            }
        }

        PLAYER_ZONE_POSITIONS.keySet().retainAll(onlinePlayers);
    }

    private static void sendNearbyZones(ServerPlayer player, ServerLevel world, int centerZoneX, int centerZoneZ) {
        for (int dx = -CLIENT_ZONE_RADIUS; dx <= CLIENT_ZONE_RADIUS; dx++) {
            for (int dz = -CLIENT_ZONE_RADIUS; dz <= CLIENT_ZONE_RADIUS; dz++) {
                WeatherZone zone = getOrCreateZone(world, centerZoneX + dx, centerZoneZ + dz);
                sync.sendZone(player, zone);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) (packed & 0xFFFFFFFFL);
    }

    public static void clearWorld(ResourceKey<Level> worldKey) {
        WORLD_ZONES.remove(worldKey);
        DIRTY_ZONES.remove(worldKey);
        StormCellManager.clearWorld(worldKey);
        PLAYER_ZONE_POSITIONS.entrySet().removeIf(entry -> entry.getValue().worldKey().equals(worldKey));
    }

    public static void forceWeatherAt(ServerLevel world, int zoneX, int zoneZ, WeatherZone.WeatherType weather, int duration) {
        WeatherZone zone = getOrCreateZone(world, zoneX, zoneZ);
        zone.forceWeather(weather, duration);
        long zoneKey = pack(zoneX, zoneZ);
        markDirty(world.dimension(), zoneKey);

        for (ServerPlayer player : world.players()) {
            ChunkPos chunkPos = player.chunkPosition();
            int pZoneX = chunkPos.x >> 4;
            int pZoneZ = chunkPos.z >> 4;
            if (Math.abs(pZoneX - zoneX) <= CLIENT_ZONE_RADIUS
                    && Math.abs(pZoneZ - zoneZ) <= CLIENT_ZONE_RADIUS) {
                sync.sendZone(player, zone);
            }
        }
    }

    private record PlayerZonePosition(ResourceKey<Level> worldKey, int zoneX, int zoneZ) {}
}
