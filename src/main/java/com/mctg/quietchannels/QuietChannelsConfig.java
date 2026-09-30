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
            .comment("这些命名空间的通道会在协商前从**两侧**整段剔除（不参与协商），",
                     "默认只放 Axiom（服务端跑 Paper 插件、客户端跑 Connector 转译的 Fabric mod 时用）。")
            .defineList("namespaces", List.of("axiom"), o -> o instanceof String);

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

    public static boolean logRelaxed() {
        try {
            return LOG.get();
        } catch (Throwable t) {
            return true;
        }
    }
}
