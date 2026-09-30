package com.mctg.quietchannels;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Set;

/** 客户端配置。 */
public final class QuietChannelsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("总开关。关闭后完全使用 NeoForge 原生协商行为。")
            .define("enabled", true);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> NAMESPACES = BUILDER
            .comment("只对这些命名空间的通道做放宽：对端缺失时从协商列表里剔除，而不是断开连接。",
                     "默认只放 Axiom（服务端跑 Paper 插件、客户端跑 Connector 转译的 Fabric mod 时用）。")
            .defineList("namespaces", List.of("axiom"), o -> o instanceof String);

    private static final ModConfigSpec.BooleanValue DROP_MISSING_SERVER_CHANNELS = BUILDER
            .comment("是否同样剔除「服务端有、客户端没有」的通道（默认 false，通常不需要）。")
            .define("dropMissingServerChannels", false);

    private static final ModConfigSpec.BooleanValue LOG = BUILDER
            .comment("剔除通道时是否在日志里打印一行。")
            .define("logRelaxed", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private QuietChannelsConfig() {
    }

    public static boolean enabled() {
        try {
            return ENABLED.get();
        } catch (Throwable t) {
            return true;
        }
    }

    public static Set<String> namespaces() {
        try {
            return Set.copyOf(NAMESPACES.get());
        } catch (Throwable t) {
            return Set.of("axiom");
        }
    }

    public static boolean dropMissingServerChannels() {
        try {
            return DROP_MISSING_SERVER_CHANNELS.get();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean logRelaxed() {
        try {
            return LOG.get();
        } catch (Throwable t) {
            return true;
        }
    }
}
