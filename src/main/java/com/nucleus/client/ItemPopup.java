package com.nucleus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.nucleus.client.mixin.GuiSceneAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

/** Composites the original item model directly into the reward HUD. */
final class ItemPopup {
    private final ItemStackRenderState model = new ItemStackRenderState();
    private final float offX = java.util.concurrent.ThreadLocalRandom.current().nextFloat(-1f,1f);
    private final float offY = java.util.concurrent.ThreadLocalRandom.current().nextFloat(-1f,1f);
    private final boolean mirror = java.util.concurrent.ThreadLocalRandom.current().nextBoolean();
    private final float extent;
    private final float yaw;
    private final float peak;
    private final long started;
    private final long duration;

    ItemPopup(ItemStack stack, float yaw, float peak, long started, long duration) {
        var client = Minecraft.getInstance();
        client.getItemModelResolver().updateForTopItem(model, stack, ItemDisplayContext.FIXED, client.level, null, 0);
        var modelId=stack.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
        if(model.isEmpty() && modelId!=null && modelId.getNamespace().equals("hypixel_skyblock")){
            var bundled=JackpotAnimation.DropType.bundledModel(stack,modelId);
            client.getItemModelResolver().updateForTopItem(model,bundled,ItemDisplayContext.FIXED,client.level,null,0);
        }
        if (model.isEmpty()) throw new IllegalArgumentException("Empty reward model for " + stack);
        var box = model.getModelBoundingBox();
        extent = (float)Math.max(.25, Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())));
        this.yaw = yaw;
        this.peak = peak;
        this.started = started;
        this.duration = Math.clamp(Math.round(duration / 50f), 1, 400) * 50L;
    }

    void draw(GuiGraphicsExtractor gfx, float x, float y, float hatchSize, float spinBlend, long now) {
        float t = Math.clamp((now - started) / (float)duration, 0f, 1f);
        float p = t < peak ? .5f * t / peak : .5f + .5f * (t - peak) / (1f - peak);
        float curve = p * (4f + p * (-13.8f + p * (25.5f + p * (-24.95f + 10.25f * p))));
        float swing = Math.abs(Mth.sin(Math.PI * curve));
        float tilt = 6f * Mth.cos(8f * p);
        float radians = (float)(Math.PI / 180);
        var rotation = new Quaternionf().rotationX((float)Math.PI).rotateY(yaw * radians).rotateX(20f * radians)
            .rotateY((30f + 1080f * spinBlend * swing) * radians)
            .rotateX(tilt * radians).rotateZ(tilt * radians);
        // Project the original totem camera path into GUI pixels. Its projected scale grows
        // tenfold between the distant entrance and the closest approach.
        float depth = 10f - 9f * Mth.sin(Math.PI * curve);
        float h = gfx.guiHeight(), w = gfx.guiWidth();
        float focal = h * .714074f; // half-height / tan(70-degree HUD FOV / 2)
        float fade = Math.clamp((1f-p)/.15f, 0f, 1f);
        float scale = Mth.lerp(spinBlend,hatchSize/extent,.65f*focal/depth) * fade * fade;
        if (scale < .5f) return;
        float drift = .3f * focal / depth * Math.abs(Mth.sin(2*Math.PI*curve));
        if (mirror) {
            if (p>.5f) drift=-drift;
            if (p>.3f && p<.7f) drift *= .5f-.5f*Mth.cos(5*Math.PI*(p-.5f));
        }
        // x/y already interpolate from the hatch to the normal reveal position.
        int cx = Math.round(x + spinBlend * offX * (w/h) * drift);
        int cy = Math.round(y + spinBlend * (.02f*h - offY * drift));
        int radius = (int)Math.ceil(h*.36f);
        ((GuiSceneAccessor)gfx).kcaSceneState().addPicturesInPictureState(
            new Frame(this, rotation, scale, cx-radius, cy-radius, cx+radius, cy+radius));
    }

    private record Frame(ItemPopup reward, Quaternionf rotation, float scale, int x0, int y0, int x1, int y1)
            implements PictureInPictureRenderState {
        public ScreenRectangle scissorArea() { return null; }
        public ScreenRectangle bounds() { return PictureInPictureRenderState.getBounds(x0,y0,x1,y1,null); }
    }

    static void register() {
        PictureInPictureRendererRegistry.register(context -> new Renderer());
    }

    private static final class Renderer extends PictureInPictureRenderer<Frame> {
        @Override public Class<Frame> getRenderStateClass() { return Frame.class; }
        @Override protected String getTextureLabel() { return "KCA reward"; }
        @Override protected float getTranslateY(int height,int guiScale) { return height * .5f; }
        @Override protected void renderToTexture(Frame frame, PoseStack pose, SubmitNodeCollector collector) {
            try {
                var game = Minecraft.getInstance().gameRenderer;
                game.lighting().setupFor(Lighting.Entry.ITEMS_3D);
                pose.mulPose(frame.rotation);
                frame.reward.model.submit(pose, collector, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
            } catch (RuntimeException failure) {
                JackpotAnimation.rewardRenderFailed(frame.reward, failure);
            }
        }
    }
}
