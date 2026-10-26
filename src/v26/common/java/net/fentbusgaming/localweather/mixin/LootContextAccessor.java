package net.fentbusgaming.localweather.mixin;

import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches a loot context's parameters. LootContext's own getter for an
 * optional parameter is {@code getOptionalParameter} on 26.1 and
 * {@code getOptional} on 26.3, so neither compiles against the whole 26.x
 * line; the field, {@code LootParams.contextMap()} and {@code ContextMap.has}
 * and {@code getOrThrow} are the same on every 26.x version.
 */
@Mixin(LootContext.class)
public interface LootContextAccessor {

    @Accessor("params")
    LootParams localweather$params();
}
