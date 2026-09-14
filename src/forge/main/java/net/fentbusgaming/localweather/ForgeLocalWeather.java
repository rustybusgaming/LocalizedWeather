package net.fentbusgaming.localweather;

import com.mojang.logging.LogUtils;
import net.fentbusgaming.localweather.weather.WeatherSync;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Forge platform entry point.
 *
 * Minecraft 26.x is compiled against Mojang's own names on Fabric, NeoForge and
 * Forge alike, so the zone simulation is shared with all of them rather than
 * duplicated — this module compiles it straight out of the Fabric tree. Only
 * the glue below is platform-specific.
 *
 * Client rendering and packet sync are not ported here, so weather is simulated
 * server-side but not yet shown to clients.
 */
@Mod(ForgeLocalWeather.MOD_ID)
public final class ForgeLocalWeather {

    public static final String MOD_ID = "localweather";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ForgeLocalWeather() {
        LOGGER.info("[LocalWeather] Initializing Forge platform module");

        // No payloads are registered on this platform yet, so the simulation
        // runs with a sink that drops updates rather than a half-wired one.
        WeatherZoneManager.init(WeatherSync.NONE);

        // Forge 26.x split TickEvent's phases into separate event types, each
        // with its own bus, so the phase check that used to live in the handler
        // is the choice of bus instead.
        TickEvent.ServerTickEvent.Post.BUS.addListener(this::onServerTickPost);
    }

    private void onServerTickPost(TickEvent.ServerTickEvent.Post event) {
        WeatherZoneManager.tick(event.server());
    }
}
