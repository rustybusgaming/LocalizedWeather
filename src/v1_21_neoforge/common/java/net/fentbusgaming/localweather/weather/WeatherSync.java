package net.fentbusgaming.localweather.weather;

import net.minecraft.server.level.ServerPlayer;

/**
 * Interface through which the simulation sends zone, wind and storm cell
 * updates to connected players.
 */
public interface WeatherSync {

    WeatherSync NONE = new WeatherSync() {
        @Override public void sendZone(ServerPlayer player, WeatherZone zone) {}
        @Override public void sendWind(ServerPlayer player, double windDirX, double windDirZ) {}
        @Override public void sendStormCell(ServerPlayer player, StormCell cell) {}
    };

    void sendZone(ServerPlayer player, WeatherZone zone);

    void sendWind(ServerPlayer player, double windDirX, double windDirZ);

    void sendStormCell(ServerPlayer player, StormCell cell);
}
