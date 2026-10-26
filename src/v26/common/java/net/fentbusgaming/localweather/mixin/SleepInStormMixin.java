package net.fentbusgaming.localweather.mixin;

import net.fentbusgaming.localweather.weather.WeatherZoneManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
 *
 * Not registered for Forge, which replaces both checks with its
 * SleepingTimeCheckEvent; ForgeLocalWeather answers that event instead.
 */
@Mixin({Player.class, ServerPlayer.class})
public abstract class SleepInStormMixin {

    @Redirect(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/attribute/BedRule;canSleep(Lnet/minecraft/world/level/Level;)Z"))
    private boolean localweather$sleepThroughStorms(BedRule rule, Level level) {
        if (rule.canSleep(level)) return true;
        Player self = (Player) (Object) this;
        return rule.canSleep() == BedRule.Rule.WHEN_DARK
                && level instanceof ServerLevel server
                && WeatherZoneManager.isThunderingAt(server, self.getX(), self.getZ());
    }
}
