/* SPDX-License-Identifier: GPL-3.0-only */
package dev.purplepad;

import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Full-screen placement preview. Done changes the parent draft; only its Save writes to disk. */
public final class TimerHudEditor extends Screen {
    private static final int CYAN = KcaTheme.ROSE;
    private final Screen parent;
    private final TimerConfig draft;
    private boolean moving;
    private boolean resizing;
    private double mouseStartX;
    private double mouseStartY;
    private float startX;
    private float startY;
    private float startScale;

    TimerHudEditor(Screen parent, TimerConfig draft) {
        super(Component.literal("Move & Resize Purple Pad Timer"));
        this.parent = parent;
        this.draft = draft;
    }

    @Override protected void init() {
        moving = resizing = false;
        int buttonWidth = Math.min(92, (width - 32) / 3);
        int left = (width - buttonWidth * 3 - 16) / 2;
        int y = height - 30;
        addRenderableWidget(new TimerSettingsScreen.PixelButton(left, y, buttonWidth, 20, "Center", CYAN, b -> {
            draft.x = -1;
            draft.y = Math.max(0, Math.round((height - baseHeight() * draft.scale) / 2));
        }));
        addRenderableWidget(new TimerSettingsScreen.PixelButton(left + buttonWidth + 8, y, buttonWidth, 20,
            "Cancel", KcaTheme.MUTED, b -> onClose()));
        addRenderableWidget(new TimerSettingsScreen.PixelButton(left + (buttonWidth + 8) * 2, y, buttonWidth, 20,
            "Done", KcaTheme.PETAL, b -> {
                if(parent instanceof TimerSettingsScreen settings) settings.acceptPlacement(draft);
                else try { PurplePadClient.applySettings(draft); }
                catch(java.io.IOException ex){minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal("Could not save timer placement."));return;}
                minecraft.gui.setScreen(parent);
            }));
    }

    private float baseWidth() { return TimerHudRenderer.baseWidth(font, draft); }
    private float baseHeight() { return TimerHudRenderer.baseHeight(font, draft); }
    private HudGeometry.Bounds bounds() { return TimerHudRenderer.bounds(font, draft, width, height); }

    private void place(float x, float y) {
        // Floor retains the entire element inside the screen, even at fractional sizes.
        draft.x = (int) Math.floor(HudGeometry.clampPosition(x, width, baseWidth() * draft.scale));
        draft.y = (int) Math.floor(HudGeometry.clampPosition(y, height, baseHeight() * draft.scale));
    }

    private boolean onHandle(double x, double y, HudGeometry.Bounds bounds) {
        double right = bounds.x() + bounds.width();
        double bottom = bounds.y() + bounds.height();
        return x >= right - 7 && x <= Math.min(width, right + 3)
            && y >= bottom - 7 && y <= Math.min(height, bottom + 3);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;
        var bounds = bounds();
        resizing = onHandle(event.x(), event.y(), bounds);
        moving = !resizing && bounds.contains(event.x(), event.y());
        if (!resizing && !moving) return false;
        clearFocus();
        mouseStartX = event.x();
        mouseStartY = event.y();
        startX = bounds.x();
        startY = bounds.y();
        startScale = draft.scale;
        return true;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (event.button() == 0 && (moving || resizing)) {
            if (resizing) {
                draft.scale = HudGeometry.resizeScale(startScale, event.x() - mouseStartX,
                    event.y() - mouseStartY, baseWidth(), baseHeight());
                place(startX, startY);
            } else {
                place(startX + (float) (event.x() - mouseStartX), startY + (float) (event.y() - mouseStartY));
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && (moving || resizing)) {
            moving = resizing = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        var bounds = bounds();
        if (bounds.contains(mouseX, mouseY) || onHandle(mouseX, mouseY, bounds)) {
            draft.scale = HudGeometry.clampScale(draft.scale + (float) vertical * 0.1f);
            place(bounds.x(), bounds.y());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        // Arrow keys nudge the timer only when a footer button is not focused.
        if (getFocused() == null && event.key() >= 262 && event.key() <= 265) {
            var bounds = bounds();
            int step = (event.modifiers() & 1) != 0 ? 10 : 1;
            float x = bounds.x() + (event.key() == 262 ? step : event.key() == 263 ? -step : 0);
            float y = bounds.y() + (event.key() == 264 ? step : event.key() == 265 ? -step : 0);
            place(x, y);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0x55080C15);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.centeredText(font, "MOVE & RESIZE", width / 2, 10, KcaTheme.ROSE);
        graphics.centeredText(font, "Drag timer to move. Drag corner to resize.", width / 2, 24, KcaTheme.MUTED);
        graphics.centeredText(font, "Scroll over timer for fine sizing.", width / 2, 36, KcaTheme.MUTED);
        var bounds = bounds();
        TimerHudRenderer.draw(graphics, font, draft, "2.80s", bounds.x(), bounds.y(), draft.scale);
        int x = (int) Math.floor(bounds.x()) - 2;
        int y = (int) Math.floor(bounds.y()) - 2;
        int right = (int) Math.ceil(bounds.x() + bounds.width()) + 2;
        int bottom = (int) Math.ceil(bounds.y() + bounds.height()) + 2;
        graphics.fill(x, y, right, y + 1, CYAN);
        graphics.fill(x, bottom - 1, right, bottom, CYAN);
        graphics.fill(x, y, x + 1, bottom, CYAN);
        graphics.fill(right - 1, y, right, bottom, CYAN);
        graphics.fill(Math.min(width, right) - 5, Math.min(height, bottom) - 5,
            Math.min(width, right), Math.min(height, bottom), CYAN);
        String details = String.format(Locale.ROOT, "X %d  Y %d  Scale %.2f", (int) bounds.x(), (int) bounds.y(), draft.scale);
        graphics.centeredText(font, details, width / 2, height - 55, CYAN);
        graphics.centeredText(font, "Done returns to settings. Save applies.", width / 2, height - 43, KcaTheme.MUTED);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
