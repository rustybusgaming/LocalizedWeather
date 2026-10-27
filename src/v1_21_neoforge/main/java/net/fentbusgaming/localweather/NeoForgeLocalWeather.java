package net.fentbusgaming.localweather;

import com.mojang.logging.LogUtils;
import net.fentbusgaming.localweather.network.ClientStormCellHandler;
import net.fentbusgaming.localweather.network.ClientWeatherHandler;
import net.fentbusgaming.localweather.network.NeoForgeWeatherSync;
import net.fentbusgaming.localweather.network.WeatherPayloads;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

import java.util.List;

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

    /**
     * Vanilla lets a player sleep through a daytime thunderstorm, because its
     * thunder darkens the sky; zone thunder leaves the world's sky alone, so
     * the time check refuses. Lift that refusal under zone thunder or a storm
     * cell. Vanilla only looks for nearby monsters after the time check passes,
     * so a refusal here meant it never looked: do that check here instead.
     */
    @SubscribeEvent
    public void onCanPlayerSleep(CanPlayerSleepEvent event) {
        if (event.getProblem() != Player.BedSleepingProblem.NOT_POSSIBLE_NOW) return;
        ServerPlayer player = event.getEntity();
        if (!(event.getLevel() instanceof ServerLevel level)
                || !WeatherZoneManager.isThunderingAt(level, player.getX(), player.getZ())) return;

        if (!player.isCreative()) {
            Vec3 bed = Vec3.atBottomCenterOf(event.getPos());
            List<Monster> monsters = level.getEntitiesOfClass(Monster.class,
                    new AABB(bed.x() - 8.0, bed.y() - 5.0, bed.z() - 8.0, bed.x() + 8.0, bed.y() + 5.0, bed.z() + 8.0),
                    monster -> monster.isPreventingPlayerRest(player));
            if (!monsters.isEmpty()) {
                event.setProblem(Player.BedSleepingProblem.NOT_SAFE);
                return;
            }
        }
        event.setProblem(null);
    }

    /** Keeps a player asleep through a daytime zone thunderstorm, for the same reason. */
    @SubscribeEvent
    public void onCanContinueSleeping(CanContinueSleepingEvent event) {
        if (event.getProblem() != Player.BedSleepingProblem.NOT_POSSIBLE_NOW) return;
        if (event.getEntity() instanceof Player player
                && player.level() instanceof ServerLevel level
                && WeatherZoneManager.isThunderingAt(level, player.getX(), player.getZ())) {
            event.setContinueSleeping(true);
        }
    }
}
