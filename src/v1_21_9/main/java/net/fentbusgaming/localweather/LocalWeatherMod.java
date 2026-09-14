package net.fentbusgaming.localweather;

import net.fabricmc.api.ModInitializer;
import net.fentbusgaming.localweather.network.WeatherPackets;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalWeatherMod implements ModInitializer {

    public static final String MOD_ID = "localweather";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[LocalWeather] Initializing Localized Weather mod");
        WeatherPackets.registerServerPackets();
        WeatherZoneManager.init();
    }
}
