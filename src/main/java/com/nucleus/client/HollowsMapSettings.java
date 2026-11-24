package com.nucleus.client;

import com.nucleus.NucleusConfig.MapIcon;
import com.nucleus.NucleusConfig.PinColor;
import com.nucleus.NucleusMod;
import dev.purplepad.KcaTheme;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class HollowsMapSettings extends Screen {
    private static final HollowsMap.Place[] PLACES = HollowsMap.Place.values();
    private static final PinColor[] COLORS = PinColor.values();
    private static final MapIcon[] ICONS = MapIcon.values();
    private final Screen parent;
    private int selected;
    private boolean dirty;
    private List<HollowsMap.Pin> sample;
    private String colorLabel;
    private int previewX, previewY, labelX, colorY;
    private float previewScale;

    HollowsMapSettings(Screen parent) {
        super(Component.literal("Hollows map"));
        this.parent = parent;
    }

    @Override protected void init() {
        var cfg = NucleusMod.CONFIG;
        int w = Math.min(390, width - 12), x = (width - w) / 2;
        int half = (w - 4) / 2;
        int step = Math.clamp((height - 43) / 8, 17, 23);
        int h = Math.min(20, step - 2);
        int top = 20;
        option(x, top, half, h, "Region names", cfg.mapRegions, () -> cfg.mapRegions = !cfg.mapRegions);
        option(x + half + 4, top, half, h, "Map legend", cfg.mapLegend, () -> cfg.mapLegend = !cfg.mapLegend);
        option(x, top + step, half, h, "Nucleus marker", cfg.mapNucleus, () -> cfg.mapNucleus = !cfg.mapNucleus);
        option(x + half + 4, top + step, half, h, "Waypoint pins", cfg.mapWaypoints, () -> cfg.mapWaypoints = !cfg.mapWaypoints);

        var place = PLACES[selected];
        long bit = 1L << place.ordinal();
        int y = top + 2 * step + 3;
        previewScale = Math.min(1f, Math.min((w * 0.37f) / HollowsMap.SIZE, (height - y - 42) / (float)(HollowsMap.SIZE + HollowsMap.LEGEND_ROW)));
        int previewWidth = Math.round(HollowsMap.SIZE * previewScale);
        previewX = x + w - previewWidth;
        previewY = y + 12;
        sample = List.of(new HollowsMap.Pin(place, 359, 100, 418));
        int controls = previewX - x - 10;

        button(x, y, 22, h, "<", () -> select(-1)).setTooltip(tip("Previous landmark"));
        button(x + 24, y, controls - 48, h, place.label, () -> select(1)).setTooltip(tip("Select next landmark"));
        button(x + controls - 22, y, 22, h, ">", () -> select(1)).setTooltip(tip("Next landmark"));
        y += step;
        boolean hidden = (cfg.hiddenMapPins & bit) != 0;
        boolean highlighted = (cfg.highlightedMapPins & bit) != 0;
        int col = (controls - 3) / 2;
        button(x, y, col, h, hidden ? "Hidden" : highlighted ? "Highlighted" : "Visible", () -> {
            if (hidden) cfg.hiddenMapPins &= ~bit;
            else if (highlighted) {
                cfg.highlightedMapPins &= ~bit;
                cfg.hiddenMapPins |= bit;
            } else cfg.highlightedMapPins |= bit;
            changed();
        }).setTooltip(tip("Cycle this landmark through visible, highlighted and hidden."));
        var icon = cfg.mapIcons.getOrDefault(place.name(), MapIcon.Letter);
        button(x + col + 3, y, controls - col - 3, h, "Icon - " + icon, () -> {
            cfg.mapIcons.put(place.name(), ICONS[(icon.ordinal() + 1) % ICONS.length]);
            changed();
        });
        labelX = x;
        colorY = y + step + 1;
        y = colorY + 11;
        var current = cfg.mapPinColors.getOrDefault(place.name(), PinColor.Auto);
        colorLabel = "Color - " + current.name();
        int columns = (COLORS.length + 1) / 2;
        for (int i = 0; i < COLORS.length; i++) {
            var color = COLORS[i];
            int pos = i % columns;
            int left = x + pos * controls / columns;
            int bw = x + (pos + 1) * controls / columns - left - 1;
            int rgb = color == PinColor.Auto ? 0xFFE5D8 : color.rgb;
            String glyph = color == PinColor.Auto ? "A" : "\u25A0";
            var swatch = NucleusUi.NButton.create(Component.literal(glyph).withColor(rgb), b -> {
                if (color == PinColor.Auto) cfg.mapPinColors.remove(place.name());
                else cfg.mapPinColors.put(place.name(), color);
                changed();
            }).pos(left, y + (i / columns) * (h + 1)).size(bw, h).accent(rgb).build();
            swatch.selected(color == current);
            String description = color == PinColor.Auto ? "Auto (gold, or pink when highlighted)" : color.name();
            swatch.setTooltip(tip(description));
            addRenderableWidget(swatch);
        }
        int sliderY = y + 2 * (h + 1) + 4;
        var slider = new IntSlider(x, sliderY, controls, "Marker size", 50, 200, cfg.mapMarkerPercent, "%", value -> {
            cfg.mapMarkerPercent = value;
            dirty = true;
        });
        slider.accent = 0xFFADD0;
        slider.setTooltip(tip("Resize all landmark markers on the map. The legend stays compact."));
        addRenderableWidget(slider);
        button(width / 2 - 40, height - 22, 80, 20, "Back", this::onClose);
    }

    private void select(int delta) {
        selected = Math.floorMod(selected + delta, PLACES.length);
        rebuildWidgets();
    }

    private void changed() {
        dirty = true;
        rebuildWidgets();
    }

    private void option(int x, int y, int w, int h, String name, boolean enabled, Runnable update) {
        button(x, y, w, h, name + (enabled ? " - ON" : " - OFF"), () -> { update.run(); changed(); });
    }

    private NucleusUi.NButton button(int x, int y, int w, int h, String text, Runnable click) {
        return addRenderableWidget(NucleusUi.NButton.create(Component.literal(text), b -> click.run())
            .pos(x, y).size(w, h).build());
    }

    private static Tooltip tip(String text) { return Tooltip.create(Component.literal(text)); }

    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float t) {
        KcaTheme.backdrop(g, width, height, KcaTheme.Branch.NUCLEUS);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float t) {
        super.extractRenderState(g, x, y, t);
        var place = PLACES[selected];
        g.centeredText(font, "Hollows map / Landmarks", width / 2, 6, 0xFFFFCEE5);
        g.text(font, colorLabel, labelX, colorY, 0xFFFFE1EC, true);
        g.centeredText(font, "Preview", previewX + Math.round(HollowsMap.SIZE * previewScale / 2), previewY - 11, 0xFFFFCEE5);
        HollowsMap.drawEditor(g, previewX, previewY, previewScale, sample, 1L << place.ordinal());
    }

    @Override public void removed() {
        if (dirty) NucleusMod.CONFIG.save();
        super.removed();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
