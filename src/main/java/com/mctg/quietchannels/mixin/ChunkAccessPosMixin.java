package com.mctg.quietchannels.mixin;

import com.mctg.quietchannels.QuietLocHolder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 区块构造完成时，把区块坐标写进 {@link LevelChunkFieldsMixin} 补出的
 * {@code locX}/{@code locZ} 字段。
 *
 * <p>挂在 {@code ChunkAccess} 的构造器尾部（{@code LevelChunk} 的构造器都会先调用它），
 * 这样不用逐个匹配 {@code LevelChunk} 的多个构造器签名；实例是 {@code LevelChunk}
 * （实现了 {@link QuietLocHolder}）时才写入。</p>
 */
@Mixin(ChunkAccess.class)
public class ChunkAccessPosMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void quietchannels$setChunkLoc(ChunkPos pos,
                                           UpgradeData upgradeData,
                                           LevelHeightAccessor heightAccessor,
                                           Registry<Biome> biomes,
                                           long inhabitedTime,
                                           LevelChunkSection[] sections,
                                           BlendingData blendingData,
                                           CallbackInfo ci) {
        if (pos != null && this instanceof QuietLocHolder holder) {
            holder.quietchannels$setLoc(pos.x, pos.z);
        }
    }
}
