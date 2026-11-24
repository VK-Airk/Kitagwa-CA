package com.nucleus.client;

import com.nucleus.NucleusMod;

/** Shared timer background tint behind Bal/speedrun text (0-100 to black alpha). */
public final class HudTint {
	private HudTint() {
	}

	public static dev.purplepad.HudStyle style() {
        var style = NucleusMod.CONFIG.nucleusHudStyle;
        return style == null ? dev.purplepad.HudStyle.KCA : style;
    }

    public static void backdrop(net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y, int right, int bottom) {
        style().backdrop(g, x, y, right, bottom, NucleusMod.CONFIG.nucleusAccent);
    }

    public static int bg() {
		int v = NucleusMod.CONFIG.timerBg;
		if (v < 0) {
			v = 0;
		}
		if (v > 100) {
			v = 100;
		}
		return (v * 255 / 100) << 24;
	}
}
