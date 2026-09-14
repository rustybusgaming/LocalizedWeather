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
     * No custom world geometry on 1.21.9: Fabric API's rendering module for this
     * version exposes no world-render event to hang it on — WorldRenderEvents
     * only appears from the 1.21.10-era builds onward. Zone weather, the rain
     * and fog gradients, sky darkening and directional thunder all come from
     * mixins and work normally; the storm clouds, hail particles and rain wall
     * do not exist on this version.
     */
    @Override
    public void onInitializeClient() {
        ClientWeatherHandler.register();
        ClientStormCellHandler.register();
        DirectionalThunderSound.register();
        WeatherSoundManager.register();
    }
}
