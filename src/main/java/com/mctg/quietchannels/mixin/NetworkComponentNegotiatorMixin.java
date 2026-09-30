package com.mctg.quietchannels.mixin;

import com.mctg.quietchannels.QuietChannelsConfig;
import net.neoforged.neoforge.network.negotiation.NegotiableNetworkComponent;
import net.neoforged.neoforge.network.negotiation.NegotiationResult;
import net.neoforged.neoforge.network.negotiation.NetworkComponentNegotiator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;

/**
 * 在协商前，把指定命名空间的通道从**两侧**列表里整段剔除：
 * 缺失、流(flow)不匹配、版本不匹配这三类失败因此都不会发生。
 * 其它命名空间（VSS 等）保持 NeoForge 原生协商。
 */
@Mixin(value = NetworkComponentNegotiator.class, remap = false)
public class NetworkComponentNegotiatorMixin {
    private static final Logger quietchannels$LOG = LoggerFactory.getLogger("quietchannels");
    private static boolean quietchannels$active = false;

    @Inject(method = "negotiate", at = @At("HEAD"), cancellable = true, remap = false)
    private static void quietchannels$relax(List<NegotiableNetworkComponent> server,
                                            List<NegotiableNetworkComponent> client,
                                            CallbackInfoReturnable<NegotiationResult> cir) {
        if (quietchannels$active || client == null || server == null || !QuietChannelsConfig.enabled()) {
            return;
        }
        final Set<String> namespaces = QuietChannelsConfig.namespaces();
        if (namespaces.isEmpty()) {
            return;
        }
        try {
            final List<NegotiableNetworkComponent> filteredClient = client.stream()
                    .filter(c -> !namespaces.contains(c.id().getNamespace()))
                    .toList();
            final List<NegotiableNetworkComponent> filteredServer = server.stream()
                    .filter(c -> !namespaces.contains(c.id().getNamespace()))
                    .toList();

            if (filteredClient.size() == client.size() && filteredServer.size() == server.size()) {
                return; // 该命名空间没有任何通道，走原生逻辑
            }

            if (QuietChannelsConfig.logRelaxed()) {
                quietchannels$LOG.info(
                        "[QuietChannels] excluding namespaces {} from negotiation: client {} -> {}, server {} -> {}",
                        namespaces, client.size(), filteredClient.size(), server.size(), filteredServer.size());
            }

            quietchannels$active = true;
            try {
                cir.setReturnValue(NetworkComponentNegotiator.negotiate(filteredServer, filteredClient));
            } finally {
                quietchannels$active = false;
            }
        } catch (Throwable t) {
            quietchannels$LOG.warn("[QuietChannels] relaxation failed, falling back to vanilla negotiation: {}", t.toString());
        }
    }
}
