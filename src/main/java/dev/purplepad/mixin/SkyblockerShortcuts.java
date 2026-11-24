package dev.purplepad.mixin;

import dev.purplepad.Shortcuts;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="de.hysky.skyblocker.skyblock.shortcut.Shortcuts",remap=false)
abstract class SkyblockerShortcuts {
    @Inject(method="onKeyPressed",at=@At("HEAD"),cancellable=true,require=0)
    private static void kca$ownKeys(com.mojang.blaze3d.platform.InputConstants.Key key,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        if(Shortcuts.active())ci.cancel();
    }
    @Inject(method="modifyCommand",at=@At("HEAD"),cancellable=true,require=0)
    private static void kca$ownAliases(String command,CallbackInfoReturnable<String> result){
        if(Shortcuts.active())result.setReturnValue(command);
    }
}
