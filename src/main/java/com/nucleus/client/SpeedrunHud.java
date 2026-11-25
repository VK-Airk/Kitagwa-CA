package com.nucleus.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import com.nucleus.NucleusMod;
import com.nucleus.SpeedrunStore;

/**
 * Speedrun HUD in Bal-timer style (dark box behind the text): live time +
 * split progress while running, final time when finished, best line
 * underneath. Lines are color-coded by the current split. Hollows-only.
 */
public final class SpeedrunHud implements HudElement {
	public static final SpeedrunHud INSTANCE = new SpeedrunHud();

	private SpeedrunHud() {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, DeltaTracker deltaTracker) {
		if (!HollowsDetector.isInCrystalHollows()) {
			return;
		}
		if (JackpotAnimation.cinematicActive()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		if (client.gui.screen() instanceof MoveHudScreen || client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
			return;
		}
        drawTimer(gfx,NucleusMod.SPEEDRUN.speedTimerX,NucleusMod.SPEEDRUN.speedTimerY,timerScale());
    }

    static float timerScale(){
        float scale=NucleusMod.SPEEDRUN.speedTimerScale;
        return scale>=0.5f && scale<=3f?scale:1f;
    }

    static void drawTimer(GuiGraphicsExtractor gfx,int x,int y,float s){
        var client=Minecraft.getInstance();
        SpeedrunManager.State state = SpeedrunManager.state();
		if (state == SpeedrunManager.State.IDLE) {
			return;
		}
		String line1;
		String line2 = null;
		int color1 = 0xFFFFFFFF;
		int color2 = 0xFFAAAAAA;
		if (state == SpeedrunManager.State.RUNNING) {
			String head = SpeedrunManager.currentHead();
			String headName = head == null ? "Place all crystals" : NucleusMod.SPEEDRUN.displayName(head);
			int headColor = head == null ? 0xFFFFFFFF : withFullAlpha(SpeedrunStore.colorOf(head));
			line1 = "Run " + SpeedrunStore.fmt(SpeedrunManager.liveMs())
				+ " (" + SpeedrunManager.splitIdx() + "/" + SpeedrunManager.splitCount() + ")"
				+ (SpeedrunManager.manuallyPaused() ? " [Paused]" : SpeedrunManager.afkPaused() ? " [AFK]" : "");
			line2 = "-> " + headName;
			color1 = 0xFFFFFFFF;
			color2 = headColor;
		} else {
			line1 = "Run " + SpeedrunStore.fmt(SpeedrunManager.finishedTotalMs()) + " done!";
			color1 = 0xFF55FF55;
		}

		String best = null;
		long bestMs = NucleusMod.SPEEDRUN.bestTotalMs;
		if (bestMs >= 0) {
			best = "Best " + SpeedrunStore.fmt(bestMs);
		}

		int w1 = client.font.width(line1);
		int w2 = line2 == null ? 0 : client.font.width(line2);
		int wb = best == null ? 0 : client.font.width(best);
		int wmax = Math.max(w1, Math.max(w2, wb));
		int lines = 1 + (line2 == null ? 0 : 1) + (best == null ? 0 : 1);
		String counter=NucleusMod.CONFIG.showRunCounter?SpeedrunManager.counter():null;

        if(counter!=null){wmax=Math.max(wmax,client.font.width(counter));lines++;}

        int pad = 3;
		var pose = gfx.pose();
		pose.pushMatrix();
		pose.translate((float) x, (float) y);
		pose.scale(s, s);
		try {
			HudTint.backdrop(gfx, -pad, -pad, wmax + pad, lines * 10 - 1 + pad);
			gfx.text(client.font, line1, 0, 0, color1, true);
			int yy = 10;
			if (line2 != null) {
				gfx.text(client.font, line2, 0, yy, color2, true);
				yy += 10;
			}
			if (best != null) {
				gfx.text(client.font, best, 0, yy, 0xFFFFD700, true);yy+=10;
			}
            if(counter!=null){gfx.text(client.font,counter,0,yy,0xFFE1CDCD,true);yy+=10;}

		} finally {
			pose.popMatrix();
		}
	}

	static void drawCrystals(GuiGraphicsExtractor gfx) {
        var cfg=NucleusMod.CONFIG;
        if(!cfg.kcaEnabled || !cfg.showGameplayHud || !cfg.showCrystalChecklist ||
            SpeedrunManager.state()==SpeedrunManager.State.IDLE || !HollowsDetector.isInCrystalHollows() ||
            JackpotAnimation.cinematicActive() || Minecraft.getInstance().gui.screen() instanceof MoveHudScreen)return;
        var client=Minecraft.getInstance();
        float scale=cfg.crystalHudScale;
        String line=cfg.crystalLayout != com.nucleus.NucleusConfig.CrystalLayout.COMPACT?null:CrystalTracker.checklist();
        int width=line==null?CrystalTracker.iconWidth(client.font):client.font.width(line);
        int height=line==null?CrystalTracker.iconHeight():12;
        int pad=(int)Math.ceil(3*scale);
        int x=Math.clamp(cfg.crystalHudX,pad,Math.max(pad,gfx.guiWidth()-pad-(int)Math.ceil(width*scale)));
        int y=Math.clamp(cfg.crystalHudY,pad,Math.max(pad,gfx.guiHeight()-pad-(int)Math.ceil(height*scale)));
        if(line==null)CrystalTracker.drawIcons(gfx,x,y,scale,width);
        else drawHudLine(gfx,line,x,y,scale,0xFFFFFFFF,width);
    }
    static void drawToolSets(GuiGraphicsExtractor g) {
        var cfg = NucleusMod.CONFIG;
        var screen = Minecraft.getInstance().gui.screen();
        if (!cfg.kcaEnabled || !cfg.showGameplayHud || !cfg.showToolSets || !HollowsDetector.isInCrystalHollows()
            || JackpotAnimation.cinematicActive() || screen instanceof MoveHudScreen
            || screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) return;
        String label = NucleusMod.SPEEDRUN.toolSetLabel();
        int width=Minecraft.getInstance().font.width(label);
        int x = Math.clamp(cfg.toolHudX,3,Math.max(3,g.guiWidth()-3-(int)Math.ceil(width*cfg.toolHudScale)));
        int y = Math.clamp(cfg.toolHudY,3,Math.max(3,g.guiHeight()-3-(int)Math.ceil(12*cfg.toolHudScale)));
        drawHudLine(g,label,x,y,cfg.toolHudScale,0xFFFFD6AE,width);
    }
    static void drawGoal(GuiGraphicsExtractor gfx){
        var cfg=NucleusMod.CONFIG;
        if(!cfg.kcaEnabled || !cfg.showGameplayHud || !cfg.showRunGoal || !HollowsDetector.isInCrystalHollows() || JackpotAnimation.cinematicActive() || Minecraft.getInstance().gui.screen() instanceof MoveHudScreen)return;
        var store=NucleusMod.SPEEDRUN;
        drawHudLine(gfx,store.goalLabel(),cfg.runGoalX,cfg.runGoalY,cfg.runGoalScale,store.displayedRuns()>=store.runGoal?0xFF88EE99:0xFFFFD6AE);
    }
    static void drawHudLine(GuiGraphicsExtractor gfx,String text,int x,int y,float scale,int color){
        drawHudLine(gfx,text,x,y,scale,color,Minecraft.getInstance().font.width(text));
    }
    static void drawHudLine(GuiGraphicsExtractor gfx,String text,int x,int y,float scale,int color,int width){
        var font=Minecraft.getInstance().font;
        var pose=gfx.pose();
        pose.pushMatrix();
        try{
            pose.translate(x,y);
            pose.scale(scale,scale);
            HudTint.backdrop(gfx,-3,-3,width+3,12);
            gfx.text(font,text,0,0,color,true);
        }finally{pose.popMatrix();}
    }

    private static int withFullAlpha(int rgb) {
		return 0xFF000000 | (rgb & 0x00FFFFFF);
	}
}
