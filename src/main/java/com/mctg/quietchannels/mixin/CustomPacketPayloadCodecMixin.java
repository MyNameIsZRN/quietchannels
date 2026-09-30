package com.mctg.quietchannels.mixin;

import com.mctg.quietchannels.QuietChannelsConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 修 Youer 自定义载荷编码时的类型不匹配（只作用于服务端）。
 *
 * <p>机制（反编译确认）：</p>
 * <ul>
 *   <li>Youer 把 {@code DiscardedPayload} 改造成「实现 {@code PluginsPayload}、带 data 字段」；</li>
 *   <li>AxiomPaper 这类插件用 Paper 风格 API 直接 {@code new ClientboundCustomPayloadPacket(DiscardedPayload)} 发送；</li>
 *   <li>而 Youer 为「插件通道」注册的 codec 是 {@code StreamCodec<B, PluginsDiscardedPayload>}（
 *       {@code PluginsPayload.codec(...)} 里编译期插入了 {@code checkcast PluginsDiscardedPayload}）；</li>
 *   <li>→ 编码时 {@code ClassCastException: DiscardedPayload cannot be cast to PluginsDiscardedPayload}
 *       → 包发不出去 → 玩家被踢（实测：进服 5 秒后 {@code EncoderException}）。</li>
 * </ul>
 *
 * <p>修法：在 {@code CustomPacketPayload$1#writeCap} 的 HEAD 处拦截——只要载荷是
 * {@code DiscardedPayload}（即 Youer 那种带数据的），就按 Youer {@code PluginsPayload.dcodec}
 * 的语义直接写 {@code id + 原始字节}，不经过那个按 {@code PluginsDiscardedPayload} 定型的 codec。
 * 数据通过反射读取（{@code data()} / {@code getData()} / 字段 {@code data}），
 * 因为 {@code PluginsPayload} 是 Youer 私有的运行时接口，编译期不存在。</p>
 */
@Mixin(targets = "net.minecraft.network.protocol.common.custom.CustomPacketPayload$1")
public class CustomPacketPayloadCodecMixin {
    @Unique
    private static final Logger quietchannels$LOG = LoggerFactory.getLogger("quietchannels");
    @Unique
    private static boolean quietchannels$loggedOnce = false;

    @Inject(method = "writeCap", at = @At("HEAD"), cancellable = true, remap = false)
    private void quietchannels$writeYouerPluginPayload(FriendlyByteBuf buf,
                                                       CustomPacketPayload.Type<?> type,
                                                       CustomPacketPayload payload,
                                                       CallbackInfo ci) {
        if (!QuietChannelsConfig.enabled() || !(payload instanceof DiscardedPayload)) {
            return;
        }
        try {
            final ByteBuf data = quietchannels$extractData(payload);
            buf.writeResourceLocation(type.id());
            if (data != null) {
                final int size = data.readableBytes();
                buf.writeBytes(data.duplicate());
                if (!quietchannels$loggedOnce) {
                    quietchannels$loggedOnce = true;
                    quietchannels$LOG.info("[QuietChannels] encoded plugin payload '{}' raw ({} bytes) to bypass Youer codec mismatch",
                            type.id(), size);
                }
            } else {
                quietchannels$LOG.warn("[QuietChannels] payload '{}' has no data; sending id only", type.id());
            }
            ci.cancel();
        } catch (Throwable t) {
            quietchannels$LOG.warn("[QuietChannels] raw payload write failed for '{}': {}", type.id(), t.toString());
        }
    }

    /** 反射取数据：Youer 的 DiscardedPayload 有 data() / getData() / 字段 data。 */
    @Unique
    private static ByteBuf quietchannels$extractData(Object payload) {
        for (String name : new String[]{"data", "getData", "getSlicedData"}) {
            try {
                final Method method = payload.getClass().getMethod(name);
                final Object value = method.invoke(payload);
                if (value instanceof ByteBuf buffer) {
                    return buffer;
                }
            } catch (Throwable ignored) {
                // 试下一个
            }
        }
        try {
            final Field field = payload.getClass().getDeclaredField("data");
            field.setAccessible(true);
            final Object value = field.get(payload);
            if (value instanceof ByteBuf buffer) {
                return buffer;
            }
        } catch (Throwable ignored) {
            // 放弃
        }
        return null;
    }
}
