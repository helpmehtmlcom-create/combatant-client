/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.events.Events;
import combatant.client.events.impl.PacketEvent;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.network.BlinkManager;
import combatant.client.util.proxy.ProxyNettyInstaller;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
@Mixin(Connection.class)
public class ConnectionMixin {

    @Unique
    private static final int MAX_BUNDLE_DEPTH = 5;

    @Inject(method = "genericsFtw", at = @At("HEAD"), cancellable = true)
    private static <T extends PacketListener> void combatant$onHandlePacket(Packet<T> packet, PacketListener listener, CallbackInfo ci) {
        if (packet == null) {
            DebugLog.warn("Received null packet in genericsFtw");
            ci.cancel();
            return;
        }
        if (BlinkManager.isSilentlyHandlingPackets()) return;
        if (!Events.BUS.hasListeners(PacketEvent.Receive.class)) return;

        try {
            PacketEvent.Receive event = new PacketEvent.Receive(packet);
            Events.BUS.post(event);
            if (event.isCancelled()) {
                ci.cancel();
                return;
            }

            if (packet instanceof ClientboundBundlePacket packs) {
                Set<Packet<?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
                seen.add(packs);
                combatant$unpackBundle(packs, seen, 0);
            }
        } catch (Throwable t) {
            DebugLog.error("Error intercepting incoming packet: %s", t, packet.getClass().getSimpleName());
        }
    }

    @Unique
    private static void combatant$unpackBundle(ClientboundBundlePacket bundle, Set<Packet<?>> seen, int depth) {
        if (depth > MAX_BUNDLE_DEPTH) {
            DebugLog.warn("Exceeded max bundle packet nesting depth (%d)", depth);
            return;
        }
        Iterable<Packet<?>> subPackets;
        try {
            subPackets = (Iterable) bundle.subPackets();
        } catch (Throwable t) {
            DebugLog.error("Failed to read subpackets from bundle packet", t);
            return;
        }
        if (subPackets == null) return;

        for (Packet<?> sub : subPackets) {
            if (sub == null) continue;
            if (!seen.add(sub)) {
                DebugLog.warn("Duplicate/circular packet detected in bundle: %s", sub.getClass().getSimpleName());
                continue;
            }
            try {
                PacketEvent.Receive subEvent = new PacketEvent.Receive(sub, false);
                Events.BUS.post(subEvent);
                if (sub instanceof ClientboundBundlePacket nestedBundle) {
                    combatant$unpackBundle(nestedBundle, seen, depth + 1);
                }
            } catch (Throwable t) {
                DebugLog.error("Error processing unpacked packet: %s", t, sub.getClass().getSimpleName());
            }
        }
    }

    @Inject(method = "genericsFtw", at = @At("TAIL"))
    private static <T extends PacketListener> void combatant$onHandlePacketPost(Packet<T> packet, PacketListener listener, CallbackInfo ci) {
        if (packet == null) return;
        if (BlinkManager.isSilentlyHandlingPackets()) return;
        if (!Events.BUS.hasListeners(PacketEvent.ReceivePost.class)) return;

        try {
            PacketEvent.ReceivePost event = new PacketEvent.ReceivePost(packet);
            Events.BUS.post(event);
            if (packet instanceof ClientboundBundlePacket packs) {
                Set<Packet<?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
                seen.add(packs);
                combatant$unpackBundlePost(packs, seen, 0);
            }
        } catch (Throwable t) {
            DebugLog.error("Error intercepting incoming packet post: %s", t, packet.getClass().getSimpleName());
        }
    }

    @Unique
    private static void combatant$unpackBundlePost(ClientboundBundlePacket bundle, Set<Packet<?>> seen, int depth) {
        if (depth > MAX_BUNDLE_DEPTH) {
            DebugLog.warn("Exceeded max bundle packet nesting depth in ReceivePost (%d)", depth);
            return;
        }
        Iterable<Packet<?>> subPackets;
        try {
            subPackets = (Iterable) bundle.subPackets();
        } catch (Throwable t) {
            DebugLog.error("Failed to read subpackets from bundle packet in ReceivePost", t);
            return;
        }
        if (subPackets == null) return;

        for (Packet<?> sub : subPackets) {
            if (sub == null) continue;
            if (!seen.add(sub)) {
                continue;
            }
            try {
                PacketEvent.ReceivePost subEvent = new PacketEvent.ReceivePost(sub, false);
                Events.BUS.post(subEvent);
                if (sub instanceof ClientboundBundlePacket nestedBundle) {
                    combatant$unpackBundlePost(nestedBundle, seen, depth + 1);
                }
            } catch (Throwable t) {
                DebugLog.error("Error processing unpacked packet in ReceivePost: %s", t, sub.getClass().getSimpleName());
            }
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void combatant$onSendPacketPre(Packet<?> packet, CallbackInfo ci) {
        if (packet == null) {
            DebugLog.warn("Attempted to send null packet");
            ci.cancel();
            return;
        }
        if (BlinkManager.isSilentlyHandlingPackets()) return;
        if (!Events.BUS.hasListeners(PacketEvent.Send.class)) return;
        try {
            PacketEvent.Send event = new PacketEvent.Send(packet);
            Events.BUS.post(event);
            if (event.isCancelled()) {
                ci.cancel();
            }
        } catch (Throwable t) {
            DebugLog.error("Error intercepting outgoing packet pre: %s", t, packet.getClass().getSimpleName());
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("RETURN"))
    private void combatant$onSendPacketPost(Packet<?> packet, CallbackInfo ci) {
        if (packet == null) return;
        if (BlinkManager.isSilentlyHandlingPackets()) return;
        if (!Events.BUS.hasListeners(PacketEvent.SendPost.class)) return;
        try {
            PacketEvent.SendPost event = new PacketEvent.SendPost(packet);
            Events.BUS.post(event);
        } catch (Throwable t) {
            DebugLog.error("Error intercepting outgoing packet post: %s", t, packet.getClass().getSimpleName());
        }
    }

    @Inject(method = "configurePacketHandler", at = @At("HEAD"))
    private void combatant$installProxyHandler(ChannelPipeline pipeline, CallbackInfo ci) {
        if (pipeline == null) return;
        try {
            ProxyNettyInstaller.install(pipeline);
        } catch (Throwable t) {
            DebugLog.error("Failed to install proxy handler", t);
        }
    }


}
