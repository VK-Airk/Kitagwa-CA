/* SPDX-License-Identifier: GPL-3.0-only */
package dev.purplepad;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Small, texture-free SkyBlock-style settings panel. Edits stay local until saved. */
public final class TimerSettingsScreen extends Screen {
    private static final int NAVY = KcaTheme.INK;
    private static final int BACKGROUND = 0xFF1A101A;
    private static final int MAGENTA = KcaTheme.ROSE;
    private static final int CYAN = KcaTheme.PETAL;
    private static final int ORANGE = KcaTheme.MAUVE;
    private static final int GREEN = KcaTheme.PETAL;
    private static final int MUTED = KcaTheme.MUTED;
    private TimerConfig draft;
    private final Screen parent;
    private String error = "";
    private int left;
    private int top;
    private int panelWidth;
    private int age;

    public TimerSettingsScreen(TimerConfig draft) {
        this(draft, null);
    }

    TimerSettingsScreen(TimerConfig draft, Screen parent) {
        super(Component.literal("Purple Pad Timer"));
        this.draft = draft;
        this.parent = parent;
    }

    @Override protected void init() {
        panelWidth = Math.min(360, width - 16);
        left = (width - panelWidth) / 2;
        top = Math.max(0, (height - 232) / 2);
        int inner = panelWidth - 24;
        int third = (inner - 8) / 2;
        addRenderableWidget(new PixelButton(left + 12, top + 100, third, 20, enabledLabel(), GREEN, b -> {
            draft.enabled = !draft.enabled;
            b.setMessage(Component.literal(enabledLabel()));
        }));
        addRenderableWidget(new PixelButton(left + 20 + third, top + 100, third, 20, shadowLabel(), CYAN, b -> {
            draft.shadow = !draft.shadow;
            b.setMessage(Component.literal(shadowLabel()));
        }));
        addRenderableWidget(new PixelButton(left + 12, top + 132, inner, 24, "Move & Resize", ORANGE,
            b -> minecraft.setScreen(new TimerHudEditor(this, draft.copy()))));
        addRenderableWidget(new PixelButton(left + 12, top + 158, inner, 20, "GUI settings", MAGENTA,
            b -> minecraft.setScreen(new GuiSettingsScreen(this, GuiSettingsScreen.Element.TIMER, draft))));
        int buttonWidth = (inner - 16) / 3;
        addRenderableWidget(new PixelButton(left + 12, top + 184, buttonWidth, 20, "Defaults", MUTED, b -> {
            draft = new TimerConfig();
            error = "";
            rebuildWidgets();
        }));
        addRenderableWidget(new PixelButton(left + 20 + buttonWidth, top + 184, buttonWidth, 20, "Cancel", MUTED, b -> onClose()));
        addRenderableWidget(new PixelButton(left + 28 + buttonWidth * 2, top + 184, buttonWidth, 20, "Save", ORANGE, b -> save()));
    }

    private String enabledLabel() { return "Timer " + (draft.enabled ? "ON" : "OFF"); }
    private String shadowLabel() { return "Shadow " + (draft.shadow ? "ON" : "OFF"); }

    void acceptPlacement(TimerConfig placement) {
        draft.x = placement.x;
        draft.y = placement.y;
        draft.scale = placement.scale;
        error = "";
    }

    private void save() {
        try {
            PurplePadClient.applySettings(draft);
            onClose();
        } catch (java.io.IOException e) {
            error = "Could not save. Check config access.";
        }
    }

    @Override public void tick() { age++; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }

    // A simple dim layer avoids invoking Minecraft's expensive menu blur.
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        KcaTheme.backdrop(graphics,width,height,KcaTheme.Branch.DUNGEONS);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        card(graphics, left, top, panelWidth, 232, KcaTheme.EDGE, BACKGROUND);
        graphics.text(font, "PURPLE PAD", left + 12, top + 10, MAGENTA, false);
        String badge = "F7 / M7";
        graphics.text(font, badge, left + panelWidth - 12 - font.width(badge), top + 10, ORANGE, false);
        graphics.text(font, "Storm / PY countdown", left + 12, top + 23, MUTED, false);
        card(graphics, left + 12, top + 36, panelWidth - 24, 56, MAGENTA, NAVY);
        int ticks = 56 - age % 76;
        if (ticks <= 0) ticks = 56;
        String label = PurplePadClient.label(ticks);
        float baseWidth = TimerHudRenderer.baseWidth(font, draft);
        float baseHeight = TimerHudRenderer.baseHeight(font, draft);
        float sampleScale = Math.min(draft.scale, Math.min(48f / baseHeight, (panelWidth - 40f) / baseWidth));
        TimerHudRenderer.draw(graphics, font, draft, label,
            left + (panelWidth - baseWidth * sampleScale) / 2f,
            top + 36 + (56 - baseHeight * sampleScale) / 2f, sampleScale);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        String footer = error.isEmpty() ? "/purplepad help  |  Esc cancels" : error;
        graphics.text(font, footer, left + 12, top + 215, error.isEmpty() ? MUTED : 0xFFFF5555, false);
    }

    static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, int edge, int fill) {
        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        // Pixel corner accents, inspired by SkyBlock rarity cards.
        for (int cx : new int[]{x + 3, x + w - 5}) {
            for (int cy : new int[]{y + 3, y + h - 5}) g.fill(cx, cy, cx + 2, cy + 2, edge);
        }
    }

    static final class PixelButton extends Button {
        private final int accent;
        private final int ink;
        PixelButton(int x, int y, int w, int h, String text, int accent, OnPress action) {
            this(x,y,w,h,text,accent,KcaTheme.INK,action);
        }
        PixelButton(int x, int y, int w, int h, String text, int accent, int ink, OnPress action) {
            super(x, y, w, h, Component.literal(text), action, DEFAULT_NARRATION);
            this.accent = accent;
            this.ink = ink;
        }
        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            boolean highlighted = isHoveredOrFocused();
            KcaTheme.button(graphics,getX(),getY(),getWidth(),getHeight(),highlighted,active,accent,ink);
            var font = Minecraft.getInstance().font;
            graphics.text(font, getMessage(), getX() + (getWidth() - font.width(getMessage())) / 2,
                getY() + (getHeight() - font.lineHeight) / 2 + 1, highlighted ? KcaTheme.PETAL : KcaTheme.tint(accent,0xFFF7E9EA,55), false);
        }
    }
}
