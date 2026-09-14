package net.fentbusgaming.localweather.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fentbusgaming.localweather.LocalWeatherMod;
import net.fentbusgaming.localweather.weather.StormCell;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric's carrier for the payloads defined in {@link WeatherPayloads}.
 *
 * The payloads themselves are vanilla types and live in the common tree, so
 * this class is only the Fabric-specific half: registering the three
 * clientbound types and putting them on the wire.
 */
public final class WeatherPackets {

    private WeatherPackets() {}

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    public static void registerServerPackets() {
        PayloadTypeRegistry.clientboundPlay().register(
                WeatherPayloads.WeatherUpdatePayload.ID,
                WeatherPayloads.WeatherUpdatePayload.CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
                WeatherPayloads.WindUpdatePayload.ID,
                WeatherPayloads.WindUpdatePayload.CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
                WeatherPayloads.StormCellPayload.ID,
                WeatherPayloads.StormCellPayload.CODEC
        );
        LocalWeatherMod.LOGGER.info("[LocalWeather] Registered S2C weather packets.");
    }

    // -------------------------------------------------------------------------
    // Sending
    // -------------------------------------------------------------------------

    public static void sendWeatherUpdate(ServerPlayer player, WeatherZone zone) {
        ServerPlayNetworking.send(player, new WeatherPayloads.WeatherUpdatePayload(
                zone.getCurrentWeather().ordinal(),
                zone.getTargetWeather().ordinal(),
                zone.getTransitionProgress(),
                zone.getZoneX(),
                zone.getZoneZ()
        ));
    }

    public static void sendWindUpdate(ServerPlayer player, double windDirX, double windDirZ) {
        ServerPlayNetworking.send(player,
                new WeatherPayloads.WindUpdatePayload((float) windDirX, (float) windDirZ));
    }

    public static void sendStormCell(ServerPlayer player, StormCell cell) {
        ServerPlayNetworking.send(player, new WeatherPayloads.StormCellPayload(
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
