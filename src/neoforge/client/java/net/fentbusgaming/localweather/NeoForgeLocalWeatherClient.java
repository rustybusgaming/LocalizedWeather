package net.fentbusgaming.localweather;

import net.fentbusgaming.localweather.network.ClientStormCellHandler;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.fentbusgaming.localweather.render.HailParticleRenderer;
import net.fentbusgaming.localweather.render.RainCurtainRenderer;
import net.fentbusgaming.localweather.render.StormCloudRenderer;
import net.fentbusgaming.localweather.sound.DirectionalThunderSound;
import net.fentbusgaming.localweather.sound.WeatherSoundManager;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge's client wiring, and the mirror of Fabric's {@code LocalWeatherClient}.
 *
 * Everything it drives — the zone cache, the storm-cell cache, the three
 * renderers and the sounds — is loader-agnostic and lives in
 * {@code src/v26/clientcommon}, shared with the Fabric build. This class only
 * says which NeoForge event calls what.
 *
 * {@code dist = Dist.CLIENT} keeps the whole class, and everything it reaches,
 * off a dedicated server.
 */
@Mod(value = NeoForgeLocalWeather.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeLocalWeatherClient {

    public NeoForgeLocalWeatherClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(this);
        NeoForgeLocalWeather.LOGGER.info("[LocalWeather] Client handlers, renderers and sounds registered.");
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        ClientWeatherHandler.clientTick(client);
        ClientStormCellHandler.clientTick(client);
        DirectionalThunderSound.clientTick(client);
        WeatherSoundManager.clientTick(client);
    }

    /**
     * 26.x builds the level from submitted nodes, so the overlays are handed
     * over here as custom-geometry nodes. This event carries the same three
     * things Fabric's {@code LevelRenderContext} does.
     */
    @SubscribeEvent
    public void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        StormCloudRenderer.render(event.getLevelRenderState(), event.getSubmitNodeCollector(), event.getPoseStack());
        HailParticleRenderer.render(event.getLevelRenderState(), event.getSubmitNodeCollector(), event.getPoseStack());
        RainCurtainRenderer.render(event.getLevelRenderState(), event.getSubmitNodeCollector(), event.getPoseStack());
    }
}
