package dev.purplepad.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.purplepad.BetaBadges;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class BetaNameTag {
    @Inject(method="submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",at=@At("TAIL"))
    private void kca$showBeta(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci){
        if(state.nameTag==null || state.nameTagAttachment==null || !BetaBadges.active())return;
        var level=Minecraft.getInstance().level;
        if(level==null || !(level.getEntity(state.id) instanceof Player player) || !BetaBadges.listed(player))return;
        double height=state.scoreText==null?0.27:0.54;
        collector.submitNameTag(pose,state.nameTagAttachment.add(0,height,0),state.showExtraEars?-10:0,BetaBadges.LABEL,
            !state.isDiscrete,state.lightCoords,state.distanceToCameraSq,camera);
    }
}
