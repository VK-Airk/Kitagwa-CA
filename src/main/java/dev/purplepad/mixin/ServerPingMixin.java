/* SPDX-License-Identifier: GPL-3.0-only
 * Server tick hook adapted from Synnerz/devonian 1.34.9.
 */
package dev.purplepad.mixin;

import dev.purplepad.PurplePadClient;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ServerPingMixin {
    @Inject(method = "handlePing", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
        shift = At.Shift.AFTER), require = 1)
    private void purplepad$serverTick(ClientboundPingPacket packet, CallbackInfo ci) {
        if (packet.getId() < 0) PurplePadClient.onPing(packet.getId());
    }
}
