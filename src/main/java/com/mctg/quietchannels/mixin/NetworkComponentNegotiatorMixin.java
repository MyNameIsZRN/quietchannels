package com.mctg.quietchannels.mixin;

import com.mctg.quietchannels.QuietChannelsConfig;
import net.minecraft.resources.ResourceLocation;
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
import java.util.stream.Collectors;

/**
 * 只放宽「客户端有、服务端没有」的必需通道：把对端缺失的通道在协商列表里剔除，
 * 而不是让 NeoForge 直接断开连接。其余通道（VSS 等）保持原生协商。
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
            final Set<ResourceLocation> serverIds = server.stream()
                    .map(NegotiableNetworkComponent::id).collect(Collectors.toSet());
            final Set<ResourceLocation> clientIds = client.stream()
                    .map(NegotiableNetworkComponent::id).collect(Collectors.toSet());

            final List<NegotiableNetworkComponent> filteredClient = client.stream()
                    .filter(c -> serverIds.contains(c.id()) || !namespaces.contains(c.id().getNamespace()))
                    .toList();

            List<NegotiableNetworkComponent> filteredServer = server;
            if (QuietChannelsConfig.dropMissingServerChannels()) {
                filteredServer = server.stream()
                        .filter(c -> clientIds.contains(c.id()) || !namespaces.contains(c.id().getNamespace()))
                        .toList();
            }

            if (filteredClient.size() == client.size() && filteredServer.size() == server.size()) {
                return; // 没有需要放宽的通道，走原生逻辑
            }

            if (QuietChannelsConfig.logRelaxed()) {
                quietchannels$LOG.info(
                        "[QuietChannels] relaxing channel negotiation: client {} -> {}, server {} -> {} (namespaces {})",
                        client.size(), filteredClient.size(), server.size(), filteredServer.size(), namespaces);
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
