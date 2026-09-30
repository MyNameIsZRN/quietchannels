package com.mctg.quietchannels;

import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * 自读配置：{@code config/quietchannels.properties}（首次运行时写入默认值）。
 *
 * <p>刻意不使用 NeoForge 的 {@code ModConfigSpec}：COMMON 配置在客户端会被
 * {@code FileWatcher} 反复判定为「不正确」并重写，形成每秒一次的刷屏死循环
 * （实测 1.2.1 客户端日志 125 行全是同一句警告）。</p>
 *
 * <pre>
 * enabled = true
 * namespaces = axiom            # 逗号分隔
 * relightMaxChunksPerCall = 256 # 0 = 不限制
 * logRelaxed = true
 * </pre>
 */
public final class QuietChannelsConfig {
    private static final String FILE_NAME = "quietchannels.properties";

    private static volatile boolean loaded = false;
    private static boolean enabled = true;
    private static boolean logRelaxed = true;
    private static int relightMaxChunksPerCall = 256;
    private static Set<String> namespaces = Set.of("axiom");

    private QuietChannelsConfig() {
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        final Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        final Properties props = new Properties();
        boolean writeDefaults = false;
        try {
            if (Files.exists(path)) {
                try (InputStream in = Files.newInputStream(path)) {
                    props.load(in);
                }
            } else {
                writeDefaults = true;
            }
            enabled = Boolean.parseBoolean(props.getProperty("enabled", "true").trim());
            logRelaxed = Boolean.parseBoolean(props.getProperty("logRelaxed", "true").trim());
            relightMaxChunksPerCall = readInt(props.getProperty("relightMaxChunksPerCall", "256"), 256);
            namespaces = parseNamespaces(props.getProperty("namespaces", "axiom"));
            if (writeDefaults) {
                writeDefaults(path);
            }
        } catch (Throwable ignored) {
            // 读失败就保持默认值，不影响连接
        }
    }

    private static int readInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static Set<String> parseNamespaces(String raw) {
        final Set<String> set = new HashSet<>();
        for (String part : raw.split(",")) {
            final String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        return set.isEmpty() ? Set.of("axiom") : Set.copyOf(set);
    }

    private static void writeDefaults(Path path) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            final Properties out = new Properties();
            out.setProperty("enabled", String.valueOf(enabled));
            out.setProperty("namespaces", String.join(",", namespaces));
            out.setProperty("relightMaxChunksPerCall", String.valueOf(relightMaxChunksPerCall));
            out.setProperty("logRelaxed", String.valueOf(logRelaxed));
            try (OutputStream os = Files.newOutputStream(path)) {
                out.store(os, "quietchannels: 通道协商对齐的命名空间 / 重光照上限（改完重启生效）");
            }
        } catch (Throwable ignored) {
            // 写不进去也无所谓
        }
    }

    public static boolean enabled() {
        ensureLoaded();
        return enabled;
    }

    public static boolean logRelaxed() {
        ensureLoaded();
        return logRelaxed;
    }

    public static Set<String> namespaces() {
        ensureLoaded();
        return namespaces;
    }

    /** 单次重光照调用的区块上限；{@code <= 0} 表示不限制。 */
    public static int relightMaxChunksPerCall() {
        ensureLoaded();
        return relightMaxChunksPerCall <= 0 ? Integer.MAX_VALUE : relightMaxChunksPerCall;
    }
}
