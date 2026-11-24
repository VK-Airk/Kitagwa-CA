package dev.purplepad.mixin;

import dev.purplepad.PartyChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="net.sbo.mod.utils.chat.Chat",remap=false)
abstract class SboPartyReply {
    @Inject(method="pc",at=@At("HEAD"),cancellable=true,require=0)
    private void kca$route(String text, CallbackInfo ci){
        if(PartyChat.captureReply(text))ci.cancel();
    }
}
