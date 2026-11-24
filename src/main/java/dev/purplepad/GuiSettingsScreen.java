package dev.purplepad;

import com.nucleus.NucleusMod;
import com.nucleus.client.HudTint;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class GuiSettingsScreen extends Screen {
    public enum Element {
        TIMER("Purple Pad Timer"), COUNTER("Secrets and rooms"), NUCLEUS("Nucleus text HUDs");
        final String label;
        Element(String label) { this.label = label; }
    }

    private final Screen parent;
    private Element element;
    private final TimerConfig draft;
    private TimerConfig timer;
    private List<String> counter;
    private int counterWidth;
    private int top, left, inner;
    private String error = "";

    public GuiSettingsScreen(Screen parent, Element element) { this(parent, element, null); }
    GuiSettingsScreen(Screen parent, Element element, TimerConfig draft) {
        super(Component.literal("GUI settings"));
        this.parent = parent;
        this.element = element;
        this.draft = draft;
    }

    private HudStyle selection() {
        return switch (element) {
            case TIMER -> timer.style();
            case COUNTER -> RunStats.style();
            case NUCLEUS -> HudTint.style();
        };
    }

    private void select(HudStyle style) {
        switch (element) {
            case TIMER -> {
                TimerConfig next = draft == null ? PurplePadClient.timerConfig() : draft;
                next.setStyle(style);
                if (draft == null) {
                    try { PurplePadClient.applySettings(next); }
                    catch (IOException e) { error = "Could not save timer style."; return; }
                }
            }
            case COUNTER -> RunStats.setStyle(style);
            case NUCLEUS -> { NucleusMod.CONFIG.nucleusHudStyle = style; NucleusMod.CONFIG.save(); }
        }
        error = "";
        rebuildWidgets();
    }

    private void option(String label, int column, int y, boolean selected, Runnable action) {
        int w = (inner - 8) / 3;
        var button = addRenderableWidget(new TimerSettingsScreen.PixelButton(left + column * (w + 4), y, w, 20,
            label, selected ? KcaTheme.ROSE : KcaTheme.MUTED, b -> action.run()));
        button.active = !selected;
    }

    @Override protected void init() {
        inner = Math.min(416, width - 20);
        left = (width - inner) / 2;
        top = Math.max(2, (height - 184) / 2);
        timer = draft == null ? PurplePadClient.timerConfig() : draft;
        counter = RunHudEditor.sample();
        counterWidth = counter.stream().mapToInt(font::width).max().orElse(0);
        for (Element e : Element.values()) if(e==Element.NUCLEUS?KcaEdition.NUCLEUS:KcaEdition.DUNGEONS)option(e.label, e.ordinal(), top + 43, e == element, () -> {
            element = e;
            rebuildWidgets();
        });
        for (HudStyle style : HudStyle.values()) option(style.label, style.ordinal(), top + 127,
            style == selection(), () -> select(style));
        addRenderableWidget(new TimerSettingsScreen.PixelButton(width / 2 - 40, top + 161, 80, 20, "Back",
            KcaTheme.MUTED, b -> onClose()));
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float tick) {
        KcaTheme.backdrop(g,width,height,element==Element.NUCLEUS?KcaTheme.Branch.NUCLEUS:KcaTheme.Branch.DUNGEONS);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float tick) {
        int cx = width / 2, cy = top + 96;
        KcaTheme.heading(g, font, "KCA", cx, top, 2);
        g.centeredText(font, "GUI settings", cx, top + 25, KcaTheme.PETAL);
        KcaTheme.panel(g, left, top + 69, inner, 53);
        if (element == Element.TIMER) {
            float scale = 1.7f;
            TimerHudRenderer.draw(g, font, timer, "2.80s", cx - TimerHudRenderer.baseWidth(font,timer)*scale/2,
                cy - TimerHudRenderer.baseHeight(font,timer)*scale/2, scale);
        } else if (element == Element.COUNTER) {
            int w = counterWidth + (RunStats.simple ? 2 : 12);
            int h = counter.size() * (font.lineHeight + 1) + (RunStats.simple ? 0 : 8);
            RunHudEditor.paint(g, font, counter, cx-w/2, cy-h/2, w, h, 1);
        } else {
            String sample = "Nucleus Run  1:24.50";
            int w = font.width(sample), x = cx-w/2, y = cy-font.lineHeight/2;
            HudTint.backdrop(g, x-4, y-3, x+w+4, y+font.lineHeight+3);
            g.text(font, sample, x, y, NucleusMod.CONFIG.nucleusAccent, true);
        }
        super.extractRenderState(g, mx, my, tick);
        int w = (inner-8)/3;
        for (int i : new int[]{0,1}) {
            int column = i == 0 ? element.ordinal() : selection().ordinal();
            int x = left + column*(w+4), y = top + (i == 0 ? 61 : 145);
            g.fill(x+2,y,x+w-2,y+2,KcaTheme.ROSE);
        }
        if (!error.isEmpty()) g.centeredText(font,error,cx,top+150,KcaTheme.ROSE);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
