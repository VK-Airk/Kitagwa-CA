package dev.purplepad.mixin;

import dev.purplepad.PartyChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.odtheking.odin.utils.ChatUtilsKt",remap=false)
abstract class OdinPartyReply {
    @Inject(method="sendCommand",at=@At("HEAD"),cancellable=true,require=0)
    private static void kca$route(String command, CallbackInfo ci){
        if(PartyChat.captureCommand(command))ci.cancel();
    }
}
