package net.fentbusgaming.localweather.weather;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Saves zone weather into the world folder, so a restart picks up where the
 * world left off.
 *
 * The file is {@code localweather_zones.dat} beside {@code level.dat}. It is
 * written from inside vanilla's own world save — autosave, {@code /save-all}
 * and shutdown all pass through the same method — so it is always consistent
 * with the game time saved alongside it. Minecraft's clock does not run while
 * a server is off, and neither does the weather: a storm that had five minutes
 * left when the server stopped has five minutes left when it starts again.
 *
 * Plain versioned text rather than NBT, because NBT's API has moved between
 * every version this mod supports and this class touches no Minecraft code at
 * all. That keeps it shared by every loader and version, and a curious server
 * owner can read the file.
 *
 * <pre>
 * localweather-zones 1
 * gametime 123456
 * wind angle target ticksUntilShift
 * dim minecraft:overworld
 * z key zoneX zoneZ CURRENT TARGET progress duration dormantSince
 * </pre>
 *
 * Nothing here throws. A failed save logs one line and leaves the previous
 * file in place; a missing, unreadable or malformed file loads as no saved
 * weather, and a line that cannot be parsed is skipped rather than costing
 * the rest of the file. Either way the world simply generates fresh weather,
 * which is what it did before this class existed.
 */
public final class ZoneStore {

    public static final String FILE_NAME = "localweather_zones.dat";
    private static final String HEADER = "localweather-zones";
    private static final int FORMAT = 1;

    private ZoneStore() {}

    /** Everything a world's save holds. */
    public static final class Snapshot {
        /** Zones per dimension id, every one of them dormant. */
        public final Map<String, Map<Long, WeatherZone>> zones;
        public final long gameTime;
        public final boolean hasWind;
        public final double windAngle;
        public final double windTarget;
        public final int windTicksUntilShift;

        Snapshot(Map<String, Map<Long, WeatherZone>> zones, long gameTime, boolean hasWind,
                 double windAngle, double windTarget, int windTicksUntilShift) {
            this.zones = zones;
            this.gameTime = gameTime;
            this.hasWind = hasWind;
            this.windAngle = windAngle;
            this.windTarget = windTarget;
            this.windTicksUntilShift = windTicksUntilShift;
        }

        static Snapshot empty() {
            return new Snapshot(new HashMap<>(), 0L, false, 0.0, 0.0, 0);
        }

        public int zoneCount() {
            int n = 0;
            for (Map<Long, WeatherZone> m : zones.values()) n += m.size();
            return n;
        }
    }

    /**
     * Write every zone, and the wind, to {@code worldDir}. Writes a temporary
     * file and moves it into place, so a crash mid-save cannot leave a half
     * written file where the last good one was.
     *
     * @return the number of zones written, or -1 if the save failed
     */
    public static int save(Path worldDir, Map<String, Map<Long, WeatherZone>> zonesByDimension, long gameTime) {
        Path target = worldDir.resolve(FILE_NAME);
        Path tmp = worldDir.resolve(FILE_NAME + ".tmp");
        int written = 0;
        try {
            Files.createDirectories(worldDir);
            try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                w.write(HEADER + " " + FORMAT + "\n");
                w.write("gametime " + gameTime + "\n");
                w.write(String.format(Locale.ROOT, "wind %s %s %d%n",
                        Double.toString(WindState.getWindAngle()),
                        Double.toString(WindState.getTargetAngle()),
                        WindState.getTicksUntilShift()));
                for (Map.Entry<String, Map<Long, WeatherZone>> dim : zonesByDimension.entrySet()) {
                    // Ids are namespaced paths, which never contain whitespace;
                    // refuse one that does rather than write a line that would
                    // read back as something else.
                    if (dim.getKey().isEmpty() || dim.getKey().matches(".*\\s.*")) continue;
                    w.write("dim " + dim.getKey() + "\n");
                    for (Map.Entry<Long, WeatherZone> e : dim.getValue().entrySet()) {
                        WeatherZone z = e.getValue();
                        w.write(String.format(Locale.ROOT, "z %d %d %d %s %s %s %d %d%n",
                                e.getKey(), z.getZoneX(), z.getZoneZ(),
                                z.getCurrentWeather().name(), z.getTargetWeather().name(),
                                Float.toString(z.getTransitionProgress()),
                                z.getWeatherDuration(), z.getDormantSince()));
                        written++;
                    }
                }
            }
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return written;
        } catch (IOException | RuntimeException e) {
            System.err.println("[LocalWeather] Could not save zone weather to " + target
                    + " (" + e.getMessage() + "); the previous save is unchanged.");
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // Nothing further to do; the next save overwrites it.
            }
            return -1;
        }
    }

    /**
     * Read what {@link #save} wrote. Every zone comes back dormant: those that
     * were dormant keep the time they were first left, and those that were in
     * use are treated as left at the moment of the save. A player arriving near
     * one wakes it through {@link ZoneRetention} like any other dormant zone,
     * caught up on however much game time has passed since.
     */
    public static Snapshot load(Path worldDir) {
        Path file = worldDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) return Snapshot.empty();

        Map<String, Map<Long, WeatherZone>> zones = new HashMap<>();
        long gameTime = 0L;
        boolean hasWind = false;
        double windAngle = 0.0, windTarget = 0.0;
        int windTicks = 0;
        int skipped = 0;

        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String first = r.readLine();
            if (first == null || !first.trim().startsWith(HEADER + " ")) {
                System.err.println("[LocalWeather] " + file + " is not a zone save; ignoring it.");
                return Snapshot.empty();
            }
            int format = parseIntOr(first.trim().substring(HEADER.length()).trim(), -1);
            if (format != FORMAT) {
                System.err.println("[LocalWeather] " + file + " is format " + format
                        + ", this version reads " + FORMAT + "; ignoring it.");
                return Snapshot.empty();
            }

            // Zone lines need the save's game time to settle when an in-use zone
            // was "left", so they are held until the whole file has been read.
            Map<String, java.util.List<String[]>> pending = new HashMap<>();
            String dim = null;
            String line;
            while ((line = r.readLine()) != null) {
                String[] t = line.trim().split("\\s+");
                if (t.length == 0 || t[0].isEmpty()) continue;
                try {
                    switch (t[0]) {
                        case "gametime" -> gameTime = Long.parseLong(t[1]);
                        case "wind" -> {
                            windAngle = Double.parseDouble(t[1]);
                            windTarget = Double.parseDouble(t[2]);
                            windTicks = Integer.parseInt(t[3]);
                            hasWind = Double.isFinite(windAngle) && Double.isFinite(windTarget);
                        }
                        case "dim" -> dim = t[1];
                        case "z" -> {
                            if (dim == null || t.length < 9) { skipped++; continue; }
                            pending.computeIfAbsent(dim, k -> new java.util.ArrayList<>()).add(t);
                        }
                        default -> skipped++;
                    }
                } catch (RuntimeException e) {
                    skipped++;
                }
            }

            for (Map.Entry<String, java.util.List<String[]>> e : pending.entrySet()) {
                Map<Long, WeatherZone> dimZones = new HashMap<>();
                for (String[] t : e.getValue()) {
                    try {
                        long key = Long.parseLong(t[1]);
                        int x = Integer.parseInt(t[2]);
                        int z = Integer.parseInt(t[3]);
                        WeatherZone.WeatherType current = WeatherZone.WeatherType.valueOf(t[4]);
                        WeatherZone.WeatherType target = WeatherZone.WeatherType.valueOf(t[5]);
                        float progress = Float.parseFloat(t[6]);
                        int duration = Integer.parseInt(t[7]);
                        long dormantSince = Long.parseLong(t[8]);
                        if (!Float.isFinite(progress) || duration < 0) { skipped++; continue; }
                        long since = dormantSince >= 0 ? Math.min(dormantSince, gameTime) : gameTime;
                        dimZones.put(key, WeatherZone.restore(x, z, current, target, progress, duration, since));
                    } catch (RuntimeException ex) {
                        skipped++;
                    }
                }
                if (!dimZones.isEmpty()) zones.put(e.getKey(), dimZones);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[LocalWeather] Could not read " + file + " (" + e.getMessage()
                    + "); starting with fresh weather.");
            return Snapshot.empty();
        }

        if (skipped > 0) {
            System.err.println("[LocalWeather] Skipped " + skipped + " unreadable line(s) in " + file + ".");
        }
        return new Snapshot(zones, gameTime, hasWind, windAngle, windTarget, windTicks);
    }

    private static int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
