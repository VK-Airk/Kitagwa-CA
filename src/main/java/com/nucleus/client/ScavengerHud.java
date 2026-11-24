package com.nucleus.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import com.nucleus.NucleusMod;

/**
 * Scavenger session HUD in Bal-timer style: tools/hour rate, average full
 * set time, and session totals. Mines of Divan only.
 */
public final class ScavengerHud implements HudElement {
	public static final ScavengerHud INSTANCE = new ScavengerHud();

	private String line1,line2,line3;
    private long nextTextRefresh;
    private int textWidth;

	private ScavengerHud() {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, DeltaTracker deltaTracker) {
		if (!NucleusMod.CONFIG.scavengerEnabled) {
			return;
		}
		if (JackpotAnimation.cinematicActive()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		if (client.gui.screen() instanceof MoveHudScreen) {
			return;
		}
		if (!HollowsDetector.inMinesOfDivan(client)) {
			return;
		}

        long now=System.nanoTime();
        if(line1==null || now-nextTextRefresh>=0){
            refreshText(client);nextTextRefresh=now+250_000_000L;
        }

		int x = NucleusMod.CONFIG.scavengerX;
		int y = NucleusMod.CONFIG.scavengerY;
		try {
			int maxX = Math.max(0, client.getWindow().getGuiScaledWidth() - 160);
			int maxY = Math.max(0, client.getWindow().getGuiScaledHeight() - 40);
			x = Math.min(Math.max(0, x), maxX);
			y = Math.min(Math.max(0, y), maxY);
		} catch (Exception ignored) {
		}

		int pad = 3;
		float s = NucleusMod.CONFIG.scavengerScale;
		if (s < 0.5f || s > 3.0f) {
			s = 1.0f;
		}
		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(s, s);
		try {
			HudTint.backdrop(gfx, -pad, -pad, textWidth + pad, 3 * 10 - 1 + pad);
			gfx.text(client.font, line1, 0, 0, 0xFFFFFFFF, true);
			gfx.text(client.font, line2, 0, 10, 0xFF55FF55, true);
			gfx.text(client.font, line3, 0, 20, 0xFFFFFF55, true);
		} finally {
			pose.popMatrix();
		}
	}

    private void refreshText(Minecraft client){
		if (NucleusMod.CONFIG.scavengerSetsPerHour) {
			line1 = "Sets: " + Scavenger.setsCompleted()
				+ " (" + String.format("%.1f", Scavenger.setsPerHour()) + "/h)"
				+ (Scavenger.afkPaused() ? " [AFK]" : "");
		} else {
			line1 = "Tools: " + Scavenger.toolEvents()
				+ " (" + String.format("%.1f", Scavenger.toolsPerHour()) + "/h)"
				+ (Scavenger.afkPaused() ? " [AFK]" : "");
		}
		long avg = Scavenger.avgSetMs();
		line2 = "Avg full set: " + fmtLong(avg);
		long avgFind = DivanDetectorSolver.avgFindMs();
		line3 = "Avg find chest: " + fmtDecimal(avgFind);

        textWidth=Math.max(client.font.width(line1),Math.max(client.font.width(line2),client.font.width(line3)));
    }

	/** m:ss, or --:-- for "none yet" (negative). */
	static String fmtLong(long ms) {
		if (ms < 0) {
			return "--:--";
		}
		long totalSec = ms / 1000L;
		return (totalSec / 60L) + ":" + String.format("%02d", totalSec % 60L);
	}

	/** m:ss.t with tenths (e.g. 2:05.3), or --:-- for "none yet". */
	static String fmtDecimal(long ms) {
		return ms < 0 ? "--:--" : fmtLong(ms) + "." + (ms % 1000L) / 100L;
	}
}
