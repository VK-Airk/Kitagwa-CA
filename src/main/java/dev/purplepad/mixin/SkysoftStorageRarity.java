package dev.purplepad.mixin;

import dev.purplepad.RarityBackground;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.skysoft.features.inventory.storage.SkyblockerItemBackgrounds", remap = false)
abstract class SkysoftStorageRarity {
    // Skysoft calls this for both live and stored items, even without Skyblocker installed.
    @Inject(method = "draw(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/item/ItemStack;II)V", at = @At("HEAD"), require = 0)
    private void kca$storageSlot(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, CallbackInfo ci) {
        RarityBackground.drawStorage(graphics, stack, x, y);
    }
}
