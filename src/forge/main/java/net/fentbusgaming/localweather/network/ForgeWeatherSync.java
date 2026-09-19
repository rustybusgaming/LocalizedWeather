package net.fentbusgaming.localweather.network;

import net.fentbusgaming.localweather.weather.StormCell;
import net.fentbusgaming.localweather.weather.WeatherSync;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.PacketDistributor;

/**
 * Forge's carrier for the payloads defined in {@link WeatherPayloads}.
 *
 * The payloads are vanilla types shared with Fabric and NeoForge, so the wire
 * format is identical on all three and only the send call differs.
 */
public final class ForgeWeatherSync implements WeatherSync {

    private final Channel<CustomPacketPayload> channel;

    public ForgeWeatherSync(Channel<CustomPacketPayload> channel) {
        this.channel = channel;
    }

    @Override
    public void sendZone(ServerPlayer player, WeatherZone zone) {
        send(player, new WeatherPayloads.WeatherUpdatePayload(
                zone.getCurrentWeather().ordinal(),
                zone.getTargetWeather().ordinal(),
                zone.getTransitionProgress(),
                zone.getZoneX(),
                zone.getZoneZ()));
    }

    @Override
    public void sendWind(ServerPlayer player, double windDirX, double windDirZ) {
        send(player, new WeatherPayloads.WindUpdatePayload((float) windDirX, (float) windDirZ));
    }

    @Override
    public void sendStormCell(ServerPlayer player, StormCell cell) {
        send(player, new WeatherPayloads.StormCellPayload(
                cell.getId(),
                cell.getX(),
                cell.getZ(),
                (float) cell.getVelX(),
                (float) cell.getVelZ(),
                cell.getRadius(),
                cell.getIntensity()));
    }

    private void send(ServerPlayer player, CustomPacketPayload payload) {
        channel.send(payload, PacketDistributor.PLAYER.with(player));
    }
}
