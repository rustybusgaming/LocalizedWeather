package net.fentbusgaming.localweather.weather;

import net.fentbusgaming.localweather.weather.WeatherZone.WeatherType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ZoneRetentionTest {

    private static Map<Long, WeatherZone> zonesWith(long key, WeatherZone zone) {
        Map<Long, WeatherZone> zones = new HashMap<>();
        zones.put(key, zone);
        return zones;
    }

    @Test
    void zoneSurvivesWhenEveryoneLeaves() {
        WeatherZone storm = new WeatherZone(0, 0, WeatherType.THUNDER, 10_000);
        Map<Long, WeatherZone> zones = zonesWith(1L, storm);

        ZoneRetention.reconcile(zones, Set.of(), 100);

        assertSame(storm, zones.get(1L), "the zone was thrown away");
        assertTrue(storm.isDormant());
    }

    @Test
    void stormIsStillThereOnAShortReturn() {
        WeatherZone storm = new WeatherZone(0, 0, WeatherType.THUNDER, 10_000);
        Map<Long, WeatherZone> zones = zonesWith(1L, storm);

        ZoneRetention.reconcile(zones, Set.of(), 1_000);
        ZoneRetention.reconcile(zones, Set.of(1L), 3_000);

        assertEquals(WeatherType.THUNDER, storm.getCurrentWeather());
        assertEquals(8_000, storm.getWeatherDuration(), "the 2000 ticks away were not spent");
        assertFalse(storm.isDormant());
    }

    @Test
    void longAbsenceExpiresOnTheNextRealTick() {
        WeatherZone storm = new WeatherZone(0, 0, WeatherType.THUNDER, 500);
        Map<Long, WeatherZone> zones = zonesWith(1L, storm);

        ZoneRetention.reconcile(zones, Set.of(), 0);
        ZoneRetention.reconcile(zones, Set.of(1L), 100_000);

        // Left at one tick, not zero or negative, so the manager's own
        // tickDuration() reports the expiry and picks the next weather from
        // the biome — a path this class cannot run.
        assertEquals(1, storm.getWeatherDuration());
        assertTrue(storm.tickDuration());
    }

    @Test
    void transitionInFlightFinishesWhileAway() {
        WeatherZone zone = new WeatherZone(0, 0, WeatherType.CLEAR, 50_000);
        zone.setTargetWeather(WeatherType.RAIN);
        Map<Long, WeatherZone> zones = zonesWith(1L, zone);

        ZoneRetention.reconcile(zones, Set.of(), 0);
        ZoneRetention.reconcile(zones, Set.of(1L), 1_000);

        assertEquals(1.0f, zone.getTransitionProgress());
        assertEquals(WeatherType.RAIN, zone.getCurrentWeather());
    }

    @Test
    void shortAbsenceAdvancesTheTransitionPartway() {
        WeatherZone zone = new WeatherZone(0, 0, WeatherType.CLEAR, 50_000);
        zone.setTargetWeather(WeatherType.RAIN);
        Map<Long, WeatherZone> zones = zonesWith(1L, zone);

        ZoneRetention.reconcile(zones, Set.of(), 0);
        ZoneRetention.reconcile(zones, Set.of(1L), WeatherZone.TRANSITION_TICKS / 4);

        assertEquals(0.25f, zone.getTransitionProgress(), 1e-4f);
        assertEquals(WeatherType.CLEAR, zone.getCurrentWeather());
    }

    @Test
    void zoneInRangeIsNeverTouched() {
        WeatherZone zone = new WeatherZone(0, 0, WeatherType.RAIN, 7_000);
        Map<Long, WeatherZone> zones = zonesWith(1L, zone);

        for (int t = 0; t < 5; t++) {
            ZoneRetention.reconcile(zones, Set.of(1L), t * 1_000L);
        }

        assertFalse(zone.isDormant());
        assertEquals(7_000, zone.getWeatherDuration());
    }

    @Test
    void dormantClockKeepsTheTimeItWasFirstLeft() {
        WeatherZone zone = new WeatherZone(0, 0, WeatherType.RAIN, 10_000);
        Map<Long, WeatherZone> zones = zonesWith(1L, zone);

        ZoneRetention.reconcile(zones, Set.of(), 1_000);
        ZoneRetention.reconcile(zones, Set.of(), 5_000);

        assertEquals(1_000, zone.getDormantSince());
    }

    @Test
    void evictionDropsTheOldestAndNeverAnActiveZone() {
        Map<Long, WeatherZone> zones = new HashMap<>();
        int n = ZoneRetention.MAX_DORMANT + 10;
        for (long k = 0; k < n; k++) {
            WeatherZone zone = new WeatherZone((int) k, 0, WeatherType.CLEAR, 99_999);
            zone.markDormant(k); // lower key, left earlier
            zones.put(k, zone);
        }
        WeatherZone active = new WeatherZone(-1, 0, WeatherType.RAIN, 99_999);
        zones.put(-1L, active);

        ZoneRetention.reconcile(zones, Set.of(-1L), 1_000_000);

        long dormant = zones.values().stream().filter(WeatherZone::isDormant).count();
        assertEquals(ZoneRetention.MAX_DORMANT, dormant);
        assertFalse(zones.containsKey(0L), "the oldest dormant zone survived");
        assertFalse(zones.containsKey(9L), "the tenth-oldest dormant zone survived");
        assertTrue(zones.containsKey(10L), "a zone inside the cap was dropped");
        assertSame(active, zones.get(-1L), "the active zone was evicted");
    }

    @Test
    void catchUpWithNothingElapsedChangesNothing() {
        WeatherZone zone = new WeatherZone(0, 0, WeatherType.SNOW, 1_234);
        zone.catchUp(0);
        zone.catchUp(-50);
        assertEquals(1_234, zone.getWeatherDuration());
    }

    @Test
    void frozenWeatherSpendsNoTimeWhileAway() {
        // doWeatherCycle off: a storm left for 5000 ticks is untouched on return.
        WeatherZone storm = new WeatherZone(0, 0, WeatherType.THUNDER, 10_000);
        Map<Long, WeatherZone> zones = zonesWith(1L, storm);

        ZoneRetention.reconcile(zones, Set.of(), 1_000, false);
        ZoneRetention.reconcile(zones, Set.of(), 3_000, false);
        ZoneRetention.reconcile(zones, Set.of(1L), 6_000, false);

        assertFalse(storm.isDormant());
        assertEquals(10_000, storm.getWeatherDuration());
    }

    @Test
    void onlyTimeAfterTheRuleIsBackOnCounts() {
        // Left at 1000, rule turned off at 5000 and kept off until 9000, player
        // back at 10000 with the rule on. Freezing holds the zone as it was left,
        // so only the 1000 ticks since the rule came back are spent.
        WeatherZone storm = new WeatherZone(0, 0, WeatherType.THUNDER, 10_000);
        Map<Long, WeatherZone> zones = zonesWith(1L, storm);

        ZoneRetention.reconcile(zones, Set.of(), 1_000, true);
        ZoneRetention.reconcile(zones, Set.of(), 5_000, false);
        ZoneRetention.reconcile(zones, Set.of(), 9_000, false);
        ZoneRetention.reconcile(zones, Set.of(1L), 10_000, true);

        assertEquals(9_000, storm.getWeatherDuration());
    }
}
