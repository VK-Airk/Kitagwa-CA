package dev.purplepad.mixin;

import dev.purplepad.Shortcuts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "com.skysoft.features.inventory.buttons.InventoryButtonManager", remap = false)
abstract class SkysoftInventoryShortcuts {
    @ModifyArg(
        method = "executeCommand(Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendCommand(Ljava/lang/String;)V"),
        index = 0, require = 0
    )
    private String kca$expandInventoryShortcut(String command) {
        return Shortcuts.typed(command);
    }
}
