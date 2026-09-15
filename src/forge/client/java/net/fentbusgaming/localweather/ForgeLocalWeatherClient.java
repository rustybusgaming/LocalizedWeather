package net.fentbusgaming.localweather;

import net.fentbusgaming.localweather.network.ClientStormCellHandler;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.fentbusgaming.localweather.sound.DirectionalThunderSound;
import net.fentbusgaming.localweather.sound.WeatherSoundManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;

/**
 * Forge's client wiring, and the mirror of Fabric's {@code LocalWeatherClient}
 * and {@code NeoForgeLocalWeatherClient}.
 *
 * Everything it drives — the zone cache, the storm-cell cache, the renderers and
 * the sounds — is loader-agnostic and lives in {@code src/v26/clientcommon},
 * shared with both other loaders. This class only says which Forge event calls
 * what.
 *
 * Reached only from a {@code Dist.CLIENT} branch in {@link ForgeLocalWeather},
 * so neither this class nor anything it names loads on a dedicated server.
 *
 * The renderers are not wired here: Forge 26.1.2 has no level-render event at
 * all, so they are driven from a mixin instead. See {@code LevelRenderMixin}.
 */
public final class ForgeLocalWeatherClient {

    private ForgeLocalWeatherClient() {}

    public static void init() {
        TickEvent.ClientTickEvent.Post.BUS.addListener(ForgeLocalWeatherClient::onClientTickPost);
        ForgeLocalWeather.LOGGER.info("[LocalWeather] Client handlers and sounds registered.");
    }

    private static void onClientTickPost(TickEvent.ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        ClientWeatherHandler.clientTick(client);
        ClientStormCellHandler.clientTick(client);
        DirectionalThunderSound.clientTick(client);
        WeatherSoundManager.clientTick(client);
    }
}
