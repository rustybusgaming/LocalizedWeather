package net.fentbusgaming.localweather;

import com.mojang.logging.LogUtils;
import net.fentbusgaming.localweather.network.ClientStormCellHandler;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.fentbusgaming.localweather.network.NeoForgeWeatherSync;
import net.fentbusgaming.localweather.network.WeatherPayloads;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@Mod(NeoForgeLocalWeather.MOD_ID)
public final class NeoForgeLocalWeather {

    public static final String MOD_ID = "localweather";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NeoForgeLocalWeather(IEventBus modBus) {
        LOGGER.info("[LocalWeather] Initializing NeoForge platform module (1.21.1)");

        WeatherZoneManager.init(new NeoForgeWeatherSync());
        modBus.addListener(NeoForgeLocalWeather::registerPayloads);
        NeoForge.EVENT_BUS.register(this);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MOD_ID);

        registrar.playToClient(
                WeatherPayloads.WeatherUpdatePayload.ID,
                WeatherPayloads.WeatherUpdatePayload.CODEC,
                (payload, context) -> ClientWeatherHandler.handleWeatherUpdate(payload));

        registrar.playToClient(
                WeatherPayloads.WindUpdatePayload.ID,
                WeatherPayloads.WindUpdatePayload.CODEC,
                (payload, context) -> ClientWeatherHandler.handleWindUpdate(payload));

        registrar.playToClient(
                WeatherPayloads.StormCellPayload.ID,
                WeatherPayloads.StormCellPayload.CODEC,
                (payload, context) -> ClientStormCellHandler.handleStormCell(payload));

        LOGGER.info("[LocalWeather] Registered S2C weather packets.");
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        WeatherZoneManager.tick(event.getServer());
    }
}
