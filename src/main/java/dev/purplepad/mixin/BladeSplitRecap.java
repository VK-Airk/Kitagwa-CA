package dev.purplepad.mixin;

import dev.purplepad.RunStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="blade.addon.utils.dungeon.Phase",remap=false)
abstract class BladeSplitRecap {
    @Inject(method="printSplits()V",at=@At("HEAD"),cancellable=true,require=0)
    private static void kca$hideEndReport(CallbackInfo ci){
        if(RunStats.hideBladeRecap())ci.cancel();
    }
}
