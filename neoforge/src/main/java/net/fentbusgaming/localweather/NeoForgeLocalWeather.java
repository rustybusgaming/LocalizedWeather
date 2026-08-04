package net.fentbusgaming.localweather;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(NeoForgeLocalWeather.MOD_ID)
public final class NeoForgeLocalWeather {
    public static final String MOD_ID = "localweather";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NeoForgeLocalWeather(IEventBus modBus) {
        LOGGER.info("[LocalWeather] Initializing NeoForge platform module");
    }
}