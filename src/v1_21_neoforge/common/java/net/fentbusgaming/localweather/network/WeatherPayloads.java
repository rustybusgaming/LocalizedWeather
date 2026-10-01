package net.fentbusgaming.localweather.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The three server-to-client payloads for localized weather synchronization.
 */
public final class WeatherPayloads {

    public static final String MOD_ID = "localweather";

    public static final ResourceLocation WEATHER_UPDATE_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "weather_update");

    public static final ResourceLocation WIND_UPDATE_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "wind_update");

    public static final ResourceLocation STORM_CELL_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "storm_cell");

    private WeatherPayloads() {}

    /**
     * Zone weather: what the zone is coming from, what it is going to, and how
     * far through the transition it is.
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
                StreamCodec.of(
                        (buf, payload) -> {
                            ByteBufCodecs.VAR_INT.encode(buf, payload.cellId);
                            ByteBufCodecs.DOUBLE.encode(buf, payload.x);
                            ByteBufCodecs.DOUBLE.encode(buf, payload.z);
                            ByteBufCodecs.FLOAT.encode(buf, payload.velX);
                            ByteBufCodecs.FLOAT.encode(buf, payload.velZ);
                            ByteBufCodecs.FLOAT.encode(buf, payload.radius);
                            ByteBufCodecs.FLOAT.encode(buf, payload.intensity);
                        },
                        buf -> new StormCellPayload(
                                ByteBufCodecs.VAR_INT.decode(buf),
                                ByteBufCodecs.DOUBLE.decode(buf),
                                ByteBufCodecs.DOUBLE.decode(buf),
                                ByteBufCodecs.FLOAT.decode(buf),
                                ByteBufCodecs.FLOAT.decode(buf),
                                ByteBufCodecs.FLOAT.decode(buf),
                                ByteBufCodecs.FLOAT.decode(buf)
                        )
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
