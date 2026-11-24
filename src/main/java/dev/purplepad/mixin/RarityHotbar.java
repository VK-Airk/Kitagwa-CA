package dev.purplepad.mixin;
import dev.purplepad.RarityBackground;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
@Mixin(Hud.class)
abstract class RarityHotbar {
    @Inject(method="extractSlot",at=@At("HEAD"))
    private void kca$background(GuiGraphicsExtractor g,int x,int y,DeltaTracker delta,Player player,ItemStack stack,int seed,CallbackInfo ci){
        RarityBackground.draw(g,stack,x,y,true);
    }
}
