/* SPDX-License-Identifier: GPL-3.0-only */
package dev.purplepad.mixin;

import dev.purplepad.PurplePadClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class StormChatMixin {
    @Inject(method = "handleRespawn", at = @At("TAIL"), require = 1)
    private void purplepad$worldChange(ClientboundRespawnPacket packet, CallbackInfo ci) {
        PurplePadClient.reset();
    }

    @Inject(method = "handleLogin", at = @At("TAIL"), require = 1)
    private void purplepad$login(ClientboundLoginPacket packet, CallbackInfo ci) {
        PurplePadClient.reset();
    }
}
