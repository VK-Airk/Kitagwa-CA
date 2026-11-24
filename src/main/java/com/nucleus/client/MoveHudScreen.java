package com.nucleus.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import com.nucleus.NucleusMod;
import com.nucleus.SpeedrunStore;

/**
 * One move screen for every HUD element (Bal, speedrun, lobby day,
 * scavenger, alerts). Drag a preview to move it, scroll over one to resize
 * it (0.5x-3.0x); the hovered element gets an outline. The gambling
 * animation is intentionally not here.
 */
public class MoveHudScreen extends Screen {
	private static final float MIN_SCALE = 0.5f;
	private static final float MAX_SCALE = 3.0f;
	private static final int PAD = 3;

	private final Screen parent;

	private record HudSwitch(String label, java.util.function.BooleanSupplier enabled, Consumer<Boolean> set) {}

    private static final class Entry {
        final List<HudSwitch> switches;
		final String name;
		final String[] lines;
		final int[] colors;
		final float[] lineScales;
		dev.purplepad.DungeonHudPreview dungeon;
        boolean dirty, iconCrystals, hollowsMap;
		int x;
		int y;
		float scale;
		final Consumer<Entry> commit;

		Entry(String name, String[] lines, int[] colors, float[] lineScales,
			int x, int y, float scale, Consumer<Entry> commit, HudSwitch... switches) {
            this.switches=List.of(switches);
			this.name = name;
			this.lines = lines;
			this.colors = colors;
			this.lineScales = lineScales;
			this.x = x;
			this.y = y;
			this.scale = scale;
			this.commit = commit;
		}

		float boxW(Font font) {
			if(hollowsMap)return HollowsMap.SIZE+PAD*2;
            if(iconCrystals)return CrystalTracker.iconWidth(font)+PAD*2;
            return dungeon==null?HudText.width(font, lines, lineScales) + PAD * 2:dungeon.width;
		}

		int left(){return dungeon==null?Math.round(x-PAD*scale):x;}
        int top(){return dungeon==null?Math.round(y-PAD*scale):y;}

        float boxH() {
			if(hollowsMap)return HollowsMap.height(true)+PAD*2;
            if(iconCrystals)return CrystalTracker.iconHeight()+PAD*2;
            return dungeon==null?HudText.contentHeight(lines, lineScales) - 1 + PAD * 2:dungeon.height;
		}
	}

	private final List<Entry> entries = new ArrayList<>();
	private Entry grabbed = null;
    private int selected=-1;
    private Button chooser;
    private final List<Button> switches = new ArrayList<>();
    private void select(int index){
        selected=index; grabbed=null;
        for(Button b:switches)removeWidget(b);
        switches.clear();
        if(selected<0){chooser.setMessage(Component.literal("Select a HUD"));return;}
        Entry entry=entries.get(selected);
        chooser.setMessage(Component.literal(entry.name));
        int count=entry.switches.size(), w=Math.min(98,(width-12)/count-4);
        int x=(width-(count*w+(count-1)*4))/2;
        for(HudSwitch control:entry.switches){
            Button b=addRenderableWidget(NucleusUi.NButton.create(switchLabel(control),btn->{
                control.set.accept(!control.enabled.getAsBoolean());
                if(entry.dungeon==null)NucleusMod.CONFIG.save();
                btn.setMessage(switchLabel(control));
            }).pos(x,height-47).size(w,19).build());
            switches.add(b);x+=w+4;
        }
    }
    private Component switchLabel(HudSwitch control){
        return Component.literal(control.label+"  "+(control.enabled.getAsBoolean()?"ON":"OFF"));
    }
    private void cycle(int direction){select(selected<0?(direction>0?0:entries.size()-1):Math.floorMod(selected+direction,entries.size()));}
	private double grabDX = 0;
	private double grabDY = 0;

	public MoveHudScreen(Screen parent) {
		super(Component.literal("Move GUI elements"));
		this.parent = parent;
	}

	public static void open(Screen parent) {
		Minecraft client = Minecraft.getInstance();
		client.schedule(() -> client.gui.setScreen(new MoveHudScreen(parent)));
	}

	private static float clampScale(float s) {
		return Math.min(MAX_SCALE, Math.max(MIN_SCALE, s));
	}

	@Override
	protected void init() {
		switches.clear();
        entries.clear();
		grabbed = null;
		if(dev.purplepad.KcaEdition.NUCLEUS){
        String[] balLines = BalTimerHud.lines();
		int[] balColors = BalTimerHud.colors();
		if (balLines.length == 0) {
			balLines = new String[] { BalTimer.displayString() };
			balColors = new int[] { BalTimer.displayColor() };
		}
		entries.add(new Entry("Bal timer", balLines, balColors, HudText.ones(balLines.length),
			NucleusMod.CONFIG.balTimerX, NucleusMod.CONFIG.balTimerY,
			clampScale(NucleusMod.CONFIG.balTimerScale),
			e -> {
				NucleusMod.CONFIG.balTimerX = e.x;
				NucleusMod.CONFIG.balTimerY = e.y;
				NucleusMod.CONFIG.balTimerScale = e.scale;
			},
            new HudSwitch("Timer", () -> NucleusMod.CONFIG.balTimerEnabled, value -> NucleusMod.CONFIG.balTimerEnabled=value),
            new HudSwitch("Detector", () -> NucleusMod.CONFIG.balDetectorEnabled, value -> NucleusMod.CONFIG.balDetectorEnabled=value)));
		var speedLines=new java.util.ArrayList<>(java.util.List.of(speedrunLines()));
        var speedColors=java.util.Arrays.copyOf(speedrunColors(),speedLines.size()+2);
        if(NucleusMod.CONFIG.showRunCounter){speedColors[speedLines.size()]=0xFFE1CDCD;speedLines.add(SpeedrunManager.counter());}

        entries.add(new Entry("Speedrun timer", speedLines.toArray(String[]::new), speedColors, HudText.ones(speedLines.size()),
			NucleusMod.SPEEDRUN.speedTimerX, NucleusMod.SPEEDRUN.speedTimerY,
			clampScale(NucleusMod.SPEEDRUN.speedTimerScale),
			e -> {
				NucleusMod.SPEEDRUN.speedTimerX = e.x;
				NucleusMod.SPEEDRUN.speedTimerY = e.y;
				NucleusMod.SPEEDRUN.speedTimerScale = e.scale;
			},
            new HudSwitch("Timer", () -> NucleusMod.CONFIG.speedrunEnabled, value -> { NucleusMod.CONFIG.speedrunEnabled=value; if(!value) SpeedrunManager.onLeave(); })));
		var cfg=NucleusMod.CONFIG;
        entries.add(new Entry("Nucleus run goal",new String[]{NucleusMod.SPEEDRUN.goalLabel()},new int[]{0xFFFFD6AE},HudText.ones(1),
            cfg.runGoalX,cfg.runGoalY,clampScale(cfg.runGoalScale),e->{cfg.runGoalX=e.x;cfg.runGoalY=e.y;cfg.runGoalScale=e.scale;},
            new HudSwitch("Run goal",()->cfg.showRunGoal,value->cfg.showRunGoal=value)));
        entries.add(new Entry("Crystal checklist",new String[]{CrystalTracker.checklist()},new int[]{0xFFFFFFFF},HudText.ones(1),
            cfg.crystalHudX,cfg.crystalHudY,clampScale(cfg.crystalHudScale),
            e->{cfg.crystalHudX=e.x;cfg.crystalHudY=e.y;cfg.crystalHudScale=e.scale;},
            new HudSwitch("Crystals",()->cfg.showCrystalChecklist,value->cfg.showCrystalChecklist=value)));
        entries.getLast().iconCrystals=cfg.crystalLayout != com.nucleus.NucleusConfig.CrystalLayout.COMPACT;
        var map=new Entry("Crystal Hollows map",new String[0],new int[0],new float[0],
            cfg.hollowsMapX,cfg.hollowsMapY,clampScale(cfg.hollowsMapScale),
            e->{cfg.hollowsMapX=e.x;cfg.hollowsMapY=e.y;cfg.hollowsMapScale=e.scale;},
            new HudSwitch("Display",()->cfg.hollowsMap,v->cfg.hollowsMap=v));
        map.hollowsMap=true;
        entries.add(map);
        entries.add(new Entry("Stash",new String[]{StashHud.sample()},new int[]{0xFFFFC2DC},HudText.ones(1),
            cfg.stashX,cfg.stashY,clampScale(cfg.stashScale),e->{cfg.stashX=e.x;cfg.stashY=e.y;cfg.stashScale=e.scale;},
            new HudSwitch("Stash",()->cfg.showStash,v->cfg.showStash=v)));
        entries.add(new Entry("Divan tool sets",new String[]{NucleusMod.SPEEDRUN.toolSetLabel()},new int[]{0xFFFFD6AE},HudText.ones(1),
            cfg.toolHudX,cfg.toolHudY,clampScale(cfg.toolHudScale),e->{cfg.toolHudX=e.x;cfg.toolHudY=e.y;cfg.toolHudScale=e.scale;},
            new HudSwitch("Tool sets",()->cfg.showToolSets,v->cfg.showToolSets=v)));
        String day = LobbyDay.displayDay();
		String lobby = LobbyDay.displayLobby();
		entries.add(new Entry("Lobby day",
			new String[] {
				day.isEmpty() ? "☀ Day 123 (45%)" : day,
				lobby.isEmpty() ? "Lobby 1:23:45" : lobby
			},
			new int[] { 0xFFFFD700, 0xFFAAAAAA },
			HudText.ones(2),
			NucleusMod.CONFIG.lobbyDayX, NucleusMod.CONFIG.lobbyDayY,
			clampScale(NucleusMod.CONFIG.lobbyDayScale),
			e -> {
				NucleusMod.CONFIG.lobbyDayX = e.x;
				NucleusMod.CONFIG.lobbyDayY = e.y;
				NucleusMod.CONFIG.lobbyDayScale = e.scale;
			},
            new HudSwitch("Display", () -> NucleusMod.CONFIG.lobbyDayEnabled, value -> NucleusMod.CONFIG.lobbyDayEnabled=value)));
		entries.add(new Entry("Scavenger tracker",
			new String[] {
				"Tools: " + Scavenger.toolEvents() + " (" + String.format("%.1f", Scavenger.toolsPerHour()) + "/h)",
				"Avg full set: " + ScavengerHud.fmtLong(Scavenger.avgSetMs()),
				"Avg find chest: " + ScavengerHud.fmtDecimal(DivanDetectorSolver.avgFindMs())
			},
			new int[] { 0xFFFFFFFF, 0xFF55FF55, 0xFFFFFF55 },
			HudText.ones(3),
			NucleusMod.CONFIG.scavengerX, NucleusMod.CONFIG.scavengerY,
			clampScale(NucleusMod.CONFIG.scavengerScale),
			e -> {
				NucleusMod.CONFIG.scavengerX = e.x;
				NucleusMod.CONFIG.scavengerY = e.y;
				NucleusMod.CONFIG.scavengerScale = e.scale;
			},
            new HudSwitch("Tracker", () -> NucleusMod.CONFIG.scavengerEnabled, value -> NucleusMod.CONFIG.scavengerEnabled=value)));
		entries.add(new Entry("Terror stacks",
			new String[] { String.valueOf(TerrorStacks.count()) },
			new int[] { TerrorStacks.color() },
			HudText.ones(1),
			NucleusMod.CONFIG.terrorStacksX, NucleusMod.CONFIG.terrorStacksY,
			clampScale(NucleusMod.CONFIG.terrorStacksScale),
			e -> {
				NucleusMod.CONFIG.terrorStacksX = e.x;
				NucleusMod.CONFIG.terrorStacksY = e.y;
				NucleusMod.CONFIG.terrorStacksScale = e.scale;
			},
            new HudSwitch("Display", () -> NucleusMod.CONFIG.terrorStacksEnabled, value -> NucleusMod.CONFIG.terrorStacksEnabled=value)));
		entries.add(alertEntry());
        }
        if(dev.purplepad.KcaEdition.DUNGEONS)for(boolean timer:new boolean[]{true,false}){
            var hud=new dev.purplepad.DungeonHudPreview(timer,font,width,height);
            var e=new Entry(timer?"Purple Pad Timer":"Secrets and rooms",new String[0],new int[0],new float[0],hud.x,hud.y,hud.scale,v->hud.save(v.x,v.y,v.scale),
                new HudSwitch("Display",hud::enabled,hud::setEnabled));
            e.dungeon=hud;entries.add(e);
        }
		// Keep everything on screen (window may have shrunk since saving).
		for (Entry e : entries) {
			float w = e.boxW(this.font) * e.scale;
			float h = e.boxH() * e.scale;
			e.x = (int) Math.min(Math.max(0, e.x), Math.max(0, this.width - w));
			e.y = (int) Math.min(Math.max(0, e.y), Math.max(0, this.height - h));
		}
		
        int cx=width/2,y=height-25;
        addRenderableWidget(NucleusUi.NButton.create(Component.literal("<"),b->cycle(-1)).pos(cx-145,y).size(25,19).build());
        chooser=addRenderableWidget(NucleusUi.NButton.create(Component.literal("Select a HUD"),b->cycle(1)).pos(cx-117,y).size(125,19).build());
        addRenderableWidget(NucleusUi.NButton.create(Component.literal(">"),b->cycle(1)).pos(cx+11,y).size(25,19).build());
        addRenderableWidget(NucleusUi.NButton.create(Component.literal("Done"),btn->onClose()).pos(cx+42,y).size(100,19).build());
        select(selected);
	}

	/** Alerts live at an offset from screen center (stays centered on resize). */
	private Entry alertEntry() {
		String[] lines;
		int[] colors;
		if (PetAlert.alertActive()) {
			String pet = PetAlert.currentPet();
			lines = new String[] { "WRONG PET!", "Equip Mole" + (pet == null ? "" : " (now: " + pet + ")") };
			colors = new int[] { 0xFFFF5555, 0xFFFFAA00 };
		} else {
			lines = new String[] { "WRONG PET!", "Equip Mole (now: Ghoul)" };
			colors = new int[] { 0xFFFF5555, 0xFFFFAA00 };
		}
		float scale = clampScale(NucleusMod.CONFIG.alertScale);
		float boxW = (HudText.width(this.font, lines, AlertHud.LINE_SCALES) + PAD * 2) * scale;
		int x = (int) (this.width / 2f + NucleusMod.CONFIG.alertOffX - boxW / 2f);
		int y = (int) (this.height / 2f + NucleusMod.CONFIG.alertOffY);
		return new Entry("Alerts", lines, colors, AlertHud.LINE_SCALES, x, y, scale,
			e -> {
				float bw = (HudText.width(this.font, e.lines, e.lineScales) + PAD * 2) * e.scale;
				NucleusMod.CONFIG.alertOffX = Math.round(e.x + bw / 2f - this.width / 2f);
				NucleusMod.CONFIG.alertOffY = Math.round(e.y - this.height / 2f);
				NucleusMod.CONFIG.alertScale = e.scale;
			},
            new HudSwitch("Wrong pet", () -> NucleusMod.CONFIG.petAlertEnabled, value -> NucleusMod.CONFIG.petAlertEnabled=value),
            new HudSwitch("Low tools", () -> NucleusMod.CONFIG.lowToolsAlertEnabled, value -> NucleusMod.CONFIG.lowToolsAlertEnabled=value),
            new HudSwitch("Bad lobby", () -> NucleusMod.CONFIG.badLobbyAlertEnabled, value -> NucleusMod.CONFIG.badLobbyAlertEnabled=value));
	}

	private static String[] speedrunLines() {
		if (SpeedrunManager.state() == SpeedrunManager.State.RUNNING) {
			String head = SpeedrunManager.currentHead();
			return new String[] {
				"Run " + SpeedrunStore.fmt(SpeedrunManager.liveMs())
					+ " (" + SpeedrunManager.splitIdx() + "/" + SpeedrunManager.splitCount() + ")",
				"-> " + (head == null ? "..." : NucleusMod.SPEEDRUN.displayName(head))
			};
		}
		if (SpeedrunManager.state() == SpeedrunManager.State.FINISHED) {
			return new String[] { "Run " + SpeedrunStore.fmt(SpeedrunManager.finishedTotalMs()) + " done!" };
		}
		return new String[] { "Run --:--.- (0/8)", "-> Give egg" };
	}

	private static int[] speedrunColors() {
		if (SpeedrunManager.state() == SpeedrunManager.State.FINISHED) {
			return new int[] { 0xFF55FF55 };
		}
		return new int[] { 0xFFFFFFFF, 0xFF55FFFF };
	}

	private void saveAll() {
		for (Entry e : entries) {
			if(e.dirty){e.commit.accept(e);e.dirty=false;if(NucleusMod.CONFIG.kcaEnabled)Achievements.unlockFun("kca_ui_hud");}
		}
		NucleusMod.CONFIG.save();
		NucleusMod.SPEEDRUN.save();
	}

	private Entry entryAt(double mx, double my) {
		for (int i = entries.size(); i >= 0; i--) {
            if(i==selected || (i==entries.size() && selected<0))continue;
            Entry e=entries.get(i==entries.size()?selected:i);
			float w = e.boxW(this.font) * e.scale;
			float h = e.boxH() * e.scale;
			if (mx >= e.left() && mx < e.left() + w && my >= e.top() && my < e.top() + h) {
				return e;
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if(super.mouseClicked(event,doubled))return true;
        if (event.button() == 0) {
			Entry hit = entryAt(event.x(), event.y());
			if (hit != null) {
				if(selected!=entries.indexOf(hit))select(entries.indexOf(hit));
                grabbed = hit;
				grabDX = event.x() - hit.x;
				grabDY = event.y() - hit.y;
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (grabbed != null) {
			float w = grabbed.boxW(this.font) * grabbed.scale;
			float h = grabbed.boxH() * grabbed.scale;
			grabbed.dirty=true;
            grabbed.x = (int) Math.min(Math.max(0, Math.round(event.x() - grabDX)),
				Math.max(0, this.width - w));
			grabbed.y = (int) Math.min(Math.max(0, Math.round(event.y() - grabDY)),
				Math.max(0, this.height - h));
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (grabbed != null && event.button() == 0) {
			boolean changed=grabbed.dirty;
            grabbed = null;
            if(changed)saveAll();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		Entry hit = entryAt(mouseX, mouseY);
		if (hit != null && scrollY != 0) {
            if(selected!=entries.indexOf(hit))select(entries.indexOf(hit));
			hit.dirty=true;
            hit.scale=Math.clamp(hit.scale+(float)Math.signum(scrollY)*0.1f,MIN_SCALE,hit.dungeon!=null && hit.dungeon.timer?6f:MAX_SCALE);
			hit.x=Math.clamp(hit.x,0,Math.max(0,(int)(width-hit.boxW(font)*hit.scale)));
            hit.y=Math.clamp(hit.y,0,Math.max(0,(int)(height-hit.boxH()*hit.scale)));
            saveAll();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		gfx.fill(0, 0, this.width, this.height, 0x80000000);
        Entry hovered = entryAt(mouseX, mouseY);
		for(int i=0;i<=entries.size();i++){
            if(i==selected || (i==entries.size() && selected<0))continue;
            Entry e=entries.get(i==entries.size()?selected:i);
			float w = e.boxW(this.font);
			float h = e.boxH();
			
			int x1=e.left(), y1=e.top();
            int x2=x1+(int)Math.ceil(w*e.scale), y2=y1+(int)Math.ceil(h*e.scale);
            // Make overlapping previews legible without changing their saved positions.
            gfx.fill(x1,y1,x2,y2,0xF51C1823);
            if(e.hollowsMap){HollowsMap.draw(gfx,e.x,e.y,e.scale,true);}
            else if(e.iconCrystals){CrystalTracker.drawIcons(gfx,e.x,e.y,e.scale);}
            else if(e.dungeon!=null){e.dungeon.draw(gfx,e.x,e.y,e.scale);}else{
            var pose = gfx.pose();
			pose.pushMatrix();
			pose.translate((float) e.x, (float) e.y);
			pose.scale(e.scale, e.scale);
			try {
				HudTint.backdrop(gfx, -PAD, -PAD, (int) (w - PAD), (int) (h - PAD));
				HudText.draw(gfx, this.font, e.lines, e.colors, e.lineScales);
			} finally {
				pose.popMatrix();
			}
			}
            // Hover outline so overlapping elements are easy to tell apart.
			if (e == hovered || i==entries.size()) {
				int color = i==entries.size()?dev.purplepad.KcaTheme.ROSE:0xFFE9E4F6;
				gfx.fill(x1 - 1, y1 - 1, x2 + 1, y1, color);
				gfx.fill(x1 - 1, y2, x2 + 1, y2 + 1, color);
				gfx.fill(x1 - 1, y1, x1, y2, color);
				gfx.fill(x2, y1, x2 + 1, y2, color);
			}
		}
        String hint=selected<0?(hovered==null?"Click a HUD to see its settings":hovered.name+"  ·  Click for settings"):"Drag to move  ·  Scroll to resize  ·  "+Math.round(entries.get(selected).scale*100)+"%";
        int hintY=height-(selected<0?39:61), half=font.width(hint)/2;
        gfx.fill(width/2-half-4,hintY-2,width/2+half+5,hintY+font.lineHeight+1,0xE51C1823);
        gfx.centeredText(font,hint,width/2,hintY,dev.purplepad.KcaTheme.PETAL);
        super.extractRenderState(gfx,mouseX,mouseY,partialTick);
	}

	@Override
	public void onClose() {
		saveAll(); Minecraft.getInstance().gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
