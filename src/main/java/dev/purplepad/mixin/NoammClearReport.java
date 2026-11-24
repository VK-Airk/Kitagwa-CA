package dev.purplepad.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.purplepad.RunStats;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets="com.github.noamm9.utils.dungeons.map.handlers.ClearInfoUpdater$sendClearInfoMessage$1",remap=false)
abstract class NoammClearReport {
    @WrapOperation(method="invokeSuspend",at=@At(value="INVOKE",target="Lcom/github/noamm9/utils/ChatUtils;chat(Lnet/minecraft/network/chat/Component;)V"),require=0)
    private void kca$readReport(@Coerce Object chat, Component row, Operation<Void> original){
        Minecraft.getInstance().execute(()->{
            if(!RunStats.acceptClearRow(row))original.call(chat,row);
        });
    }
}
