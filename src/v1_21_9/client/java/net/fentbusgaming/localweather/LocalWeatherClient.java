package net.fentbusgaming.localweather;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fentbusgaming.localweather.network.ClientStormCellHandler;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.fentbusgaming.localweather.sound.DirectionalThunderSound;
import net.fentbusgaming.localweather.sound.WeatherSoundManager;

@Environment(EnvType.CLIENT)
public class LocalWeatherClient implements ClientModInitializer {

    /**
     * The storm clouds, hail and rain wall are drawn from WorldRendererMixin
     * rather than registered here: Fabric API for 1.21.9 has no world-render
     * event to hang them on. Everything else registers the same way as on the
     * later 1.21.x lines.
     */
    @Override
    public void onInitializeClient() {
        ClientWeatherHandler.register();
        ClientStormCellHandler.register();
        DirectionalThunderSound.register();
        WeatherSoundManager.register();
    }
}
