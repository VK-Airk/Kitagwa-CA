package dev.purplepad.mixin;
import dev.purplepad.RarityBackground;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.ChatFormatting;
import net.minecraft.world.inventory.ChestMenu;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
@Mixin(AbstractContainerScreen.class)
abstract class RaritySlots {
    @Unique private Boolean kca$storageScreen;

    @Unique private boolean kca$isStorageScreen(){
        if(kca$storageScreen==null){
            AbstractContainerScreen<?> screen=(AbstractContainerScreen<?>)(Object)this;
            boolean storage=false;
            if(screen.getMenu() instanceof ChestMenu){
                String title=ChatFormatting.stripFormatting(screen.getTitle().getString()).strip();
                storage=title.equals("Storage") || title.equals("Ender Chest")
                        || title.startsWith("Ender Chest ") || (title.contains("Backpack") && title.contains("(Slot #"));
            }
            kca$storageScreen=storage;
        }
        return kca$storageScreen;
    }

    @Inject(method="extractSlot",at=@At("HEAD"))
    private void kca$background(GuiGraphicsExtractor g,Slot slot,int mouseX,int mouseY,CallbackInfo ci){
        if(kca$isStorageScreen())RarityBackground.drawStorage(g,slot.getItem(),slot.x,slot.y);
        else RarityBackground.draw(g,slot.getItem(),slot.x,slot.y,false);
    }
}
