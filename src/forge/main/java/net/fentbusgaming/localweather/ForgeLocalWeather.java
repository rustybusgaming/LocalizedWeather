package net.fentbusgaming.localweather;

import com.mojang.logging.LogUtils;
import net.fentbusgaming.localweather.network.ForgeWeatherSync;
import net.fentbusgaming.localweather.network.WeatherPayloads;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkProtocol;
import org.slf4j.Logger;

/**
 * Forge platform entry point.
 *
 * Minecraft 26.x is compiled against Mojang's own names on Fabric, NeoForge and
 * Forge alike, so the simulation, the client presentation and the mixins are all
 * shared rather than duplicated — this module compiles them straight out of the
 * Fabric tree. Only the glue below is platform-specific.
 */
@Mod(ForgeLocalWeather.MOD_ID)
public final class ForgeLocalWeather {

    public static final String MOD_ID = "localweather";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Forge builds its channels up front rather than registering payload types
     * against a global registry, so the three clientbound payloads are declared
     * here in one chain.
     *
     * {@code addMain} rather than {@code add}: it runs the handler on the client
     * thread, which is what Fabric's receivers and NeoForge's default do, and
     * what the client caches expect.
     *
     * The handlers are lambda bodies rather than method references so the
     * client-only classes they name are linked on first delivery, which never
     * happens on a dedicated server.
     */
    public static final Channel<CustomPacketPayload> CHANNEL = ChannelBuilder
            .named(Identifier.fromNamespaceAndPath(MOD_ID, "main"))
            .networkProtocolVersion(1)
            .optionalClient()
            .payloadChannel()
            .protocol(NetworkProtocol.PLAY)
            .clientbound()
            .addMain(WeatherPayloads.WeatherUpdatePayload.ID,
                    WeatherPayloads.WeatherUpdatePayload.CODEC,
                    (payload, context) -> net.fentbusgaming.localweather.network.ClientWeatherHandler
                            .handleWeatherUpdate(payload))
            .addMain(WeatherPayloads.WindUpdatePayload.ID,
                    WeatherPayloads.WindUpdatePayload.CODEC,
                    (payload, context) -> net.fentbusgaming.localweather.network.ClientWeatherHandler
                            .handleWindUpdate(payload))
            .addMain(WeatherPayloads.StormCellPayload.ID,
                    WeatherPayloads.StormCellPayload.CODEC,
                    (payload, context) -> net.fentbusgaming.localweather.network.ClientStormCellHandler
                            .handleStormCell(payload))
            .build();

    public ForgeLocalWeather() {
        LOGGER.info("[LocalWeather] Initializing Forge platform module");

        WeatherZoneManager.init(new ForgeWeatherSync(CHANNEL));

        // Forge 26.x split TickEvent's phases into separate event types, each
        // with its own static bus, so the phase check that used to live in the
        // handler is the choice of bus instead. Listeners go on that bus
        // directly: Forge's new EventBus rejects MinecraftForge.EVENT_BUS
        // .register() for a class holding a single listener, and the compiler
        // checks the event type this way where an annotated handler would only
        // fail at load.
        //
        // Driven from the overworld's level tick rather than ServerTickEvent so
        // the simulation advances once per server tick and not once per
        // dimension.
        TickEvent.LevelTickEvent.Post.BUS.addListener(this::onLevelTickPost);

        // Forge's @Mod carries no dist attribute, unlike NeoForge's, so the
        // client half is reached through an explicit check. Naming the class
        // only inside this branch keeps it, and everything it touches, off a
        // dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeLocalWeatherClient.init();
        }
    }

    private void onLevelTickPost(TickEvent.LevelTickEvent.Post event) {
        if (!(event.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        if (server == null || level != server.overworld()) return;
        WeatherZoneManager.tick(server);
    }
}
