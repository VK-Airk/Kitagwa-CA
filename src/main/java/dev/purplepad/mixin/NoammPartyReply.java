package dev.purplepad.mixin;

import dev.purplepad.PartyChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.github.noamm9.utils.ChatUtils",remap=false)
abstract class NoammPartyReply {
    @Inject(method="sendPartyMessage",at=@At("HEAD"),cancellable=true,require=0)
    private void kca$party(Object value,CallbackInfo ci){
        if(PartyChat.captureReply(String.valueOf(value)))ci.cancel();
    }
    @Inject(method={"sendCommand","sendMessage"},at=@At("HEAD"),cancellable=true,require=0)
    private void kca$route(String command, long delay, CallbackInfo ci){
        if(PartyChat.captureCommand(command))ci.cancel();
    }
}
