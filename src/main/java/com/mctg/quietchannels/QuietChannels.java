package com.mctg.quietchannels;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * Quiet Channels — 客户端侧放宽 NeoForge 的 payload 通道协商。
 *
 * <p>服务端与客户端都要装：NeoForge 的通道协商在服务端执行（NetworkRegistry
 * 对客户端上报的通道列表调用 negotiate，失败即踢人），客户端只负责显示失败原因。
 *
 * <p>背景：在 NeoForge 系服务端（含 Youer 混合端）上，客户端通过 Sinytra Connector 加载的
 * Fabric 模组（如 Axiom）会把它的 payload 通道注册成「必需」。如果服务端没有同名 mod，
 * 协商失败 → 客户端在配置阶段被断开（"客户端希望传输载荷，但服务器端不支持"）。</p>
 *
 * <p>本模组把「对端缺失的通道」在指定命名空间内**从协商列表中剔除**，而不是中止连接：
 * 其余通道（VSS 等）照常协商，缺失的通道在客户端侧表现为「服务端没有该 mod」，
 * 从而让 Axiom 客户端回退到它的 Paper 插件协议。</p>
 */
@Mod(QuietChannels.MOD_ID)
public class QuietChannels {
    public static final String MOD_ID = "quietchannels";

    public QuietChannels(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, QuietChannelsConfig.SPEC);
    }
}
