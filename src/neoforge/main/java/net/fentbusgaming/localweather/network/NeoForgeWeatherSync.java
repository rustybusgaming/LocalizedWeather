package net.fentbusgaming.localweather.network;

import net.fentbusgaming.localweather.weather.StormCell;
import net.fentbusgaming.localweather.weather.WeatherSync;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge's carrier for the payloads defined in {@link WeatherPayloads}.
 *
 * The payloads are vanilla types shared with Fabric, so the wire format is
 * identical on both loaders and only the send call differs.
 */
public final class NeoForgeWeatherSync implements WeatherSync {

    @Override
    public void sendZone(ServerPlayer player, WeatherZone zone) {
        PacketDistributor.sendToPlayer(player, new WeatherPayloads.WeatherUpdatePayload(
                zone.getCurrentWeather().ordinal(),
                zone.getTargetWeather().ordinal(),
                zone.getTransitionProgress(),
                zone.getZoneX(),
                zone.getZoneZ()
        ));
    }

    @Override
    public void sendWind(ServerPlayer player, double windDirX, double windDirZ) {
        PacketDistributor.sendToPlayer(player,
                new WeatherPayloads.WindUpdatePayload((float) windDirX, (float) windDirZ));
    }

    @Override
    public void sendStormCell(ServerPlayer player, StormCell cell) {
        PacketDistributor.sendToPlayer(player, new WeatherPayloads.StormCellPayload(
                cell.getId(),
                cell.getX(),
                cell.getZ(),
                (float) cell.getVelX(),
                (float) cell.getVelZ(),
                cell.getRadius(),
                cell.getIntensity()
        ));
    }
}
