package net.fentbusgaming.localweather.mixin;

import net.fentbusgaming.localweather.config.LocalWeatherConfig;
import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Saves zone weather whenever Minecraft saves the world, and re-reads the
 * config whenever {@code /reload} runs.
 *
 * {@code save} is what Yarn's names call the method that writes every
 * level to disk, and it is the one vanilla runs from all three places a world
 * gets saved: the autosave, {@code /save-all}, and shutdown. Hooking it rather
 * than a loader's server-stopping event means one hook on every loader, and a
 * zone file that is always written alongside the game time it was taken at.
 *
 * The descriptor is spelled out so a future overload cannot quietly pick up the
 * injection.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Inject(method = "save(ZZZ)Z", at = @At("RETURN"))
    private void localweather$saveZones(boolean suppressLog, boolean flush, boolean force,
                                        CallbackInfoReturnable<Boolean> cir) {
        WeatherZoneManager.save((MinecraftServer) (Object) this);
    }

    /**
     * {@code /reload} is already the command a server owner runs after editing
     * a datapack, and it is operator-only on every version, so the config rides
     * along with it rather than needing a command of its own.
     */
    @Inject(method = "reloadResources(Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"))
    private void localweather$reloadConfig(Collection<String> packs,
                                           CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        LoggerFactory.getLogger("localweather")
                .info("[LocalWeather] Reloaded config: {}", LocalWeatherConfig.reload());
    }
}
