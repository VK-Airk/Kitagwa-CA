package dev.purplepad.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.purplepad.RarityBackground;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "de.hysky.skyblocker.skyblock.storageoverlay.StorageOverlayScreen$BackpackWidget", remap = false)
abstract class SkyblockerStorageRarity {
    // Only cached pages use this call. Open pages already go through vanilla slots.
    @WrapOperation(method = "renderCustomBackpack", at = @At(value = "INVOKE",
            target = "Lde/hysky/skyblocker/skyblock/item/background/ItemBackgroundManager;drawBackgrounds(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V"), require = 0)
    private void kca$cachedSlot(ItemStack stack, GuiGraphicsExtractor graphics, int x, int y, Operation<Void> original) {
        RarityBackground.drawStorage(graphics, stack, x, y);
        original.call(stack, graphics, x, y);
    }
}
