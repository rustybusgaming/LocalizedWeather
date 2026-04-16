package net.fentbusgaming.localweather.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fentbusgaming.localweather.LocalWeatherMod;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Handles all network packets for LocalWeather.
 *
 * Packet: WeatherUpdatePayload
 *   → Server → Client
 *   → Tells the client what weather type their current zone has and the transition progress.
 */
public final class WeatherPackets {

    public static final Identifier WEATHER_UPDATE_ID =
            Identifier.of(LocalWeatherMod.MOD_ID, "weather_update");

    private WeatherPackets() {}

    // -------------------------------------------------------------------------
    // Payload
    // -------------------------------------------------------------------------

    /**
     * Custom payload sent from server to client with zone weather info.
     *
     * Fields:
     *  - weatherOrdinal: ordinal of WeatherZone.WeatherType (current effective weather)
     *  - transitionProgress: float 0.0–1.0 (how far into transition we are)
     *  - zoneX, zoneZ: zone grid coordinates (for debugging / future use)
     */
    public record WeatherUpdatePayload(
            int weatherOrdinal,
            float transitionProgress,
            int zoneX,
            int zoneZ
    ) implements CustomPayload {

        public static final CustomPayload.Id<WeatherUpdatePayload> ID =
                new CustomPayload.Id<>(WEATHER_UPDATE_ID);

        public static final PacketCodec<RegistryByteBuf, WeatherUpdatePayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.VAR_INT, WeatherUpdatePayload::weatherOrdinal,
                        PacketCodecs.FLOAT,   WeatherUpdatePayload::transitionProgress,
                        PacketCodecs.VAR_INT, WeatherUpdatePayload::zoneX,
                        PacketCodecs.VAR_INT, WeatherUpdatePayload::zoneZ,
                        WeatherUpdatePayload::new
                );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    public static void registerServerPackets() {
        PayloadTypeRegistry.playS2C().register(
                WeatherUpdatePayload.ID,
                WeatherUpdatePayload.CODEC
        );
        LocalWeatherMod.LOGGER.info("[LocalWeather] Registered S2C weather packet.");
    }

    // -------------------------------------------------------------------------
    // Sending
    // -------------------------------------------------------------------------

    public static void sendWeatherUpdate(ServerPlayerEntity player, WeatherZone zone) {
        WeatherUpdatePayload payload = new WeatherUpdatePayload(
                zone.getEffectiveWeather().ordinal(),
                zone.getTransitionProgress(),
                zone.getZoneX(),
                zone.getZoneZ()
        );
        ServerPlayNetworking.send(player, payload);
    }
}
