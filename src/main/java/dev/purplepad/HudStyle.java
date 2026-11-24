package dev.purplepad;

import com.nucleus.client.HudTint;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public enum HudStyle {
    SIMPLE("Simple"), @com.google.gson.annotations.SerializedName(value="KCA", alternate={"MADO"}) KCA("KCA"), FANCY("Purple Pad Fancy");

    final String label;
    HudStyle(String label) { this.label = label; }

    public void backdrop(GuiGraphicsExtractor g, int left, int top, int right, int bottom, int accent) {
        if (this == SIMPLE) return;
        if (this == KCA) {
            g.fill(left, top, right, bottom, HudTint.bg());
            return;
        }
        g.fill(left + 1, top + 1, right - 1, bottom - 1, 0xE8201620);
        g.fill(left, top, right, top + 1, accent);
        g.fill(left, bottom - 1, right, bottom, accent);
        g.fill(left, top + 1, left + 1, bottom - 1, accent);
        g.fill(right - 1, top + 1, right, bottom - 1, accent);
        int corner = Math.min(4, (right - left) / 2);
        g.fill(left, top + 1, left + corner, top + 2, accent);
        g.fill(right - corner, top + 1, right, top + 2, accent);
        g.fill(left, bottom - 2, left + corner, bottom - 1, accent);
        g.fill(right - corner, bottom - 2, right, bottom - 1, accent);
    }
}
