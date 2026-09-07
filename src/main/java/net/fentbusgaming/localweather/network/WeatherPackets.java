package net.fentbusgaming.localweather.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fentbusgaming.localweather.LocalWeatherMod;
import net.fentbusgaming.localweather.weather.StormCell;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;

/**
 * Handles all network packets for LocalWeather.
 *
 * Packet: WeatherUpdatePayload
 *   → Server → Client
 *   → Tells the client what weather type their current zone has and the transition progress.
 *
 * Packet: StormCellPayload
 *   → Server → Client
 *   → One moving single-cell thunderstorm: where it is, where it is heading,
 *     how wide its core is and how strong it currently is.
 */
public final class WeatherPackets {

    public static final Identifier WEATHER_UPDATE_ID =
            Identifier.fromNamespaceAndPath(LocalWeatherMod.MOD_ID, "weather_update");

    public static final Identifier WIND_UPDATE_ID =
            Identifier.fromNamespaceAndPath(LocalWeatherMod.MOD_ID, "wind_update");

    public static final Identifier STORM_CELL_ID =
            Identifier.fromNamespaceAndPath(LocalWeatherMod.MOD_ID, "storm_cell");

    private WeatherPackets() {}

    // -------------------------------------------------------------------------
    // Payload
    // -------------------------------------------------------------------------

    /**
     * Custom payload sent from server to client with zone weather info.
     *
     * Fields:
     *  - currentWeatherOrdinal: weather at the start of the transition
     *  - targetWeatherOrdinal: weather at the end of the transition
     *  - transitionProgress: float 0.0–1.0 (how far into transition we are)
     *  - zoneX, zoneZ: zone grid coordinates (for debugging / future use)
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

    /**
     * Wind direction payload sent from server to client.
     */
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

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    public static void registerServerPackets() {
        PayloadTypeRegistry.clientboundPlay().register(
                WeatherUpdatePayload.ID,
                WeatherUpdatePayload.CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
                WindUpdatePayload.ID,
                WindUpdatePayload.CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
                StormCellPayload.ID,
                StormCellPayload.CODEC
        );
        LocalWeatherMod.LOGGER.info("[LocalWeather] Registered S2C weather packets.");
    }

    // -------------------------------------------------------------------------
    // Sending
    // -------------------------------------------------------------------------

    public static void sendWeatherUpdate(ServerPlayer player, WeatherZone zone) {
        WeatherUpdatePayload payload = new WeatherUpdatePayload(
                                zone.getCurrentWeather().ordinal(),
                                zone.getTargetWeather().ordinal(),
                zone.getTransitionProgress(),
                zone.getZoneX(),
                zone.getZoneZ()
        );
        ServerPlayNetworking.send(player, payload);
    }

    public static void sendWindUpdate(ServerPlayer player, double windDirX, double windDirZ) {
        WindUpdatePayload payload = new WindUpdatePayload((float) windDirX, (float) windDirZ);
        ServerPlayNetworking.send(player, payload);
    }

    public static void sendStormCell(ServerPlayer player, StormCell cell) {
        StormCellPayload payload = new StormCellPayload(
                cell.getId(),
                cell.getX(),
                cell.getZ(),
                (float) cell.getVelX(),
                (float) cell.getVelZ(),
                cell.getRadius(),
                cell.getIntensity()
        );
        ServerPlayNetworking.send(player, payload);
    }
}
