package net.fentbusgaming.localweather.network;

import net.fentbusgaming.localweather.weather.StormCell;
import net.fentbusgaming.localweather.weather.WeatherSync;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.minecraft.server.level.ServerPlayer;

/** Fabric's side of the one seam between the simulation and a loader. */
public final class FabricWeatherSync implements WeatherSync {

    @Override
    public void sendZone(ServerPlayer player, WeatherZone zone) {
        WeatherPackets.sendWeatherUpdate(player, zone);
    }

    @Override
    public void sendWind(ServerPlayer player, double windDirX, double windDirZ) {
        WeatherPackets.sendWindUpdate(player, windDirX, windDirZ);
    }

    @Override
    public void sendStormCell(ServerPlayer player, StormCell cell) {
        WeatherPackets.sendStormCell(player, cell);
    }
}
