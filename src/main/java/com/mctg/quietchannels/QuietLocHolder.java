package com.mctg.quietchannels;

/**
 * 由 {@code LevelChunkFieldsMixin} 实现的鸭子接口：给 {@code LevelChunk} 补出的
 * {@code locX}/{@code locZ} 字段赋值。
 *
 * <p>因为字段是运行时注入的，编译期无法直接访问，所以通过接口方法写入。</p>
 */
public interface QuietLocHolder {
    void quietchannels$setLoc(int x, int z);
}
