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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 让「服务端有、但注册信息与客户端对不上」的通道能通过协商，而不是把客户端踢掉。
 *
 * <p>场景：服务端只有 AxiomPaper 插件（无 Axiom mod）。Youer 会把插件注册的 Bukkit
 * 插件通道桥接进 NeoForge 的 payload 注册表，但这些桥接项没有声明 flow/version，
 * 于是与客户端（Connector 转译的 Axiom mod）声明的通道在协商时 flow 校验失败：
 * {@code neoforge.network.negotiation.failure.flow.client.missing}。</p>
 *
 * <p>本 mixin 对配置命名空间的通道做两件事：
 * <ol>
 *   <li>服务端那一侧的条目：若客户端有同 id 的条目，则采用客户端的 flow/version；</li>
 *   <li>两侧条目都标记为 optional —— 对端缺失时 NeoForge 会自行忽略，而不是报错。</li>
 * </ol>
 * <b>注意</b>：这些通道必须保留在协商结果里（不能剔除）。一旦剔除，NeoForge 会把
 * 服务端发出的载荷降级为 {@code DiscardedPayload}，而 Youer 的发送路径会把它强转成
 * 自己的 {@code PluginsDiscardedPayload} → ClassCastException → 踢人。</p>
 */
@Mixin(value = NetworkComponentNegotiator.class, remap = false)
public class NetworkComponentNegotiatorMixin {
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
            final Map<ResourceLocation, NegotiableNetworkComponent> clientById = new HashMap<>();
            for (NegotiableNetworkComponent c : client) {
                clientById.putIfAbsent(c.id(), c);
            }

            final List<NegotiableNetworkComponent> alignedServer = new ArrayList<>(server.size());
            for (NegotiableNetworkComponent s : server) {
                if (!namespaces.contains(s.id().getNamespace())) {
                    alignedServer.add(s);
                    continue;
                }
                final NegotiableNetworkComponent c = clientById.get(s.id());
                if (c == null) {
                    alignedServer.add(new NegotiableNetworkComponent(s.id(), s.version(), s.flow(), true));
                } else {
                    alignedServer.add(new NegotiableNetworkComponent(
                            s.id(),
                            c.version() != null ? c.version() : s.version(),
                            c.flow().isPresent() ? c.flow() : s.flow(),
                            true));
                }
            }

            final List<NegotiableNetworkComponent> alignedClient = new ArrayList<>(client.size());
            for (NegotiableNetworkComponent c : client) {
                alignedClient.add(namespaces.contains(c.id().getNamespace())
                        ? new NegotiableNetworkComponent(c.id(), c.version(), c.flow(), true)
                        : c);
            }

            if (alignedServer.equals(server) && alignedClient.equals(client)) {
                return; // 无需调整，走原生逻辑
            }

            if (QuietChannelsConfig.logRelaxed()) {
                quietchannels$LOG.info(
                        "[QuietChannels] aligning namespaces {} : server {} -> {} aligned, client {} -> {} optional",
                        namespaces, server.size(), alignedServer.size(), client.size(), alignedClient.size());
            }

            quietchannels$active = true;
            try {
                cir.setReturnValue(NetworkComponentNegotiator.negotiate(alignedServer, alignedClient));
            } finally {
                quietchannels$active = false;
            }
        } catch (Throwable t) {
            quietchannels$LOG.warn("[QuietChannels] alignment failed, falling back to vanilla negotiation: {}", t.toString());
        }
    }
}
