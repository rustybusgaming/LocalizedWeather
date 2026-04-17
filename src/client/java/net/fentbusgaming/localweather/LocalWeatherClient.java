package net.fentbusgaming.localweather;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.fentbusgaming.localweather.render.StormCloudRenderer;
import net.fentbusgaming.localweather.sound.DirectionalThunderSound;
import net.fentbusgaming.localweather.sound.WeatherSoundManager;

@Environment(EnvType.CLIENT)
public class LocalWeatherClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientWeatherHandler.register();
        StormCloudRenderer.register();
        DirectionalThunderSound.register();
        WeatherSoundManager.register();
    }
}
