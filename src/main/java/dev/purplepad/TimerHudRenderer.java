/* SPDX-License-Identifier: GPL-3.0-only */
package dev.purplepad;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;

final class TimerHudRenderer {
    private TimerHudRenderer() { }
    static float baseWidth(Font font, TimerConfig config) {
        return font.width("2.80s") + (config.simpleMode ? 0 : 8);
    }
    static float baseHeight(Font font, TimerConfig config) {
        // Font.lineHeight includes line spacing, which is not part of a single countdown's ink.
        return config.simpleMode ? font.lineHeight : inkHeight(font, config) + 6;
    }
    static HudGeometry.Bounds bounds(Font font, TimerConfig config, int width, int height) {
        return HudGeometry.bounds(config.x, config.y, config.scale, baseWidth(font, config),
            baseHeight(font, config), width, height);
    }
    static void draw(GuiGraphicsExtractor graphics, Font font, TimerConfig config, String label,
                     float x, float y, float scale) {
        float textX = x;
        float textY = y;
        if (!config.simpleMode) {
            int left = Math.round(x);
            int top = Math.round(y);
            int right = Math.round(x + baseWidth(font, config) * scale);
            int bottom = Math.round(y + baseHeight(font, config) * scale);
            config.style().backdrop(graphics, left, top, right, bottom, com.nucleus.NucleusMod.CONFIG.timerColor);
            float inkWidth = Math.max(1, font.width(label) - 1 + (config.shadow ? 1 : 0));
            textX = left + (right - left - inkWidth * scale) / 2f;
            textY = top + (bottom - top - inkHeight(font, config) * scale) / 2f;
        }
        // Keep the origin on a physical pixel rather than a fractional pixel after GUI scaling.
        int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        textX = Math.round(textX * guiScale) / (float) guiScale;
        textY = Math.round(textY * guiScale) / (float) guiScale;
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(textX, textY);
        pose.scale(scale, scale);
        graphics.text(font, label, 0, 0, com.nucleus.NucleusMod.CONFIG.timerColor, config.shadow);
        pose.popMatrix();
    }

    private static int inkHeight(Font font, TimerConfig config) {
        return Math.max(1, font.lineHeight - 2 + (config.shadow ? 1 : 0));
    }
}
