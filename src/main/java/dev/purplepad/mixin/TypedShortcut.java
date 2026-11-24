package dev.purplepad.mixin;

import dev.purplepad.Shortcuts;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ChatScreen.class)
abstract class TypedShortcut {
    @ModifyArg(method="handleChatInput",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientPacketListener;sendCommand(Ljava/lang/String;)V"),index=0)
    private String kca$expandTyped(String command){return Shortcuts.typed(command);}
}
