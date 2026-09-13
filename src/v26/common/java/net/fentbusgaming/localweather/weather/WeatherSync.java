package net.fentbusgaming.localweather.weather;

import net.minecraft.server.level.ServerPlayer;

/**
 * How the zone simulation reaches clients.
 *
 * The simulation itself is loader-agnostic — Minecraft 26.x is compiled against
 * Mojang's own names on both Fabric and NeoForge, so everything it touches is
 * common. Networking is not: each loader registers payloads its own way. This
 * is the one seam between them.
 */
public interface WeatherSync {

    /** A sink that sends nothing, for a platform with no networking wired up yet. */
    WeatherSync NONE = new WeatherSync() {
        @Override
        public void sendZone(ServerPlayer player, WeatherZone zone) {}

        @Override
        public void sendWind(ServerPlayer player, double windDirX, double windDirZ) {}

        @Override
        public void sendStormCell(ServerPlayer player, StormCell cell) {}
    };

    void sendZone(ServerPlayer player, WeatherZone zone);

    void sendWind(ServerPlayer player, double windDirX, double windDirZ);

    void sendStormCell(ServerPlayer player, StormCell cell);
}
