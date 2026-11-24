package com.nucleus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Renders the spinning reward in Minecraft's GUI pass alongside the vault and captions. */
final class ItemPopup {
    private final ItemEntityRenderState model = new ItemEntityRenderState();
    private final Vector3f pivot;
    private final float extent;
    private final float yaw;
    private final float peak;
    private final long started;
    private final long duration;

    ItemPopup(ItemStack stack, float yaw, float peak, long started, long duration) {
        var client = Minecraft.getInstance();
        model.entityType = EntityType.ITEM;
        model.count = 1;
        model.lightCoords = 0xF000F0;
        client.getItemModelResolver().updateForTopItem(model.item, stack, ItemDisplayContext.FIXED, client.level, null, 0);
        if (model.item.isEmpty()) throw new IllegalArgumentException("Empty reward model for " + stack);
        var box = model.item.getModelBoundingBox();
        var center = box.getCenter();
        // Undo the dropped-item renderer's lift and center the model on the spin axis.
        pivot = new Vector3f((float)-center.x, (float)(box.minY - center.y - .1625), (float)-center.z);
        extent = (float)Math.max(.25, Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())));
        this.yaw = yaw;
        this.peak = peak;
        this.started = started;
        this.duration = duration;
    }

    void draw(GuiGraphicsExtractor gfx, float x, float y, float size, int radius, float spinBlend, long now) {
        float t = Math.clamp((now - started) / (float)duration, 0f, 1f);
        float p = t < peak ? .5f * t / peak : .5f + .5f * (t - peak) / (1f - peak);
        float curve = p * (4f + p * (-13.8f + p * (25.5f + p * (-24.95f + 10.25f * p))));
        float swing = Math.abs(Mth.sin(Math.PI * curve));
        float tilt = 6f * Mth.cos(8f * p);
        float radians = (float)(Math.PI / 180);
        var rotation = new Quaternionf().rotationX((float)Math.PI).rotateX(20f * radians)
            .rotateY((yaw + 30f + 1080f * spinBlend * swing) * radians)
            .rotateX(tilt * radians).rotateZ(tilt * radians);
        float scale = .94f * size / extent * (1f - .45f * spinBlend * (1f - swing));
        int cx = Math.round(x), cy = Math.round(y);
        gfx.entity(model, scale, rotation.transform(new Vector3f(pivot)), rotation, null,
            cx - radius, cy - radius, cx + radius, cy + radius);
    }
}
