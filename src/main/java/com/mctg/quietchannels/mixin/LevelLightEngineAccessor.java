package com.mctg.quietchannels.mixin;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露父类 {@link LevelLightEngine} 的 protected 字段 {@code levelHeightAccessor}。
 *
 * <p>不能直接在 {@code ThreadedLevelLightEngine} 的 mixin 里用 {@code @Shadow} 取它：
 * 该字段声明在父类上，Mixin 会报 «@Shadow field ... was not located in the target class»。</p>
 */
@Mixin(LevelLightEngine.class)
public interface LevelLightEngineAccessor {
    @Accessor("levelHeightAccessor")
    LevelHeightAccessor quietchannels$heightAccessor();
}
