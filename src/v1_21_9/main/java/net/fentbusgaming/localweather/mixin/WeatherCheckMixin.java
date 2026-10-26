package net.fentbusgaming.localweather.mixin;

import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.loot.condition.WeatherCheckLootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes the {@code weather_check} condition read the zone it is checked in.
 *
 * The condition asks the world's one rain and thunder flags, which the zones
 * keep down, so it never passed. Channeling is the one players notice: its
 * lightning is gated on {@code weather_check} with thundering true, so a
 * trident never called lightning, even thrown into a zone thunderstorm. Loot
 * tables, advancements and datapack predicates that check the weather were
 * wrong the same way.
 *
 * Where the context has a position (Channeling's has the hit mob or the
 * lightning rod), the zone there answers, along with vanilla's own flag for a
 * server that lets vanilla weather run. Without a position there is no zone to
 * ask, so vanilla answers alone.
 */
@Mixin(WeatherCheckLootCondition.class)
public abstract class WeatherCheckMixin {

    @Redirect(method = "test(Lnet/minecraft/loot/context/LootContext;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;isRaining()Z"))
    private boolean localweather$rainFromZone(ServerWorld level, LootContext context) {
        if (level.isRaining()) return true;
        Vec3d origin = context.hasParameter(LootContextParameters.ORIGIN) ? context.get(LootContextParameters.ORIGIN) : null;
        return origin != null && WeatherZoneManager.isPrecipitatingAt(level, origin.x, origin.z);
    }

    @Redirect(method = "test(Lnet/minecraft/loot/context/LootContext;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;isThundering()Z"))
    private boolean localweather$thunderFromZone(ServerWorld level, LootContext context) {
        if (level.isThundering()) return true;
        Vec3d origin = context.hasParameter(LootContextParameters.ORIGIN) ? context.get(LootContextParameters.ORIGIN) : null;
        return origin != null && WeatherZoneManager.isThunderingAt(level, origin.x, origin.z);
    }
}
