package com.mctg.quietchannels.mixin;

import com.mctg.quietchannels.QuietChannelsConfig;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.negotiation.NegotiableNetworkComponent;
import net.neoforged.neoforge.network.negotiation.NetworkComponentNegotiator;
import net.neoforged.neoforge.network.negotiation.NegotiationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 把配置命名空间（默认 {@code axiom}）的通道在协商前做**对称归一化**，让协商必然通过。
 *
 * <p>21.1.251 的校验规则（反编译自 neoforge-21.1.251-universal.jar）：
 * <pre>
 * validateComponent(left, right, side):
 *   if (left.flow 有 && right.flow 无) -&gt; failure flow.&lt;side&gt;.missing
 *   if (left.flow 有 && right.flow 有 && 不同) -&gt; failure flow.&lt;side&gt;.mismatch
 *   if (!fix(left.version, right.version)) -&gt; failure version.mismatch
 * </pre>
 * 实测的失败正是 {@code flow.client.missing}：<b>服务端</b>侧（Youer 把 AxiomPaper 插件注册的
 * Bukkit 通道桥接进 NeoForge 注册表）声明了 flow，而<b>客户端</b>侧（Connector 转译的 Axiom mod）
 * 没有声明 flow。</p>
 *
 * <p>因此这里对**两侧同 id** 的条目统一改写为
 * {@code (id, 固定版本串, Optional.empty(), optional=true)}：flow 两侧都为空 → 跳过 flow 校验；
 * 版本两侧相同 → 版本校验通过。单侧独有的条目只标记 optional，交给 NeoForge 自己的
 * {@code buildDisabledOptionalComponents} 丢弃。</p>
 *
 * <p><b>不能把这些通道从列表里剔除</b>：一旦剔除，NeoForge 会把服务端出站载荷降级成
 * {@code DiscardedPayload}，Youer 强转 {@code PluginsDiscardedPayload} 失败并踢人。
 * 另外这会话里两侧调用顺序在不同调用点可能相反，所以改写规则刻意做成对称的。</p>
 */
@Mixin(value = NetworkComponentNegotiator.class, remap = false)
public class NetworkComponentNegotiatorMixin {
    /** 归一化后两侧共用的版本串（只要相等即可通过 fix 校验）。 */
    private static final String quietchannels$NORMALIZED_VERSION = "quietchannels";

    private static final Logger quietchannels$LOG = LoggerFactory.getLogger("quietchannels");
    private static boolean quietchannels$active = false;

    @Inject(method = "negotiate", at = @At("HEAD"), cancellable = true, remap = false)
    private static void quietchannels$align(List<NegotiableNetworkComponent> server,
                                            List<NegotiableNetworkComponent> client,
                                            CallbackInfoReturnable<NegotiationResult> cir) {
        if (quietchannels$active || server == null || client == null || !QuietChannelsConfig.enabled()) {
            return;
        }
        final Set<String> namespaces = QuietChannelsConfig.namespaces();
        if (namespaces.isEmpty()) {
            return;
        }
        try {
            final Set<ResourceLocation> serverIds = new HashSet<>();
            for (NegotiableNetworkComponent s : server) {
                if (namespaces.contains(s.id().getNamespace())) {
                    serverIds.add(s.id());
                }
            }
            final Set<ResourceLocation> clientIds = new HashSet<>();
            for (NegotiableNetworkComponent c : client) {
                if (namespaces.contains(c.id().getNamespace())) {
                    clientIds.add(c.id());
                }
            }
            if (serverIds.isEmpty() && clientIds.isEmpty()) {
                return; // 该命名空间没有通道，走原生逻辑
            }

            final List<NegotiableNetworkComponent> alignedServer = new ArrayList<>(server.size());
            for (NegotiableNetworkComponent s : server) {
                if (!namespaces.contains(s.id().getNamespace())) {
                    alignedServer.add(s);
                } else if (clientIds.contains(s.id())) {
                    // 两侧都有：归一化，两侧完全一致
                    alignedServer.add(new NegotiableNetworkComponent(
                            s.id(), quietchannels$NORMALIZED_VERSION, Optional.empty(), true));
                } else {
                    // 只有服务端有：交给 NeoForge 的 optional 逻辑丢弃
                    alignedServer.add(new NegotiableNetworkComponent(s.id(), s.version(), s.flow(), true));
                }
            }

            final List<NegotiableNetworkComponent> alignedClient = new ArrayList<>(client.size());
            for (NegotiableNetworkComponent c : client) {
                if (!namespaces.contains(c.id().getNamespace())) {
                    alignedClient.add(c);
                } else if (serverIds.contains(c.id())) {
                    alignedClient.add(new NegotiableNetworkComponent(
                            c.id(), quietchannels$NORMALIZED_VERSION, Optional.empty(), true));
                } else {
                    alignedClient.add(new NegotiableNetworkComponent(c.id(), c.version(), c.flow(), true));
                }
            }

            if (alignedServer.equals(server) && alignedClient.equals(client)) {
                return;
            }

            if (QuietChannelsConfig.logRelaxed()) {
                quietchannels$LOG.info(
                        "[QuietChannels] normalizing namespaces {} : server {} components ({} matched), client {} components ({} matched)",
                        namespaces, alignedServer.size(), serverIds.size(), alignedClient.size(), clientIds.size());
            }

            quietchannels$active = true;
            try {
                cir.setReturnValue(NetworkComponentNegotiator.negotiate(alignedServer, alignedClient));
            } finally {
                quietchannels$active = false;
            }
        } catch (Throwable t) {
            quietchannels$LOG.warn("[QuietChannels] normalization failed, falling back to vanilla negotiation: {}", t.toString());
        }
    }
}
