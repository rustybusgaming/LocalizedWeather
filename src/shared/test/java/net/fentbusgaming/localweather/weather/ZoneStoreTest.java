package net.fentbusgaming.localweather.weather;

import net.fentbusgaming.localweather.weather.WeatherZone.WeatherType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ZoneStoreTest {

    @TempDir
    Path world;

    @AfterEach
    void resetWind() {
        WindState.reset();
    }

    private static Map<String, Map<Long, WeatherZone>> overworld(long key, WeatherZone zone) {
        Map<Long, WeatherZone> zones = new HashMap<>();
        zones.put(key, zone);
        Map<String, Map<Long, WeatherZone>> byDim = new HashMap<>();
        byDim.put("minecraft:overworld", zones);
        return byDim;
    }

    private void write(String text) throws IOException {
        Files.writeString(world.resolve(ZoneStore.FILE_NAME), text, StandardCharsets.UTF_8);
    }

    @Test
    void stormComesBackAfterARestart() {
        WeatherZone storm = new WeatherZone(3, -7, WeatherType.THUNDER, 4_321);
        assertEquals(1, ZoneStore.save(world, overworld(42L, storm), 10_000));

        ZoneStore.Snapshot snap = ZoneStore.load(world);

        assertEquals(10_000, snap.gameTime);
        WeatherZone back = snap.zones.get("minecraft:overworld").get(42L);
        assertNotNull(back, "the zone was not saved");
        assertEquals(3, back.getZoneX());
        assertEquals(-7, back.getZoneZ());
        assertEquals(WeatherType.THUNDER, back.getCurrentWeather());
        assertEquals(4_321, back.getWeatherDuration());
    }

    @Test
    void halfFinishedTransitionIsKept() {
        WeatherZone zone = new WeatherZone(0, 0, WeatherType.CLEAR, 500);
        zone.setTargetWeather(WeatherType.RAIN);
        for (int i = 0; i < 100; i++) zone.tickTransition();

        ZoneStore.save(world, overworld(1L, zone), 0);
        WeatherZone back = ZoneStore.load(world).zones.get("minecraft:overworld").get(1L);

        assertEquals(WeatherType.CLEAR, back.getCurrentWeather());
        assertEquals(WeatherType.RAIN, back.getTargetWeather());
        assertEquals(zone.getTransitionProgress(), back.getTransitionProgress(), 1e-6);
    }

    @Test
    void zoneInUseAtSaveIsLeftAtTheSaveTime() {
        WeatherZone zone = new WeatherZone(0, 0, WeatherType.RAIN, 5_000);
        ZoneStore.save(world, overworld(1L, zone), 8_000);

        WeatherZone back = ZoneStore.load(world).zones.get("minecraft:overworld").get(1L);

        assertTrue(back.isDormant(), "a restored zone should wait for a player to wake it");
        assertEquals(8_000, back.getDormantSince());
    }

    @Test
    void zoneAlreadyDormantKeepsWhenItWasLeft() {
        WeatherZone zone = new WeatherZone(0, 0, WeatherType.RAIN, 5_000);
        zone.markDormant(2_000);
        ZoneStore.save(world, overworld(1L, zone), 8_000);

        WeatherZone back = ZoneStore.load(world).zones.get("minecraft:overworld").get(1L);

        assertEquals(2_000, back.getDormantSince());
    }

    @Test
    void timeAwayAfterARestartIsOnlyGameTime() {
        // Saved with 5000 ticks of rain left, the zone in use. The server is
        // off for an hour of real time, which is no game time at all; the
        // player then walks back in 1000 ticks after the restart.
        WeatherZone zone = new WeatherZone(0, 0, WeatherType.RAIN, 5_000);
        ZoneStore.save(world, overworld(1L, zone), 8_000);
        Map<Long, WeatherZone> zones = ZoneStore.load(world).zones.get("minecraft:overworld");

        ZoneRetention.reconcile(zones, Set.of(1L), 9_000);

        WeatherZone back = zones.get(1L);
        assertFalse(back.isDormant());
        assertEquals(WeatherType.RAIN, back.getCurrentWeather());
        assertEquals(4_000, back.getWeatherDuration());
    }

    @Test
    void windIsRestored() {
        WindState.restore(1.25, 2.5, 777);
        ZoneStore.save(world, new HashMap<>(), 0);
        WindState.reset();

        ZoneStore.Snapshot snap = ZoneStore.load(world);

        assertTrue(snap.hasWind);
        assertEquals(1.25, snap.windAngle, 1e-12);
        assertEquals(2.5, snap.windTarget, 1e-12);
        assertEquals(777, snap.windTicksUntilShift);
    }

    @Test
    void dimensionsAreKeptApart() {
        Map<String, Map<Long, WeatherZone>> byDim = overworld(1L, new WeatherZone(0, 0, WeatherType.RAIN, 100));
        Map<Long, WeatherZone> modded = new HashMap<>();
        modded.put(1L, new WeatherZone(0, 0, WeatherType.SNOW, 200));
        byDim.put("somemod:frozen_lands", modded);

        ZoneStore.Snapshot snap = ZoneStore.load(saveAndReturn(byDim));

        assertEquals(2, snap.zoneCount());
        assertEquals(WeatherType.RAIN, snap.zones.get("minecraft:overworld").get(1L).getCurrentWeather());
        assertEquals(WeatherType.SNOW, snap.zones.get("somemod:frozen_lands").get(1L).getCurrentWeather());
    }

    private Path saveAndReturn(Map<String, Map<Long, WeatherZone>> byDim) {
        ZoneStore.save(world, byDim, 0);
        return world;
    }

    @Test
    void secondSaveReplacesTheFirstAndLeavesNoTempFile() throws IOException {
        ZoneStore.save(world, overworld(1L, new WeatherZone(0, 0, WeatherType.RAIN, 100)), 0);
        ZoneStore.save(world, overworld(2L, new WeatherZone(1, 1, WeatherType.CLEAR, 100)), 50);

        ZoneStore.Snapshot snap = ZoneStore.load(world);
        assertEquals(1, snap.zoneCount());
        assertTrue(snap.zones.get("minecraft:overworld").containsKey(2L));

        try (var files = Files.list(world)) {
            assertEquals(Set.of(ZoneStore.FILE_NAME),
                    files.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
        }
    }

    @Test
    void noFileMeansNoSavedWeather() {
        ZoneStore.Snapshot snap = ZoneStore.load(world);
        assertEquals(0, snap.zoneCount());
        assertFalse(snap.hasWind);
    }

    @Test
    void someoneElsesFileIsIgnored() throws IOException {
        write("hello world\nz 1 0 0 RAIN RAIN 1.0 100 -1\n");
        assertEquals(0, ZoneStore.load(world).zoneCount());
    }

    @Test
    void newerFormatIsIgnoredRatherThanMisread() throws IOException {
        write("localweather-zones 2\ngametime 5\ndim minecraft:overworld\nz 1 0 0 RAIN RAIN 1.0 100 -1\n");
        assertEquals(0, ZoneStore.load(world).zoneCount());
    }

    @Test
    void badLineCostsOnlyThatLine() throws IOException {
        write("""
                localweather-zones 1
                gametime 1000
                dim minecraft:overworld
                z 1 0 0 RAIN RAIN 1.0 100 -1
                z 2 0 1 TORNADO RAIN 1.0 100 -1
                z 3 0 2 RAIN RAIN NaN 100 -1
                z 4 0 3 CLEAR
                z 5 0 4 CLEAR CLEAR 1.0 -5 -1
                garbage
                z 6 0 5 SNOW SNOW 1.0 300 -1
                """);

        ZoneStore.Snapshot snap = ZoneStore.load(world);

        assertEquals(Set.of(1L, 6L), snap.zones.get("minecraft:overworld").keySet());
        assertEquals(1000, snap.gameTime);
        assertFalse(snap.hasWind, "no wind line, so the wind should be left alone");
    }

    @Test
    void zoneBeforeAnyDimensionIsSkipped() throws IOException {
        write("localweather-zones 1\ngametime 0\nz 1 0 0 RAIN RAIN 1.0 100 -1\n");
        assertEquals(0, ZoneStore.load(world).zoneCount());
    }

    @Test
    void dormantTimeFromTheFutureIsClampedToTheSave() throws IOException {
        // A hand-edited or clock-rolled-back file must not leave a zone that
        // thinks it was left after "now", which would give it negative time away.
        write("localweather-zones 1\ngametime 1000\ndim minecraft:overworld\nz 1 0 0 RAIN RAIN 1.0 100 99999\n");
        assertEquals(1000, ZoneStore.load(world).zones.get("minecraft:overworld").get(1L).getDormantSince());
    }

    @Test
    void failedSaveReportsItAndKeepsThePreviousFile() throws IOException {
        ZoneStore.save(world, overworld(1L, new WeatherZone(0, 0, WeatherType.RAIN, 100)), 0);
        // A directory where the temp file should go makes the write fail.
        Files.createDirectory(world.resolve(ZoneStore.FILE_NAME + ".tmp"));

        int result = ZoneStore.save(world, overworld(2L, new WeatherZone(0, 0, WeatherType.CLEAR, 100)), 0);

        assertEquals(-1, result);
        assertTrue(ZoneStore.load(world).zones.get("minecraft:overworld").containsKey(1L));
    }
}
