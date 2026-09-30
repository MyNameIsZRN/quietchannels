package com.mctg.quietchannels.mixin;

import com.mctg.quietchannels.QuietChannelsConfig;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 给 {@link ThreadedLevelLightEngine} 补上 Paper/Starlight 才有的
 * {@code starlight$serverRelightChunks(Collection, Consumer, IntConsumer)} 方法。
 *
 * <p>背景：AxiomPaper 插件在 Youer 上每 tick 都会调用这个方法对编辑过的区块做重光照。
 * Youer 是 Paper 的重实现但**没有打 Starlight 补丁**，方法不存在，于是每 tick 抛
 * {@code NoSuchMethodError}（实测 ≈20 次/秒、104k 条/87 分钟、日志 200MB+），
 * 而且重光照从来没真正执行过。</p>
 *
 * <p>这里以「补方法」的方式提供等价实现：对每个区块的每个 section 调
 * {@code updateSectionStatus(..., false)}（世界编辑类插件通用的强制重光照手法），
 * 这样调用点能正常解析，重光照也真的发生。</p>
 */
@Mixin(ThreadedLevelLightEngine.class)
public abstract class ThreadedLevelLightEngineMixin {
    @Unique
    private static final Logger quietchannels$LOG = LoggerFactory.getLogger("quietchannels");
    @Unique
    private static long quietchannels$lastSkippedLog = 0L;

    @Shadow
    public abstract void updateSectionStatus(SectionPos sectionPos, boolean isLightEnabled);

    /**
     * 与 Paper 的 {@code starlight$serverRelightChunks} 同签名、同语义（返回处理的区块数）。
     */
    @Unique
    public int starlight$serverRelightChunks(Collection<ChunkPos> chunks,
                                             Consumer<ChunkPos> onChunkRelit,
                                             IntConsumer onCount) {
        if (chunks == null || chunks.isEmpty()) {
            if (onCount != null) {
                onCount.accept(0);
            }
            return 0;
        }

        final int limit = QuietChannelsConfig.relightMaxChunksPerCall();
        final LevelHeightAccessor heightAccessor =
                ((LevelLightEngineAccessor) (Object) this).quietchannels$heightAccessor();
        final int sections = heightAccessor != null ? heightAccessor.getSectionsCount() : 24;
        int done = 0;
        try {
            for (ChunkPos pos : chunks) {
                if (done >= limit) {
                    break;
                }
                for (int index = 0; index < sections; index++) {
                    final int sectionY = heightAccessor != null
                            ? heightAccessor.getSectionYFromSectionIndex(index)
                            : index;
                    this.updateSectionStatus(SectionPos.of(pos, sectionY), false);
                }
                if (onChunkRelit != null) {
                    onChunkRelit.accept(pos);
                }
                done++;
            }
        } catch (Throwable t) {
            quietchannels$LOG.warn("[QuietChannels] serverRelightChunks fallback failed: {}", t.toString());
        }

        final int skipped = chunks.size() - done;
        if (skipped > 0) {
            final long now = System.currentTimeMillis();
            if (now - quietchannels$lastSkippedLog > 30_000L) {
                quietchannels$lastSkippedLog = now;
                quietchannels$LOG.warn("[QuietChannels] relight queue cap hit: relit {}, skipped {} (raise relightMaxChunksPerCall if needed)",
                        done, skipped);
            }
        }
        if (onCount != null) {
            onCount.accept(done);
        }
        return done;
    }
}
