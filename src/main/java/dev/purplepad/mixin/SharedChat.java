package dev.purplepad.mixin;

import com.nucleus.client.NucleusClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class SharedChat {
    @Inject(method="handleSystemChat", at=@At("HEAD"))
    private void kca$readChat(ClientboundSystemChatPacket packet, CallbackInfo ci){
        if(!packet.overlay() && Minecraft.getInstance().isSameThread())
            NucleusClient.dispatchGameMessage(packet.content().getString());
    }
}
