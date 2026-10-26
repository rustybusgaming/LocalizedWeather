package net.fentbusgaming.localweather.mixin;

import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.attribute.BedRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets a player sleep through a daytime zone thunderstorm, as vanilla does
 * through a global one.
 *
 * Vanilla's bed rule allows sleep "when dark", and its own thunder darkens the
 * world's sky enough to count. Zone thunder leaves that sky alone, so a player
 * standing in a thunderstorm could neither get into bed nor stay in one. The
 * rule is asked in two places, getting into bed and every tick in it, and both
 * now also say yes under zone thunder or a storm cell. Vanilla's other checks,
 * monsters nearby among them, still run. A rule that is not "when dark"
 * (always, or never as in the Nether) is left exactly as it was.
 *
 * The redirect matches the call in any method rather than naming one: on
 * NeoForge the getting-into-bed check lives in a lambda inside the method
 * vanilla has it in.
 */
@Mixin({PlayerEntity.class, ServerPlayerEntity.class})
public abstract class SleepInStormMixin {

    @Redirect(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/attribute/BedRule;canSleep(Lnet/minecraft/world/World;)Z"))
    private boolean localweather$sleepThroughStorms(BedRule rule, World world) {
        if (rule.canSleep(world)) return true;
        PlayerEntity self = (PlayerEntity) (Object) this;
        return rule.canSleep() == BedRule.Condition.WHEN_DARK
                && world instanceof ServerWorld server
                && WeatherZoneManager.isThunderingAt(server, self.getX(), self.getZ());
    }
}
