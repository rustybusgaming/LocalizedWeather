package net.fentbusgaming.localweather.weather;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps a zone's weather when everyone walks away from it.
 *
 * The simulation used to delete a zone the moment no player was within range
 * of it, and build a fresh one — seventy per cent of the time a clear one —
 * when somebody came back. So a thunderstorm you rode out of was simply gone
 * when you turned around, which is the opposite of what localized weather is
 * for.
 *
 * Now a zone nobody is near goes dormant instead: it stays in the map, stops
 * being ticked, and remembers when it was left. When a player comes back into
 * range it is caught up on the time it missed in one step, so a storm that
 * should have blown over has, and one that should still be raining still is.
 * Idle zones cost nothing per tick.
 *
 * Memory is bounded by {@link #MAX_DORMANT} per world. Past that, the zones
 * that have been empty longest are dropped, and those are the ones a player is
 * least likely to walk back into.
 *
 * Touches no Minecraft or loader API, so every line of the simulation calls it
 * the same way.
 */
public final class ZoneRetention {

    /**
     * Dormant zones kept per world. A zone is 256 blocks on a side, so this is
     * a remembered area of roughly sixteen thousand blocks square — and a few
     * hundred kilobytes at most.
     */
    public static final int MAX_DORMANT = 4096;

    private ZoneRetention() {}

    /**
     * Reconcile one world's zones against which of them a player is near.
     *
     * @param zones    the world's zones, keyed by packed zone coordinates
     * @param active   the keys of every zone within a player's range this tick
     * @param gameTime the world's current game time, in ticks
     */
    public static void reconcile(Map<Long, WeatherZone> zones, Set<Long> active, long gameTime) {
        reconcile(zones, active, gameTime, true);
    }

    /**
     * As {@link #reconcile(Map, Set, long)}, for a world whose weather may be
     * frozen by its weather-cycle gamerule.
     *
     * While {@code weatherAdvances} is false a dormant zone's clock is held at
     * the current time, so the hours the rule was off are not spent against its
     * weather when a player comes back, or when the rule is turned on again.
     * A zone already dormant when the rule goes off is held as it was left:
     * frozen weather means the remembered weather too, and counting the time
     * before the switch exactly would need every zone to carry more state.
     *
     * @param weatherAdvances the world's weather-cycle gamerule
     */
    public static void reconcile(Map<Long, WeatherZone> zones, Set<Long> active, long gameTime,
                                 boolean weatherAdvances) {
        int dormant = 0;

        for (Map.Entry<Long, WeatherZone> entry : zones.entrySet()) {
            WeatherZone zone = entry.getValue();
            boolean near = active.contains(entry.getKey());

            if (near) {
                if (zone.isDormant()) {
                    if (weatherAdvances) zone.catchUp(gameTime - zone.getDormantSince());
                    zone.wake();
                }
            } else {
                if (!zone.isDormant() || !weatherAdvances) {
                    zone.markDormant(gameTime);
                }
                dormant++;
            }
        }

        if (dormant > MAX_DORMANT) {
            evictOldest(zones, dormant - MAX_DORMANT);
        }
    }

    /** Drop the {@code count} zones that have been dormant longest. */
    private static void evictOldest(Map<Long, WeatherZone> zones, int count) {
        List<Map.Entry<Long, WeatherZone>> dormant = new ArrayList<>();
        for (Map.Entry<Long, WeatherZone> entry : zones.entrySet()) {
            if (entry.getValue().isDormant()) {
                dormant.add(entry);
            }
        }
        dormant.sort((a, b) -> Long.compare(a.getValue().getDormantSince(), b.getValue().getDormantSince()));

        List<Long> doomed = new ArrayList<>(count);
        Iterator<Map.Entry<Long, WeatherZone>> it = dormant.iterator();
        while (doomed.size() < count && it.hasNext()) {
            doomed.add(it.next().getKey());
        }
        for (Long key : doomed) {
            zones.remove(key);
        }
    }
}
