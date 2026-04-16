package net.fentbusgaming.localweather.weather;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fentbusgaming.localweather.LocalWeatherMod;
import net.fentbusgaming.localweather.network.WeatherPackets;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

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
    private static final Map<RegistryKey<World>, Map<Long, WeatherZone>> WORLD_ZONES =
            new ConcurrentHashMap<>();

    /** Tracks which zones changed this tick and need network sync. */
    private static final Map<RegistryKey<World>, Set<Long>> DIRTY_ZONES =
            new ConcurrentHashMap<>();

    private static final Random RANDOM = new Random();

    // How often (in ticks) we re-evaluate zone weather (besides duration expiry).
    // This also controls how often we broadcast zone weather to all nearby players.
    private static final int SYNC_INTERVAL = 20; // every 1 second
    private static int syncTimer = 0;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(WeatherZoneManager::onServerTick);
        LocalWeatherMod.LOGGER.info("[LocalWeather] WeatherZoneManager registered.");
    }

    // -------------------------------------------------------------------------
    // Tick Handler
    // -------------------------------------------------------------------------

    private static void onServerTick(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            tickWorld(world);
        }

        syncTimer++;
        if (syncTimer >= SYNC_INTERVAL) {
            syncTimer = 0;
            broadcastAllDirtyZones(server);
        }
    }

    private static void tickWorld(ServerWorld world) {
        RegistryKey<World> key = world.getRegistryKey();
        Map<Long, WeatherZone> zones = WORLD_ZONES.computeIfAbsent(key, k -> new ConcurrentHashMap<>());

        // Tick all loaded zones
        for (Map.Entry<Long, WeatherZone> entry : zones.entrySet()) {
            WeatherZone zone = entry.getValue();

            boolean transitionDone = zone.tickTransition();
            boolean durationExpired = zone.tickDuration();

            if (transitionDone || durationExpired) {
                if (durationExpired) {
                    // Pick new weather for this zone
                    int zoneX = unpackX(entry.getKey());
                    int zoneZ = unpackZ(entry.getKey());
                    WeatherZone.WeatherType next = pickNewWeather(world, zone, zoneX, zoneZ);
                    int duration = randomDuration(next);
                    zone.setTargetWeather(next);
                    zone.setWeatherDuration(duration);
                }
                markDirty(world.getRegistryKey(), entry.getKey());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Zone Access
    // -------------------------------------------------------------------------

    /**
     * Get (or lazily create) the weather zone for the chunk a player is standing in.
     */
    public static WeatherZone getOrCreateZoneForPlayer(ServerWorld world, ServerPlayerEntity player) {
        ChunkPos chunkPos = player.getChunkPos();
        int zoneX = chunkPos.x >> 4;
        int zoneZ = chunkPos.z >> 4;
        return getOrCreateZone(world, zoneX, zoneZ);
    }

    public static WeatherZone getOrCreateZone(ServerWorld world, int zoneX, int zoneZ) {
        RegistryKey<World> key = world.getRegistryKey();
        Map<Long, WeatherZone> zones = WORLD_ZONES.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        long packed = pack(zoneX, zoneZ);
        return zones.computeIfAbsent(packed, k -> createZone(world, zoneX, zoneZ));
    }

    private static WeatherZone createZone(ServerWorld world, int zoneX, int zoneZ) {
        WeatherZone.WeatherType initial = pickInitialWeather(world, zoneX, zoneZ);
        int duration = randomDuration(initial);
        return new WeatherZone(zoneX, zoneZ, initial, duration);
    }

    // -------------------------------------------------------------------------
    // Weather Selection
    // -------------------------------------------------------------------------

    private static WeatherZone.WeatherType pickInitialWeather(ServerWorld world, int zoneX, int zoneZ) {
        // ~70% clear on first load to avoid a rainy world on first join
        if (RANDOM.nextFloat() < 0.70f) {
            return WeatherZone.WeatherType.CLEAR;
        }
        WeatherZone.WeatherType wet = RANDOM.nextFloat() < 0.15f
                ? WeatherZone.WeatherType.THUNDER
                : WeatherZone.WeatherType.RAIN;
        return applyBiomeRules(world, zoneX, zoneZ, wet);
    }

    private static WeatherZone.WeatherType pickNewWeather(
            ServerWorld world, WeatherZone zone, int zoneX, int zoneZ) {

        WeatherZone.WeatherType current = zone.getCurrentWeather();

        if (current == WeatherZone.WeatherType.CLEAR) {
            // Was clear — chance of rain/thunder
            float roll = RANDOM.nextFloat();
            if (roll < 0.20f) {
                WeatherZone.WeatherType wet = roll < 0.04f
                        ? WeatherZone.WeatherType.THUNDER
                        : WeatherZone.WeatherType.RAIN;
                return applyBiomeRules(world, zoneX, zoneZ, wet);
            }
            return WeatherZone.WeatherType.CLEAR;
        } else {
            // Was wet — high chance of clearing
            if (RANDOM.nextFloat() < 0.75f) {
                return WeatherZone.WeatherType.CLEAR;
            }
            // Stay wet or escalate to thunder
            WeatherZone.WeatherType wet = RANDOM.nextFloat() < 0.20f
                    ? WeatherZone.WeatherType.THUNDER
                    : WeatherZone.WeatherType.RAIN;
            return applyBiomeRules(world, zoneX, zoneZ, wet);
        }
    }

    /**
     * Sample the biome at the centre of the zone to apply biome weather rules.
     */
    private static WeatherZone.WeatherType applyBiomeRules(
            ServerWorld world, int zoneX, int zoneZ, WeatherZone.WeatherType requested) {

        // Centre block of the zone (at sea level y=64)
        int blockX = (zoneX * CHUNKS_PER_ZONE * 16) + (CHUNKS_PER_ZONE * 8);
        int blockZ = (zoneZ * CHUNKS_PER_ZONE * 16) + (CHUNKS_PER_ZONE * 8);
        BlockPos pos = new BlockPos(blockX, 64, blockZ);

        RegistryEntry<Biome> biome = world.getBiome(pos);
        return BiomeWeatherRules.resolveWeather(biome, requested);
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

    public static void markDirty(RegistryKey<World> worldKey, long zoneKey) {
        DIRTY_ZONES.computeIfAbsent(worldKey, k -> Collections.synchronizedSet(new HashSet<>()))
                .add(zoneKey);
    }

    private static void broadcastAllDirtyZones(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            RegistryKey<World> worldKey = world.getRegistryKey();
            Set<Long> dirty = DIRTY_ZONES.remove(worldKey);
            Map<Long, WeatherZone> zones = WORLD_ZONES.get(worldKey);
            if (dirty == null || zones == null) continue;

            for (long zoneKey : dirty) {
                WeatherZone zone = zones.get(zoneKey);
                if (zone == null) continue;
                sendZoneToNearbyPlayers(world, zone);
            }
        }

        // Also periodically push current zone weather to each player on join or if state changed
        syncPlayersZones(server);
    }

    /**
     * Send zone weather update to all players whose current zone matches this zone.
     */
    private static void sendZoneToNearbyPlayers(ServerWorld world, WeatherZone zone) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            ChunkPos chunkPos = player.getChunkPos();
            int pZoneX = chunkPos.x >> 4;
            int pZoneZ = chunkPos.z >> 4;

            // Send to players in this zone and adjacent zones (so transitions look smooth at borders)
            if (Math.abs(pZoneX - zone.getZoneX()) <= 1 && Math.abs(pZoneZ - zone.getZoneZ()) <= 1) {
                WeatherPackets.sendWeatherUpdate(player, zone);
            }
        }
    }

    /**
     * Every sync interval, push each player their current zone and all nearby zones.
     * Sends a 5×5 grid (2 zones out) so the client can detect distant storms
     * and render approaching cloud/sky/fog effects.
     */
    private static void syncPlayersZones(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                ChunkPos chunkPos = player.getChunkPos();
                int centerZoneX = chunkPos.x >> 4;
                int centerZoneZ = chunkPos.z >> 4;

                // Send current zone + 5x5 grid (2 zones in each direction)
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        WeatherZone zone = getOrCreateZone(world, centerZoneX + dx, centerZoneZ + dz);
                        WeatherPackets.sendWeatherUpdate(player, zone);
                    }
                }
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
    public static void clearWorld(RegistryKey<World> worldKey) {
        WORLD_ZONES.remove(worldKey);
        DIRTY_ZONES.remove(worldKey);
    }
}
