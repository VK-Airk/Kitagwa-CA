package dev.purplepad.mixin;
import com.mojang.blaze3d.platform.InputConstants;
import dev.purplepad.Shortcuts;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
abstract class ShortcutKeys {
    @Inject(method="set",at=@At("HEAD"))
    private static void kca$input(InputConstants.Key key,boolean down,CallbackInfo ci){Shortcuts.key(key.getName(),down);}
    @Inject(method="releaseAll",at=@At("HEAD"))
    private static void kca$released(CallbackInfo ci){Shortcuts.clearKeys();}
}
