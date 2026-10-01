package net.fentbusgaming.localweather;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fentbusgaming.localweather.network.ClientStormCellHandler;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.fentbusgaming.localweather.render.HailParticleRenderer;
import net.fentbusgaming.localweather.render.RainCurtainRenderer;
import net.fentbusgaming.localweather.render.StormCloudRenderer;
import net.fentbusgaming.localweather.sound.DirectionalThunderSound;
import net.fentbusgaming.localweather.sound.WeatherSoundManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge's client wiring for 1.21.1.
 */
@Mod(value = NeoForgeLocalWeather.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeLocalWeatherClient {

    public NeoForgeLocalWeatherClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(this);
        NeoForgeLocalWeather.LOGGER.info("[LocalWeather] Client handlers, renderers and sounds registered (1.21.1).");
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        ClientWeatherHandler.clientTick(client);
        ClientStormCellHandler.clientTick(client);
        DirectionalThunderSound.clientTick(client);
        WeatherSoundManager.clientTick(client);
    }

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float tickDelta = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        StormCloudRenderer.render(poseStack, bufferSource, cam, tickDelta);
        HailParticleRenderer.render(poseStack, bufferSource, cam);
        RainCurtainRenderer.render(poseStack, bufferSource, cam, tickDelta);

        bufferSource.endBatch(RenderType.debugQuads());
    }

    @SubscribeEvent
    public void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE) return;

        float rain = ClientWeatherHandler.getRainDarkening();
        float thunder = ClientWeatherHandler.getThunderDarkening();
        float proximity = ClientWeatherHandler.getStormProximityDarkening();
        float factor = Math.max(rain, proximity);
        if (factor < 0.02f) return;

        float fogReduction = factor * (0.40f + thunder * 0.15f);
        float far = event.getFarPlaneDistance();
        float stormEnd = far * (1.0f - fogReduction);
        float stormStart = Math.min(event.getNearPlaneDistance() * (1.0f - factor * 0.25f), stormEnd - 10f);

        if (stormEnd < far) {
            event.setNearPlaneDistance(stormStart);
            event.setFarPlaneDistance(stormEnd);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (event.getCamera().getFluidInCamera() != FogType.NONE) return;

        float rain = ClientWeatherHandler.getRainDarkening();
        float thunder = ClientWeatherHandler.getThunderDarkening();
        float proximity = ClientWeatherHandler.getStormProximityDarkening();
        float factor = Math.max(rain, proximity);
        if (factor < 0.01f) return;

        float r = event.getRed();
        float g = event.getGreen();
        float b = event.getBlue();

        float weatherR, weatherG, weatherB;
        if (thunder > 0.3f) {
            weatherR = 0.40f - thunder * 0.15f;
            weatherG = 0.38f - thunder * 0.20f;
            weatherB = 0.50f;
        } else if (rain > 0.2f) {
            weatherR = 0.50f - rain * 0.10f;
            weatherG = 0.53f - rain * 0.08f;
            weatherB = 0.60f + rain * 0.10f;
        } else {
            weatherR = 0.70f - rain * 0.15f;
            weatherG = 0.72f - rain * 0.12f;
            weatherB = 0.75f - rain * 0.10f;
        }
        r = r + (weatherR - r) * factor;
        g = g + (weatherG - g) * factor;
        b = b + (weatherB - b) * factor;

        event.setRed(Math.clamp(r, 0f, 1f));
        event.setGreen(Math.clamp(g, 0f, 1f));
        event.setBlue(Math.clamp(b, 0f, 1f));
    }
}
