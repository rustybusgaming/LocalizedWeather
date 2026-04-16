package net.fentbusgaming.localweather;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;

@Environment(EnvType.CLIENT)
public class LocalWeatherClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientWeatherHandler.register();
    }
}
