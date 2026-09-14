package net.fentbusgaming.localweather;

import com.mojang.logging.LogUtils;
import net.fentbusgaming.localweather.weather.WeatherSync;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * NeoForge platform entry point.
 *
 * The zone simulation itself is shared with the Fabric build: Minecraft 26.x is
 * compiled against Mojang's own names on both loaders, so everything the
 * simulation touches is common and only the glue differs. This class supplies
 * that glue — a server tick and, eventually, networking.
 *
 * Client rendering and packet sync are not ported yet, so weather is simulated
 * server-side here but not yet shown to clients.
 */
@Mod(NeoForgeLocalWeather.MOD_ID)
public final class NeoForgeLocalWeather {

    public static final String MOD_ID = "localweather";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NeoForgeLocalWeather(IEventBus modBus) {
        LOGGER.info("[LocalWeather] Initializing NeoForge platform module");

        // No payloads registered on this platform yet, so the simulation runs
        // with a sink that drops updates rather than a half-wired one.
        WeatherZoneManager.init(WeatherSync.NONE);
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        WeatherZoneManager.tick(event.getServer());
    }
}
