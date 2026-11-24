package dev.purplepad.mixin;
import dev.purplepad.RarityBackground;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="de.hysky.skyblocker.skyblock.item.background.adders.ItemRarityBackground",remap=false)
abstract class SkyblockerRarity {
    @Inject(method="isEnabled",at=@At("HEAD"),cancellable=true,require=0)
    private void kca$ownBackground(CallbackInfoReturnable<Boolean> ci){
        if(RarityBackground.active())ci.setReturnValue(false);
    }
}
