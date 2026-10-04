package net.fentbusgaming.localweather.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Server-side settings, read from {@code config/localweather.properties}.
 *
 * This class touches no Minecraft or loader API, which is the point: it lives
 * in {@code src/shared} and so the same file serves Fabric, NeoForge and Forge
 * on every supported Minecraft version, with no per-loader config plumbing.
 * The path is resolved against the process working directory, which is the
 * instance or server directory on all three loaders — the same place they put
 * their own configs.
 *
 * Every setting is server-side and has no bearing on what the client draws.
 * Zone size, the client's zone radius and the sync intervals are deliberately
 * not configurable: the client caches and the renderers are built around them,
 * so a server that changed one would desync every client connected to it.
 *
 * Nothing here throws. A missing file is written from the defaults, and a file
 * that cannot be read or parsed leaves the defaults in place and is reported
 * once, because a malformed config is not a reason to take a server down.
 */
public final class LocalWeatherConfig {

    /** One minute of Minecraft time. */
    private static final int TICKS_PER_MINUTE = 1200;

    private static final String FILE_NAME = "localweather.properties";

    // Defaults are the values the simulation shipped with before it was
    // configurable, so an untouched config changes nothing.
    private static final int DEFAULT_CLEAR_MIN_MINUTES = 10;
    private static final int DEFAULT_CLEAR_MAX_MINUTES = 150;
    private static final int DEFAULT_WET_MIN_MINUTES = 10;
    private static final int DEFAULT_WET_MAX_MINUTES = 20;
    private static final int DEFAULT_LIGHTNING_RARITY = 1200;
    private static final int DEFAULT_STORM_CELLS_MAX = 4;

    private static volatile LocalWeatherConfig instance;

    private final int clearTicksMin;
    private final int clearTicksMax;
    private final int wetTicksMin;
    private final int wetTicksMax;
    private final boolean lightningEnabled;
    private final int lightningRarity;
    private final boolean stormCellsEnabled;
    private final int stormCellsMax;
    private final boolean suppressVanillaWeather;

    private LocalWeatherConfig(Properties p) {
        int clearMin = minutes(p, "clear-minutes-min", DEFAULT_CLEAR_MIN_MINUTES);
        int clearMax = minutes(p, "clear-minutes-max", DEFAULT_CLEAR_MAX_MINUTES);
        int wetMin = minutes(p, "rain-minutes-min", DEFAULT_WET_MIN_MINUTES);
        int wetMax = minutes(p, "rain-minutes-max", DEFAULT_WET_MAX_MINUTES);

        // A max below its min would make the spread negative, which the
        // simulation feeds straight to Random.nextInt. Widen rather than
        // reject: the intent of min > max is obvious enough.
        if (clearMax < clearMin) clearMax = clearMin;
        if (wetMax < wetMin) wetMax = wetMin;

        this.clearTicksMin = clearMin * TICKS_PER_MINUTE;
        this.clearTicksMax = clearMax * TICKS_PER_MINUTE;
        this.wetTicksMin = wetMin * TICKS_PER_MINUTE;
        this.wetTicksMax = wetMax * TICKS_PER_MINUTE;

        this.lightningEnabled = bool(p, "lightning", true);
        this.lightningRarity = clamp(integer(p, "lightning-rarity", DEFAULT_LIGHTNING_RARITY), 1, 1_000_000);
        this.stormCellsEnabled = bool(p, "storm-cells", true);
        this.stormCellsMax = clamp(integer(p, "storm-cells-max", DEFAULT_STORM_CELLS_MAX), 0, 64);
        this.suppressVanillaWeather = bool(p, "suppress-vanilla-weather", true);
    }

    private static LocalWeatherConfig get() {
        LocalWeatherConfig local = instance;
        if (local == null) {
            synchronized (LocalWeatherConfig.class) {
                local = instance;
                if (local == null) {
                    local = load();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Replace the loaded settings with ones parsed from {@code p}, skipping the
     * file entirely. Package-private: it exists so the parsing rules — clamps,
     * a max below its min, malformed values — can be tested without a server or
     * a config directory.
     */
    static void useForTesting(Properties p) {
        synchronized (LocalWeatherConfig.class) {
            instance = new LocalWeatherConfig(p);
        }
    }

    /**
     * Read the file again, so a server owner can change durations or turn
     * lightning off without a restart. Runs alongside vanilla's {@code /reload}.
     * Zones already mid-weather keep the duration they drew; the new values
     * apply from each zone's next change.
     *
     * @return the new settings, in the same form as {@link #summary()}
     */
    public static String reload() {
        synchronized (LocalWeatherConfig.class) {
            instance = load();
        }
        return summary();
    }

    private static LocalWeatherConfig load() {
        Properties p = new Properties();
        Path path = Paths.get("config", FILE_NAME);
        try {
            if (Files.isRegularFile(path)) {
                try (InputStream in = Files.newInputStream(path)) {
                    p.load(in);
                }
            } else {
                writeTemplate(path);
            }
        } catch (IOException | RuntimeException e) {
            // Keep the defaults. One line, not a stack trace: the server is
            // about to run perfectly well without the file.
            System.err.println("[LocalWeather] Could not read config/" + FILE_NAME
                    + " (" + e.getMessage() + "); using defaults.");
        }
        return new LocalWeatherConfig(p);
    }

    private static void writeTemplate(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.write(path, (""
                + "# Localized Weather — server-side settings.\n"
                + "# Delete this file to get the defaults back. Run /reload, or restart,\n"
                + "# after changing it.\n"
                + "\n"
                + "# How long a zone stays dry, and how long it stays wet, in minutes.\n"
                + "# A zone picks a new span at random inside these bounds every time its\n"
                + "# weather turns over.\n"
                + "clear-minutes-min = " + DEFAULT_CLEAR_MIN_MINUTES + "\n"
                + "clear-minutes-max = " + DEFAULT_CLEAR_MAX_MINUTES + "\n"
                + "rain-minutes-min = " + DEFAULT_WET_MIN_MINUTES + "\n"
                + "rain-minutes-max = " + DEFAULT_WET_MAX_MINUTES + "\n"
                + "\n"
                + "# Lightning inside thunder zones. Rarity is a one-in-N chance per\n"
                + "# eligible tick, so a larger number means fewer strikes.\n"
                + "lightning = true\n"
                + "lightning-rarity = " + DEFAULT_LIGHTNING_RARITY + "\n"
                + "\n"
                + "# Travelling single-cell thunderstorms — the storm core that drags a\n"
                + "# rain wall and rain bands along with it. Set storm-cells to false for\n"
                + "# zone weather with no moving cells at all.\n"
                + "storm-cells = true\n"
                + "storm-cells-max = " + DEFAULT_STORM_CELLS_MAX + "\n"
                + "\n"
                + "# Localized weather replaces vanilla's global weather by default. Turn\n"
                + "# this off to let vanilla run its own weather cycle alongside the zones,\n"
                + "# which means rain in the sky that the zones did not put there.\n"
                + "suppress-vanilla-weather = true\n").getBytes("UTF-8"));
    }

    private static int minutes(Properties p, String key, int fallback) {
        return clamp(integer(p, key, fallback), 1, 60 * 24 * 7);
    }

    private static int integer(Properties p, String key, int fallback) {
        String raw = p.getProperty(key);
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            System.err.println("[LocalWeather] config " + key + "='" + raw.trim()
                    + "' is not a whole number; using " + fallback + ".");
            return fallback;
        }
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        String raw = p.getProperty(key);
        if (raw == null) return fallback;
        String v = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (v.equals("true") || v.equals("yes") || v.equals("1")) return true;
        if (v.equals("false") || v.equals("no") || v.equals("0")) return false;
        System.err.println("[LocalWeather] config " + key + "='" + raw.trim()
                + "' is not true or false; using " + fallback + ".");
        return fallback;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static int clearTicksMin() { return get().clearTicksMin; }
    public static int clearTicksMax() { return get().clearTicksMax; }
    public static int wetTicksMin() { return get().wetTicksMin; }
    public static int wetTicksMax() { return get().wetTicksMax; }
    public static boolean lightningEnabled() { return get().lightningEnabled; }
    public static int lightningRarity() { return get().lightningRarity; }
    public static boolean stormCellsEnabled() { return get().stormCellsEnabled; }
    public static int stormCellsMax() { return get().stormCellsMax; }
    public static boolean suppressVanillaWeather() { return get().suppressVanillaWeather; }

    /** A one-line summary for the startup log. */
    public static String summary() {
        LocalWeatherConfig c = get();
        return "clear " + (c.clearTicksMin / TICKS_PER_MINUTE) + "-" + (c.clearTicksMax / TICKS_PER_MINUTE)
                + "min, rain " + (c.wetTicksMin / TICKS_PER_MINUTE) + "-" + (c.wetTicksMax / TICKS_PER_MINUTE)
                + "min, lightning " + (c.lightningEnabled ? "1 in " + c.lightningRarity : "off")
                + ", storm cells " + (c.stormCellsEnabled ? String.valueOf(c.stormCellsMax) : "off")
                + ", vanilla weather " + (c.suppressVanillaWeather ? "suppressed" : "left alone");
    }
}
