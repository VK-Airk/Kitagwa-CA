package dev.purplepad.mixin;

import dev.purplepad.PartyChat;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class PartyPong {
    @Inject(method="handlePongResponse",at=@At("HEAD"),cancellable=true)
    private void kca$partyPing(ClientboundPongResponsePacket packet,CallbackInfo ci){
        if(PartyChat.acceptPong((ClientPacketListener)(Object)this,packet.time()))ci.cancel();
    }
}
