package com.mctg.quietchannels;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Quiet Channels — 让「服务端只有 Paper 插件、客户端有 Connector 转译的 Fabric mod」这条
 * 组合能在 NeoForge 系服务端（含 Youer 混合端）上跑通。
 *
 * <p>服务端与客户端都要装。它做两件事：</p>
 * <ol>
 *   <li><b>通道协商对齐</b>：对配置命名空间（默认 {@code axiom}）的通道，服务端侧采用客户端
 *       声明的 flow/version、两侧标记 optional，使协商通过；<b>不剔除</b>这些通道——
 *       剔除会让 NeoForge 把出站载荷降级成 {@code DiscardedPayload}，Youer 强转
 *       {@code PluginsDiscardedPayload} 失败并踢人。</li>
 *   <li><b>补上 Youer 缺失的 {@code starlight$serverRelightChunks}</b>：AxiomPaper 每 tick
 *       调用它，Youer 没有该方法 → 每 tick 抛 {@code NoSuchMethodError}（实测 ≈20 次/秒）。</li>
 * </ol>
 *
 * <p>配置不走 NeoForge 的 config 系统（那会让客户端的 FileWatcher 反复"修正"配置文件造成
 * 死循环），改为自读 {@code config/quietchannels.properties}。</p>
 */
@Mod(QuietChannels.MOD_ID)
public class QuietChannels {
    public static final String MOD_ID = "quietchannels";

    public QuietChannels(IEventBus modEventBus, ModContainer modContainer) {
        // 配置由 QuietChannelsConfig 自行读取 config/quietchannels.properties
    }
}
