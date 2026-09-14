package net.fentbusgaming.localweather.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The three server-to-client payloads, and nothing about how they are carried.
 *
 * Custom payloads and stream codecs are vanilla types on every 26.x loader, so
 * the wire format lives here and each platform only supplies registration and
 * sending: Fabric through {@code PayloadTypeRegistry}/{@code ServerPlayNetworking},
 * NeoForge through {@code RegisterPayloadHandlersEvent}. Keeping the records in
 * one place is what lets a Fabric client talk to a NeoForge server and the other
 * way round — the bytes are identical because the codecs are the same code.
 */
public final class WeatherPayloads {

    public static final String MOD_ID = "localweather";

    public static final Identifier WEATHER_UPDATE_ID =
            Identifier.fromNamespaceAndPath(MOD_ID, "weather_update");

    public static final Identifier WIND_UPDATE_ID =
            Identifier.fromNamespaceAndPath(MOD_ID, "wind_update");

    public static final Identifier STORM_CELL_ID =
            Identifier.fromNamespaceAndPath(MOD_ID, "storm_cell");

    private WeatherPayloads() {}

    /**
     * Zone weather: what the zone is coming from, what it is going to, and how
     * far through the transition it is.
     *
     * Fields:
     *  - currentWeatherOrdinal: weather at the start of the transition
     *  - targetWeatherOrdinal: weather at the end of the transition
     *  - transitionProgress: float 0.0–1.0 (how far into transition we are)
     *  - zoneX, zoneZ: zone grid coordinates
     */
    public record WeatherUpdatePayload(
            int currentWeatherOrdinal,
            int targetWeatherOrdinal,
            float transitionProgress,
            int zoneX,
            int zoneZ
    ) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<WeatherUpdatePayload> ID =
                new CustomPacketPayload.Type<>(WEATHER_UPDATE_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, WeatherUpdatePayload> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, WeatherUpdatePayload::currentWeatherOrdinal,
                        ByteBufCodecs.VAR_INT, WeatherUpdatePayload::targetWeatherOrdinal,
                        ByteBufCodecs.FLOAT,   WeatherUpdatePayload::transitionProgress,
                        ByteBufCodecs.VAR_INT, WeatherUpdatePayload::zoneX,
                        ByteBufCodecs.VAR_INT, WeatherUpdatePayload::zoneZ,
                        WeatherUpdatePayload::new
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** The global wind direction the fronts and cloud drift follow. */
    public record WindUpdatePayload(
            float windDirX,
            float windDirZ
    ) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<WindUpdatePayload> ID =
                new CustomPacketPayload.Type<>(WIND_UPDATE_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, WindUpdatePayload> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.FLOAT, WindUpdatePayload::windDirX,
                        ByteBufCodecs.FLOAT, WindUpdatePayload::windDirZ,
                        WindUpdatePayload::new
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /**
     * A single moving thunderstorm cell.
     *
     * The velocity is sent alongside the position so the client can extrapolate
     * the cell between syncs — the rain wall and rain bands then travel smoothly
     * instead of stepping once a second.
     *
     * Cells are re-sent every sync; a cell the client stops hearing about is
     * retired on its own, so there is no separate removal packet.
     */
    public record StormCellPayload(
            int cellId,
            double x,
            double z,
            float velX,
            float velZ,
            float radius,
            float intensity
    ) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<StormCellPayload> ID =
                new CustomPacketPayload.Type<>(STORM_CELL_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, StormCellPayload> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, StormCellPayload::cellId,
                        ByteBufCodecs.DOUBLE,  StormCellPayload::x,
                        ByteBufCodecs.DOUBLE,  StormCellPayload::z,
                        ByteBufCodecs.FLOAT,   StormCellPayload::velX,
                        ByteBufCodecs.FLOAT,   StormCellPayload::velZ,
                        ByteBufCodecs.FLOAT,   StormCellPayload::radius,
                        ByteBufCodecs.FLOAT,   StormCellPayload::intensity,
                        StormCellPayload::new
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
