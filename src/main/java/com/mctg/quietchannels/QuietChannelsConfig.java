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
            .comment("调整协商/补齐光照方法时是否在日志里打印一行。")
            .define("logRelaxed", true);

    private static final ModConfigSpec.IntValue RELIGHT_MAX_CHUNKS_PER_CALL = BUILDER
            .comment("Paper 的 starlight$serverRelightChunks 在本服务端缺失，本模组为其提供等价实现。",
                     "这里限制单次调用最多重光照多少个区块，防止大批量编辑造成卡顿（0 = 不限制）。",
                     "超出部分会被跳过（属于安全阀，正常编辑很少触发）。")
            .defineInRange("relightMaxChunksPerCall", 256, 0, 100000);

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

    /** 单次重光照调用的区块上限；<=0 表示不限制。 */
    public static int relightMaxChunksPerCall() {
        try {
            final int value = RELIGHT_MAX_CHUNKS_PER_CALL.get();
            return value <= 0 ? Integer.MAX_VALUE : value;
        } catch (Throwable t) {
            return 256;
        }
    }
}
