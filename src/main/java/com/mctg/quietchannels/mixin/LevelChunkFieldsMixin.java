package com.mctg.quietchannels.mixin;

import com.mctg.quietchannels.QuietLocHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 给 {@code LevelChunk} 补上 Paper 补丁提供的 {@code public int locX} / {@code locZ} 字段。
 *
 * <p>背景（反编译确认）：</p>
 * <ul>
 *   <li>Paper 的区块系统重写给 {@code LevelChunk} 加了 {@code locX}/{@code locZ} 两个 int 字段；</li>
 *   <li>AxiomPaper 插件直接以字节码访问它们：
 *       {@code getfield net/minecraft/world/level/chunk/LevelChunk.locX:I}（还有 {@code locZ}）；</li>
 *   <li>Youer 的 Paper 补丁集**没有**这两个字段 → 字段解析失败 →
 *       "Class net.minecraft.world.level.chunk.LevelChunk does not have member field 'int locX'"
 *       → 插件在第一次处理方块改动时把玩家踢掉。</li>
 * </ul>
 *
 * <p>JVM 解析字段引用时会沿继承链查找，所以只要目标类上真的存在这两个同名字段，
 * 插件那条 {@code getfield} 就能解析成功。字段值由 {@code ChunkAccessPosMixin}
 * 在区块构造完成时按区块坐标写入。</p>
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkFieldsMixin implements QuietLocHolder {
    /** Paper 字段：区块 X 坐标（注意名字必须与 Paper 一致）。 */
    public int locX;

    /** Paper 字段：区块 Z 坐标。 */
    public int locZ;

    @Override
    public void quietchannels$setLoc(int x, int z) {
        this.locX = x;
        this.locZ = z;
    }
}
