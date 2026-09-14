package net.fentbusgaming.localweather;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fentbusgaming.localweather.network.ClientStormCellHandler;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.fentbusgaming.localweather.network.WeatherPayloads;
import net.fentbusgaming.localweather.render.HailParticleRenderer;
import net.fentbusgaming.localweather.render.RainCurtainRenderer;
import net.fentbusgaming.localweather.render.StormCloudRenderer;
import net.fentbusgaming.localweather.sound.DirectionalThunderSound;
import net.fentbusgaming.localweather.sound.WeatherSoundManager;

/**
 * Fabric's client wiring.
 *
 * Everything below the events — the zone cache, the storm-cell cache, the three
 * renderers and the sounds — is loader-agnostic and lives in
 * {@code src/v26/clientcommon}, shared with the NeoForge module. This class is
 * only the part that says which Fabric event calls what.
 */
@Environment(EnvType.CLIENT)
public class LocalWeatherClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                WeatherPayloads.WeatherUpdatePayload.ID,
                (payload, context) -> ClientWeatherHandler.handleWeatherUpdate(payload));
        ClientPlayNetworking.registerGlobalReceiver(
                WeatherPayloads.WindUpdatePayload.ID,
                (payload, context) -> ClientWeatherHandler.handleWindUpdate(payload));
        ClientPlayNetworking.registerGlobalReceiver(
                WeatherPayloads.StormCellPayload.ID,
                (payload, context) -> ClientStormCellHandler.handleStormCell(payload));

        ClientTickEvents.END_CLIENT_TICK.register(ClientWeatherHandler::clientTick);
        ClientTickEvents.END_CLIENT_TICK.register(ClientStormCellHandler::clientTick);
        ClientTickEvents.END_CLIENT_TICK.register(DirectionalThunderSound::clientTick);
        ClientTickEvents.END_CLIENT_TICK.register(WeatherSoundManager::clientTick);

        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            StormCloudRenderer.render(context.levelState(), context.submitNodeCollector(), context.poseStack());
            HailParticleRenderer.render(context.levelState(), context.submitNodeCollector(), context.poseStack());
            RainCurtainRenderer.render(context.levelState(), context.submitNodeCollector(), context.poseStack());
        });

        LocalWeatherMod.LOGGER.info("[LocalWeather] Client handlers, renderers and sounds registered.");
    }
}
