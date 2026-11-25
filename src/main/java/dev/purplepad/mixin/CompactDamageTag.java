package dev.purplepad.mixin;

import com.nucleus.NucleusMod;
import com.nucleus.client.SkyBlockDetector;
import dev.purplepad.DamageNumbers;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorStandRenderer.class)
abstract class CompactDamageTag {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/decoration/ArmorStand;Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;F)V",at=@At("TAIL"))
    private void kca$compactDamage(ArmorStand stand,ArmorStandRenderState state,float partialTick,CallbackInfo ci){
        var config=NucleusMod.CONFIG;
        if(config!=null && config.kcaEnabled && (!config.showDamageSplashes || config.compactDamageNumbers) && state.nameTag!=null
            && stand.isInvisible() && stand.hasCustomName() && SkyBlockDetector.isOnSkyBlock())
            state.nameTag=DamageNumbers.display(stand,state.nameTag,config);
    }
}
