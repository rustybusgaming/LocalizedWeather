package net.fentbusgaming.localweather.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fentbusgaming.localweather.network.WeatherPackets;
import net.fentbusgaming.localweather.weather.WeatherZone;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Registers /localweather commands for testing in creative.
 *
 * Usage:
 *   /localweather set <clear|rain|thunder|snow>           — set weather in your current zone
 *   /localweather set <clear|rain|thunder|snow> <seconds>  — set weather + duration
 *   /localweather info                                     — show your current zone info
 */
public final class WeatherCommand {

    private WeatherCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal("localweather")
                .requires(source -> source.hasPermissionLevel(2)) // creative / op
                .then(literal("set")
                    .then(argument("type", word())
                        .suggests((ctx, builder) -> {
                            for (WeatherZone.WeatherType t : WeatherZone.WeatherType.values()) {
                                builder.suggest(t.name().toLowerCase());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeSet(ctx, -1))
                        .then(argument("seconds", IntegerArgumentType.integer(1, 9999))
                            .executes(ctx -> executeSet(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))
                        )
                    )
                )
                .then(literal("info")
                    .executes(WeatherCommand::executeInfo)
                )
        );
    }

    private static int executeSet(CommandContext<ServerCommandSource> ctx, int seconds) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command can only be run by a player."));
            return 0;
        }

        String typeName = getString(ctx, "type").toUpperCase();
        WeatherZone.WeatherType type;
        try {
            type = WeatherZone.WeatherType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal("Unknown weather type: " + typeName
                    + ". Use: clear, rain, thunder, snow"));
            return 0;
        }

        ServerWorld world = player.getServerWorld();
        WeatherZone zone = WeatherZoneManager.getOrCreateZoneForPlayer(world, player);

        zone.setTargetWeather(type);
        if (seconds > 0) {
            zone.setWeatherDuration(seconds * 20); // convert to ticks
        } else {
            zone.setWeatherDuration(6000); // default 5 minutes
        }

        // Mark dirty so it syncs immediately
        ChunkPos chunkPos = player.getChunkPos();
        int zoneX = chunkPos.x >> 4;
        int zoneZ = chunkPos.z >> 4;
        WeatherZoneManager.markDirty(world.getRegistryKey(),
                ((long) zoneX << 32) | (zoneZ & 0xFFFFFFFFL));

        // Also send immediately to the player
        WeatherPackets.sendWeatherUpdate(player, zone);

        String durationText = seconds > 0 ? " for " + seconds + "s" : "";
        source.sendFeedback(
            () -> Text.literal("Set zone [" + zoneX + ", " + zoneZ + "] weather to "
                    + type.name() + durationText),
            false
        );
        return 1;
    }

    private static int executeInfo(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command can only be run by a player."));
            return 0;
        }

        ServerWorld world = player.getServerWorld();
        WeatherZone zone = WeatherZoneManager.getOrCreateZoneForPlayer(world, player);

        String info = "Zone [" + zone.getZoneX() + ", " + zone.getZoneZ() + "]: "
                + zone.getCurrentWeather().name()
                + " -> " + zone.getTargetWeather().name()
                + " | Transition: " + (int)(zone.getTransitionProgress() * 100) + "%"
                + " | Duration: " + (zone.getWeatherDuration() / 20) + "s remaining";

        source.sendFeedback(() -> Text.literal(info), false);
        return 1;
    }
}
