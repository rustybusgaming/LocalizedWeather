package net.fentbusgaming.localweather.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fentbusgaming.localweather.render.HailParticleRenderer;
import net.fentbusgaming.localweather.render.RainCurtainRenderer;
import net.fentbusgaming.localweather.render.StormCloudRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Submits the weather overlays on Forge.
 *
 * Fabric has {@code LevelRenderEvents.COLLECT_SUBMITS} and NeoForge has
 * {@code SubmitCustomGeometryEvent}; Forge 26.1.2 has no level-render event of
 * any kind, so the submit phase is reached by mixin instead.
 *
 * The injection point is deliberately {@code submitBlockDestroyAnimation} and
 * not the place Fabric API hooks. Fabric injects into the synthetic lambda
 * {@code lambda$addMainPass$0} at a {@code popPush("renderSolidFeatures")}
 * profiler call, which depends on both a compiler-generated lambda name and a
 * string literal — and Forge patches {@code LevelRenderer}, so its lambdas need
 * not be numbered the same. {@code submitBlockDestroyAnimation} is a named
 * method, is called unconditionally one statement earlier in the same sequence,
 * and takes exactly the three things the renderers need as parameters, so
 * nothing has to be captured or stashed.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRenderMixin {

    @Inject(method = "submitBlockDestroyAnimation", at = @At("RETURN"))
    private void localweather$submitWeather(PoseStack poseStack,
                                            SubmitNodeCollector collector,
                                            LevelRenderState levelState,
                                            CallbackInfo ci) {
        StormCloudRenderer.render(levelState, collector, poseStack);
        HailParticleRenderer.render(levelState, collector, poseStack);
        RainCurtainRenderer.render(levelState, collector, poseStack);
    }
}
