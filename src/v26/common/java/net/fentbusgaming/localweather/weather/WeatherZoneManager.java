package net.fentbusgaming.localweather.weather;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

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

    /**
     * Weather duration ranges: min/max ticks (20 ticks = 1 second).
     * These mirror vanilla's weather duration but are per-zone.
     */
    private static final int MIN_CLEAR_TICKS  = 12000;  // ~10 min
    private static final int MAX_CLEAR_TICKS  = 180000; // ~2.5 hours
    private static final int MIN_WET_TICKS    = 12000;
    private static final int MAX_WET_TICKS    = 24000;

    /**
     * Zones indexed by world key → zone key (packed long of zoneX,zoneZ).
     */
    private static final Map<ResourceKey<Level>, Map<Long, WeatherZone>> WORLD_ZONES =
            new ConcurrentHashMap<>();

    /** Tracks which zones changed this tick and need network sync. */
    private static final Map<ResourceKey<Level>, Set<Long>> DIRTY_ZONES =
            new ConcurrentHashMap<>();

    /**
     * The simulation is shared between loaders, so it logs under its own name
     * rather than through either platform's entry point.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("localweather");

    private static final Random RANDOM = new Random();

    /**
     * Odds per player per tick of a lightning strike while standing in a thunder
     * zone — roughly one strike a minute nearby. Vanilla drives lightning from
     * its global thunder state, which this mod suppresses, so thunderstorms
     * struck nothing at all until now.
     */
    private static final int LIGHTNING_CHANCE = 1200;
    /** Horizontal spread of strikes around the player, in blocks. */
    private static final int LIGHTNING_SPREAD = 48;

    /**
     * Looked up from the registry rather than named directly: 26.1 holds the
     * constant on EntityType and 26.2 moved it to EntityTypes, so neither name
     * compiles against both targets. The registry and this accessor are the
     * same on each.
     */
    private static final EntityType<?> LIGHTNING_TYPE = BuiltInRegistries.ENTITY_TYPE
            .getValue(Identifier.fromNamespaceAndPath("minecraft", "lightning_bolt"));

    // How often (in ticks) we re-evaluate zone weather (besides duration expiry).
    // This controls how often we broadcast changed zone weather to nearby players.
    private static final int SYNC_INTERVAL = 20; // every 1 second
    private static final int WIND_SYNC_INTERVAL = 100; // every 5 seconds
    public static final int CLIENT_ZONE_RADIUS = 2;
    private static int syncTimer = 0;
    private static int windSyncTimer = 0;

    /** Last zone sent to each player, used to avoid re-sending an unchanged view. */
    private static final Map<UUID, PlayerZonePosition> PLAYER_ZONE_POSITIONS = new ConcurrentHashMap<>();

    /** Where zone updates are sent. Set by whichever loader is running us. */
    private static WeatherSync sync = WeatherSync.NONE;

    /**
     * Hand the simulation its networking. The loader is responsible for calling
     * {@link #tick(MinecraftServer)} from its own server tick event — Fabric and
     * NeoForge spell that event differently, and it is the only part of this
     * class either of them has to supply.
     */
    static WeatherSync sync() {
        return sync;
    }

    public static void init(WeatherSync weatherSync) {
        sync = weatherSync;
        LOGGER.info("[LocalWeather] WeatherZoneManager registered.");
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
            WeatherZone zone = getZone(world, chunkPos.x() >> 4, chunkPos.z() >> 4);
            if (zone == null || zone.getCurrentWeather() != WeatherZone.WeatherType.THUNDER) continue;
            if (RANDOM.nextInt(LIGHTNING_CHANCE) != 0) continue;

            BlockPos around = player.blockPosition().offset(
                    RANDOM.nextInt(LIGHTNING_SPREAD * 2 + 1) - LIGHTNING_SPREAD,
                    0,
                    RANDOM.nextInt(LIGHTNING_SPREAD * 2 + 1) - LIGHTNING_SPREAD);
            BlockPos target = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, around);
            if (!world.isLoaded(target) || !world.isRainingAt(target)) continue;

            if (!(LIGHTNING_TYPE.create(world, EntitySpawnReason.EVENT) instanceof LightningBolt bolt)) continue;
            bolt.snapTo(Vec3.atBottomCenterOf(target));
            world.addFreshEntity(bolt);
        }
    }

    private static Set<Long> getActiveZoneKeys(ServerLevel world) {
        Set<Long> activeZoneKeys = new HashSet<>();
        for (ServerPlayer player : world.players()) {
            ChunkPos chunkPos = player.chunkPosition();
            int centerZoneX = chunkPos.x() >> 4;
            int centerZoneZ = chunkPos.z() >> 4;
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

    /**
     * Get (or lazily create) the weather zone for the chunk a player is standing in.
     */
    public static WeatherZone getOrCreateZoneForPlayer(ServerLevel world, ServerPlayer player) {
        ChunkPos chunkPos = player.chunkPosition();
        int zoneX = chunkPos.x() >> 4;
        int zoneZ = chunkPos.z() >> 4;
        return getOrCreateZone(world, zoneX, zoneZ);
    }

    public static WeatherZone getOrCreateZone(ServerLevel world, int zoneX, int zoneZ) {
        ResourceKey<Level> key = world.dimension();
        Map<Long, WeatherZone> zones = WORLD_ZONES.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        long packed = pack(zoneX, zoneZ);
        return zones.computeIfAbsent(packed, k -> createZone(world, zoneX, zoneZ));
    }

    /**
     * Get an existing zone without creating it. Returns null if the zone hasn't been loaded.
     */
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
        // ~70% clear on first load to avoid a rainy world on first join
        if (RANDOM.nextFloat() < 0.70f) {
            return WeatherZone.WeatherType.CLEAR;
        }
        return applyBiomeRules(world, zoneX, zoneZ, randomWetWeather(0.15f, 0.12f));
    }

    private static WeatherZone.WeatherType pickNewWeather(
            ServerLevel world, WeatherZone zone, int zoneX, int zoneZ) {

        WeatherZone.WeatherType current = zone.getCurrentWeather();

        // Check the upwind neighbor — weather fronts drift with the wind
        int[] upwind = WindState.getUpwindZone(zoneX, zoneZ);
        ResourceKey<Level> key = world.dimension();
        Map<Long, WeatherZone> zones = WORLD_ZONES.get(key);
        WeatherZone upwindZone = (zones != null) ? zones.get(pack(upwind[0], upwind[1])) : null;

        // 45% chance to inherit upwind neighbor's weather (creates drifting fronts)
        if (upwindZone != null && RANDOM.nextFloat() < 0.45f) {
            WeatherZone.WeatherType upwindWeather = upwindZone.getCurrentWeather();
            if (upwindWeather != WeatherZone.WeatherType.CLEAR) {
                return applyBiomeRules(world, zoneX, zoneZ, upwindWeather);
            }
            // Upwind is clear — higher chance of clearing
            if (current != WeatherZone.WeatherType.CLEAR) {
                return WeatherZone.WeatherType.CLEAR;
            }
        }

        if (current == WeatherZone.WeatherType.CLEAR) {
            // Was clear — chance of rain/thunder
            float roll = RANDOM.nextFloat();
            if (roll < 0.20f) {
                return applyBiomeRules(world, zoneX, zoneZ, randomWetWeather(0.20f, 0.10f));
            }
            return WeatherZone.WeatherType.CLEAR;
        } else {
            // Was wet — high chance of clearing
            if (RANDOM.nextFloat() < 0.75f) {
                return WeatherZone.WeatherType.CLEAR;
            }
            // Stay wet or escalate to thunder
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

    /**
     * Resolve weather from an evenly distributed surface sample of the whole zone.
     * This prevents a small desert, mountain, or snowy patch at the centre from
     * determining weather for all 256 by 256 blocks.
     */
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
                BlockPos samplePos = new BlockPos(blockX, Math.max(world.getMinY(), surfaceY), blockZ);
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
            return MIN_CLEAR_TICKS + RANDOM.nextInt(MAX_CLEAR_TICKS - MIN_CLEAR_TICKS);
        }
        return MIN_WET_TICKS + RANDOM.nextInt(MAX_WET_TICKS - MIN_WET_TICKS);
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

    /**
     * Send zone weather update to all players whose current zone matches this zone.
     */
    private static void sendZoneToNearbyPlayers(ServerLevel world, WeatherZone zone) {
        for (ServerPlayer player : world.players()) {
            ChunkPos chunkPos = player.chunkPosition();
            int pZoneX = chunkPos.x() >> 4;
            int pZoneZ = chunkPos.z() >> 4;

            // Match the radius the client is sent on arrival and keeps cached. At
            // radius 1 a zone further out that changed weather was never pushed,
            // so distant storms stayed stale until the player crossed a zone
            // boundary — audible and lit, but with no clouds drawn.
            if (Math.abs(pZoneX - zone.getZoneX()) <= CLIENT_ZONE_RADIUS
                    && Math.abs(pZoneZ - zone.getZoneZ()) <= CLIENT_ZONE_RADIUS) {
                sync.sendZone(player, zone);
            }
        }
    }

    /**
     * Every sync interval, push each player their current zone and all nearby zones.
     * Sends a 5×5 grid (2 zones out) so the client can detect distant storms
     * and render approaching cloud/sky/fog effects.
     */
    private static void syncPlayersZones(MinecraftServer server, boolean sendWind) {
        double windX = WindState.getWindDirX();
        double windZ = WindState.getWindDirZ();
        Set<UUID> onlinePlayers = new HashSet<>();

        for (ServerLevel world : server.getAllLevels()) {
            for (ServerPlayer player : world.players()) {
                UUID playerId = player.getUUID();
                onlinePlayers.add(playerId);
                ChunkPos chunkPos = player.chunkPosition();
                int centerZoneX = chunkPos.x() >> 4;
                int centerZoneZ = chunkPos.z() >> 4;
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

    /** Clear all zones for a world (e.g. on world unload). */
    public static void clearWorld(ResourceKey<Level> worldKey) {
        WORLD_ZONES.remove(worldKey);
        DIRTY_ZONES.remove(worldKey);
        StormCellManager.clearWorld(worldKey);
        PLAYER_ZONE_POSITIONS.entrySet().removeIf(entry -> entry.getValue().worldKey().equals(worldKey));
    }

    /**
     * Force a zone to a specific weather state immediately.
     */
    public static void forceWeatherAt(ServerLevel world, int zoneX, int zoneZ, WeatherZone.WeatherType weather, int duration) {
        WeatherZone zone = getOrCreateZone(world, zoneX, zoneZ);
        zone.forceWeather(weather, duration);
        long zoneKey = pack(zoneX, zoneZ);
        markDirty(world.dimension(), zoneKey);

        for (ServerPlayer player : world.players()) {
            ChunkPos chunkPos = player.chunkPosition();
            int pZoneX = chunkPos.x() >> 4;
            int pZoneZ = chunkPos.z() >> 4;
            if (Math.abs(pZoneX - zoneX) <= CLIENT_ZONE_RADIUS
                    && Math.abs(pZoneZ - zoneZ) <= CLIENT_ZONE_RADIUS) {
                sync.sendZone(player, zone);
            }
        }
    }

    private record PlayerZonePosition(ResourceKey<Level> worldKey, int zoneX, int zoneZ) {}
}
