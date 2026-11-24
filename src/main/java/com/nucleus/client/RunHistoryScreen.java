package com.nucleus.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.nucleus.NucleusMod;
import com.nucleus.SpeedrunStore;

/**
 * Run History: recent / fastest / most profitable finished runs, each with
 * its profit (bundle value minus the run's costs, SkyHanni's model), plus
 * the average profit per run up top. Click a run for its splits and every
 * loot line with its price. Cancelled runs never record.
 */
public class RunHistoryScreen extends Screen {
	private enum Mode {
		RECENT("Recent", Items.CLOCK, 0x5AA9FF),
		FASTEST("Fastest", Items.SUGAR, 0x62E06A),
		PROFIT("Most profit", Items.GOLD_INGOT, 0xFFD34A);

		final String label;
		final net.minecraft.world.item.Item icon;
		final int accent;

		Mode(String label, net.minecraft.world.item.Item icon, int accent) {
			this.label = label;
			this.icon = icon;
			this.accent = accent;
		}
	}

	private static final int ROSE=dev.purplepad.KcaTheme.ROSE;
    private static final net.minecraft.resources.Identifier TROPHY=net.minecraft.resources.Identifier.fromNamespaceAndPath("kca","textures/pb_trophy.png");
    private boolean personalBest;
    private List<java.util.Map.Entry<String,Long>> pbSplits=List.of();
    private long[] stats;
    private long averageMs;
    static void tabs(java.util.function.Consumer<NucleusUi.NButton> add,int cx,int available,int selected,java.util.function.IntConsumer change){
        String[] labels={"Timer","Personal best","Run history"};
        int w=Math.min(120,(available-8)/3),x=cx-(3*w+8)/2;
        for(int i=0;i<labels.length;i++){int page=i;add.accept(dev.purplepad.KcaTheme.tab(labels[i],x+i*(w+4),49,w,i==selected,NucleusMod.CONFIG.nucleusAccent,()->change.accept(page)));}
    }
    private static final int ROW_H = 24;
	private static final int LIST_SIZE = 25;
	private static final int GREEN = 0x62E06A;
	private static final int RED = 0xFF5A5A;

	private final Screen parent;
	private static Mode mode = Mode.RECENT;
	private List<SpeedrunStore.RunRecord> view = new ArrayList<>();
	private int selected = -1;
	private double scroll = 0;

	public RunHistoryScreen(Screen parent,boolean personalBest) {
		super(Component.literal("Run History"));
		this.parent = parent;
        this.personalBest=personalBest;
	}

	// ------------------------------------------------------------ profit

	/** Bundle value of a run (stored at the time, else priced now). */
	static long lootValue(SpeedrunStore.RunRecord r) {
		if (r.lootValue >= 0) {
			return r.lootValue;
		}
		return DropPrices.bundleValue(r.loot);
	}

	/** Profit = bundle value - Jungle Key - robot parts/apparatus. null when no loot was seen. */
	static Long profit(SpeedrunStore.RunRecord r) {
		if (r.loot == null || r.loot.isEmpty()) {
			return null;
		}
		long cost = r.runCost >= 0 ? r.runCost : DropPrices.runCost();
		return lootValue(r) - cost;
	}

	/** {average, best, total, count} over runs with known loot. */
	static long[] profitStats(List<SpeedrunStore.RunRecord> runs) {
		long total = 0;
		long best = Long.MIN_VALUE;
		int n = 0;
		for (SpeedrunStore.RunRecord r : runs) {
			Long p = profit(r);
			if (p == null) {
				continue;
			}
			total += p;
			best = Math.max(best, p);
			n++;
		}
		return new long[] { n == 0 ? 0 : total / n, n == 0 ? 0 : best, total, n };
	}

	/**
	 * Coins per hour: runs per hour (1 hour / average run time) times the
	 * average profit per run. null when either side is unknown.
	 */
	static Long coinsPerHour(long avgProfit, long avgRunMs) {
		if (avgRunMs <= 0) {
			return null;
		}
		return Math.round(3_600_000.0 / avgRunMs * avgProfit);
	}

	/** 12,345 -> "12.3k", 1,234,567 -> "1.23M", signed when asked. */
	static String coins(long v, boolean signed) {
		String sign = v < 0 ? "-" : signed ? "+" : "";
		double a = Math.abs((double) v);
		String body;
		if (a >= 1e9) {
			body = String.format(Locale.ROOT, "%.2fB", a / 1e9);
		} else if (a >= 1e6) {
			body = String.format(Locale.ROOT, "%.2fM", a / 1e6);
		} else if (a >= 1e3) {
			body = String.format(Locale.ROOT, "%.1fk", a / 1e3);
		} else {
			body = String.format(Locale.ROOT, "%d", (long) a);
		}
		return sign + body;
	}

	// ------------------------------------------------------------ layout

	private int panelX1() {
		return Math.max(6, this.width / 2 - 200);
	}

	private int panelX2() {
		return Math.min(this.width - 6, this.width / 2 + 200);
	}

	private int viewTop() {
		return personalBest ? 118 : selected >= 0 ? 139 : (height<225?127:167);
	}

	private int viewBottom() {
		return this.height - 36;
	}

	@Override
	protected void init() {
		List<SpeedrunStore.RunRecord> all = NucleusMod.SPEEDRUN.runHistory;
		stats=profitStats(all);
        averageMs=NucleusMod.SPEEDRUN.trimmedAverageMs();
        pbSplits=NucleusMod.SPEEDRUN.bestRunSplits.entrySet().stream().filter(e->e.getValue()!=null && e.getValue()>=0).sorted(java.util.Map.Entry.comparingByValue()).toList();
		view = new ArrayList<>();
		if (mode == Mode.RECENT) {
			for (int i = all.size() - 1; i >= 0 && view.size() < LIST_SIZE; i--) {
				view.add(all.get(i));
			}
		} else {
			List<SpeedrunStore.RunRecord> sorted = new ArrayList<>(all);
			if (mode == Mode.FASTEST) {
				sorted.sort((a, b) -> Long.compare(a.totalMs, b.totalMs));
			} else {
				sorted.removeIf(r -> profit(r) == null);
				sorted.sort((a, b) -> Long.compare(profit(b), profit(a)));
			}
			for (int i = 0; i < Math.min(LIST_SIZE, sorted.size()); i++) {
				view.add(sorted.get(i));
			}
		}
		if (selected >= view.size()) {
			selected = -1;
		}
		int cx = this.width / 2;
        tabs(this::addRenderableWidget,cx,panelX2()-panelX1()-8,personalBest?1:2,page->{
            if(page==0){minecraft.gui.setScreen(parent);return;}
            personalBest=page==1;selected=-1;scroll=0;rebuildWidgets();
        });
        if (!personalBest && selected < 0) {
            Mode[] modes = Mode.values();
			int bw = Math.min(120, (panelX2() - panelX1() - 16) / modes.length);
			int bx = cx - (bw * modes.length + 4 * (modes.length - 1)) / 2;
			for (Mode m : modes) {
				NucleusUi.NButton b = NucleusUi.NButton.create(Component.literal(m.label), btn -> {
					mode = m;
					selected = -1;
					scroll = 0;
					rebuildWidgets();
				}).pos(bx, height<225?102:143).size(bw, 20).accent(m.accent).icon(new ItemStack(m.icon)).build();
				b.selected(m == mode);
				addRenderableWidget(b);
				bx += bw + 4;
			}
			NucleusUi.NToggle apparatus = NucleusUi.NToggle.builder(
				Component.literal("Use Apparatus cost"), this.font)
				.pos(panelX1() + 8, this.height - 28).selected(NucleusMod.CONFIG.nucleusUsesApparatus)
				.onValueChange((t, val) -> {
					NucleusMod.CONFIG.nucleusUsesApparatus = val;
                    stats=profitStats(all);
					NucleusMod.CONFIG.save();
				}).build();
			apparatus.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
				"Run cost = Jungle Key + the 6 robot parts.\nTurn on to count a Precursor Apparatus\ninstead of the robot parts in profit stats.\n(Applies to runs without a stored cost.)")));
			addRenderableWidget(apparatus);
		}
		addRenderableWidget(NucleusUi.NButton.create(
			Component.literal(selected >= 0 ? "Back to list" : "Back"),
			btn -> {
				if (selected >= 0) {
					selected = -1;
					scroll = 0;
					rebuildWidgets();
				} else {
					Minecraft client = Minecraft.getInstance();
					client.gui.setScreen(parent);
				}
			}).pos(panelX2() - 108, this.height - 28).size(100, 20).accent(0xB8B8C8).build());
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		double content = personalBest ? pbSplits.size()*18 : selected >= 0 ? detailHeight() : view.size() * ROW_H;
		double max = Math.max(0, content - (viewBottom() - viewTop()));
		if (max > 0 && mouseY >= viewTop() && mouseY <= viewBottom()) {
			scroll = Math.min(max, Math.max(0, scroll - scrollY * 16));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
		if (event.button() == 0 && !personalBest && selected < 0 && event.y() >= viewTop() && event.y() <= viewBottom()
			&& event.x() >= panelX1() && event.x() <= panelX2()) {
			int idx = (int) ((event.y() - viewTop() + scroll) / ROW_H);
			if (idx >= 0 && idx < view.size()) {
				selected = idx;
				scroll = 0;
				rebuildWidgets();
				return true;
			}
		}
		return super.mouseClicked(event, doubled);
	}

	private double detailHeight() {
		if (selected < 0 || selected >= view.size()) {
			return 0;
		}
		SpeedrunStore.RunRecord r = view.get(selected);
		return 16 + r.ids.size() * 11 + 18 + (r.loot.isEmpty() ? 12 : r.loot.size() * 11) + 50;
	}

	// ------------------------------------------------------------ drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		dev.purplepad.KcaTheme.backdrop(gfx,width,height,dev.purplepad.KcaTheme.Branch.NUCLEUS);
		dev.purplepad.KcaTheme.panel(gfx,panelX1(),46,panelX2()-panelX1(),this.height-78);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(gfx, mouseX, mouseY, partialTick);
		int cx = this.width / 2;
		dev.purplepad.KcaTheme.heading(gfx,font,"KCA",cx,6,2);
        String page=personalBest?"Personal best":selected<0?"Run history":"Run history / Splits";
        gfx.centeredText(font,"Nucleus / Speedruns / "+page,cx,30,NucleusMod.CONFIG.nucleusAccent);
        if(personalBest || selected<0) drawRecordBanner(gfx);
        if(personalBest){drawPersonalBest(gfx);return;}
		if (selected >= 0 && selected < view.size()) {
			drawDetail(gfx, view.get(selected), cx);
			return;
		}
		selected = -1;
		if(height>=225)drawStats(gfx, cx);
		if (view.isEmpty()) {
			gfx.centeredText(this.font, mode == Mode.PROFIT ? "No runs with bundle loot yet." : "No runs yet.",
				cx, viewTop() + 12, 0xFFAAAAAA);
			gfx.centeredText(this.font, "Finished runs land here.", cx, viewTop() + 24, 0xFF666677);
			return;
		}
		int x1 = panelX1() + 8;
		int x2 = panelX2() - 8;
		int y = (int) (viewTop() - scroll);
		gfx.enableScissor(panelX1(), viewTop(), panelX2(), viewBottom());
		try {
			for (int i = 0; i < view.size(); i++) {
				int ry = y + i * ROW_H;
				if (ry + ROW_H < viewTop() || ry > viewBottom()) {
					continue;
				}
				boolean hover = mouseX >= x1 && mouseX <= x2 && mouseY >= ry && mouseY < ry + ROW_H - 3
					&& mouseY >= viewTop() && mouseY <= viewBottom();
				drawRow(gfx, view.get(i), i, x1, ry, x2, hover);
			}
		} finally {
			gfx.disableScissor();
		}
	}

	/** Stat tiles: average profit per run, coins per hour, best, total, average time. */
	private void drawStats(GuiGraphicsExtractor gfx, int cx) {
		long[] st = stats;
		int n = (int) st[3];
		long avgMs = averageMs;
		Long perHour = n == 0 ? null : coinsPerHour(st[0], avgMs);
		String[][] tiles = {
			{ "PROFIT / RUN", n == 0 ? "--" : coins(st[0], true) },
			{ "COINS / HOUR", perHour == null ? "--" : coins(perHour, true) },
			{ "BEST PROFIT", n == 0 ? "--" : coins(st[1], true) },
			{ "TOTAL", n == 0 ? "--" : coins(st[2], true) },
			{ "AVG TIME", SpeedrunStore.fmt(avgMs) },
		};
		int timeTile = tiles.length - 1;
		int x1 = panelX1() + 8;
		int x2 = panelX2() - 8;
		int gap = 4;
		int tw = (x2 - x1 - gap * (tiles.length - 1)) / tiles.length;
		for (int i = 0; i < tiles.length; i++) {
			int tx = x1 + i * (tw + gap);
			int accent = i == timeTile ? 0x5AA9FF : 0xFFD34A;
			gfx.fillGradient(tx, 104, tx + tw, 135, 0xCB2A2028, 0xCC17121A);
			gfx.fill(tx, 104, tx + tw, 105, 0xFF000000 | accent);
			float ls = Math.min(1f, (tw - 4) / (float) Math.max(1, this.font.width(tiles[i][0])));
			var lp = gfx.pose();
			lp.pushMatrix();
			try {
				lp.translate(tx + tw / 2f, 108f);
				lp.scale(ls, ls);
				gfx.centeredText(this.font, "§7" + tiles[i][0], 0, 0, 0xFFFFFFFF);
			} finally {
				lp.popMatrix();
			}
			int vc = 0xFF000000 | (i==timeTile ? 0xFFE5EF : n==0 ? 0xAE9CA9 : tiles[i][1].startsWith("-") ? RED : GREEN);
			var pose = gfx.pose();
			pose.pushMatrix();
			try {
				float s = Math.min(1.4f, (tw - 6) / (float) Math.max(1, this.font.width(tiles[i][1])));
				pose.translate(tx + tw / 2f, 119f);
				pose.scale(s, s);
				gfx.centeredText(this.font, tiles[i][1], 0, 0, vc);
			} finally {
				pose.popMatrix();
			}
		}

	}

    private void drawRecordBanner(GuiGraphicsExtractor g){
        int x=panelX1()+8,right=panelX2()-8;
        g.fill(x,73,right,100,0xFF30202B);g.fill(x,73,x+2,100,ROSE);
        g.blit(TROPHY,x+6,77,x+25,96,0,1,0,1);
        g.text(font,"Personal best",x+30,82,ROSE,true);
        long pb=NucleusMod.SPEEDRUN.bestTotalMs;
        String time=pb<0?"No finished run":SpeedrunStore.fmt(pb);
        g.text(font,time,right-7-font.width(time),82,0xFFFFE5EF,true);
    }
    private void drawPersonalBest(GuiGraphicsExtractor g){
        int x=panelX1()+10,right=panelX2()-10;
        g.text(font,"PB splits",x,105,ROSE,true);
        if(pbSplits.isEmpty()){g.centeredText(font,NucleusMod.SPEEDRUN.bestTotalMs<0?"Finish a run to set your personal best.":"No split times saved for this record.",width/2,viewTop()+6,0xFFCCB8C3);return;}
        int y=viewTop()-(int)scroll;
        g.enableScissor(x,viewTop(),right,viewBottom());
        try{for(var split:pbSplits){
            if(y+17>viewTop() && y<viewBottom()){
                g.fill(x,y,right,y+16,0xB1221B23);
                g.fill(x,y,x+2,y+16,0xFF000000|SpeedrunStore.colorOf(split.getKey()));
                g.text(font,NucleusMod.SPEEDRUN.displayName(split.getKey()),x+7,y+4,0xFFE9DAE0,false);
                String time=SpeedrunStore.fmt(split.getValue());g.text(font,time,right-6-font.width(time),y+4,0xFFFFCDD9,false);
            } y+=18;
        }}finally{g.disableScissor();}
    }
    private void drawRow(GuiGraphicsExtractor gfx, SpeedrunStore.RunRecord r, int i, int x1, int y, int x2,
		boolean hover) {
		int y2 = y + ROW_H - 3;
		Long p = profit(r);
		int accent = p == null ? 0x8888AA : p >= 0 ? GREEN : RED;
		gfx.fillGradient(x1, y, x2, y2, hover ? 0xE0303048 : 0xC0202030, hover ? 0xE01A1A2A : 0xC0121220);
		gfx.fill(x1, y, x1 + 2, y2, 0xFF000000 | accent);
		if (hover) {
			gfx.fill(x1, y, x2, y + 1, 0x80000000 | accent);
		}
		int ty = y + (ROW_H - 3 - 8) / 2;
		if(r.totalMs>=0 && r.totalMs==NucleusMod.SPEEDRUN.bestTotalMs){
            gfx.blit(TROPHY,x1+6,ty-3,x1+20,ty+11,0,1,0,1);
            gfx.fill(x1,y,x1+2,y2,ROSE);
        }else gfx.text(this.font, "#" + (i + 1), x1 + 6, ty, 0xFF8888AA, false);
		gfx.text(this.font, SpeedrunStore.fmt(r.totalMs), x1 + 30, ty, 0xFFFFFFFF, true);
		String when = r.finishedAt > 0 ? ago(r.finishedAt) : "";
		gfx.text(this.font, "§8" + when, x1 + 82, ty, 0xFFFFFFFF, false);
		String right = p == null ? "§8no loot seen" : coins(p, true);
		int rw = this.font.width(right);
		gfx.text(this.font, right, x2 - 6 - rw, ty, 0xFF000000 | accent, true);
		if (p != null) {
			String loot = "§8" + r.loot.size() + " loot";
			gfx.text(this.font, loot, x2 - 12 - rw - this.font.width(loot), ty, 0xFFFFFFFF, false);
		}
	}

	private static String ago(long at) {
		long s = Math.max(0, (System.currentTimeMillis() - at) / 1000);
		if (s < 60) {
			return s + "s ago";
		}
		if (s < 3600) {
			return (s / 60) + "m ago";
		}
		if (s < 86400) {
			return (s / 3600) + "h ago";
		}
		return (s / 86400) + "d ago";
	}

	private void drawDetail(GuiGraphicsExtractor gfx, SpeedrunStore.RunRecord r, int cx) {
		Long p = profit(r);
		long value = lootValue(r);
		long cost = r.runCost >= 0 ? r.runCost : DropPrices.runCost();
		gfx.centeredText(this.font, (r.totalMs==NucleusMod.SPEEDRUN.bestTotalMs?"§dPB  ":"§f") + SpeedrunStore.fmt(r.totalMs) + " §8• §7" + r.ids.size() + " splits"
			+ (r.finishedAt > 0 ? " §8• §7" + ago(r.finishedAt) : ""), cx, 79, 0xFFFFFFFF);
		// Profit banner
		String big = p == null ? "NO LOOT SEEN" : coins(p, true) + " PROFIT";
		int bc = p == null ? 0xFF8888AA : p >= 0 ? 0xFF000000 | GREEN : 0xFF000000 | RED;
		var pose = gfx.pose();
		pose.pushMatrix();
		try {
			pose.translate(cx, 94f);
			pose.scale(1.6f, 1.6f);
			gfx.centeredText(this.font, big, 0, 0, bc);
		} finally {
			pose.popMatrix();
		}
		if (p != null) {
			gfx.centeredText(this.font, "§7loot §a" + coins(value, false) + " §8- §7costs §c" + coins(cost, false)
				+ " §8(key + " + (NucleusMod.CONFIG.nucleusUsesApparatus && r.runCost < 0 ? "apparatus" : "robot parts")
				+ ")", cx, 118, 0xFFFFFFFF);
		}
		int x1 = panelX1() + 12;
		int x2 = panelX2() - 12;
		int y = (int) (viewTop() - scroll);
		gfx.enableScissor(panelX1(), viewTop(), panelX2(), viewBottom());
		try {
			NucleusUi.header(gfx, this.font, "SPLITS", cx, y, (x2 - x1) / 2, 0x5AA9FF);
			y += 14;
			for (int i = 0; i < r.ids.size(); i++) {
				String id = r.ids.get(i);
				long cum = i < r.splits.size() ? r.splits.get(i) : -1;
				long seg = i < r.segments.size() ? r.segments.get(i) : -1;
				gfx.fill(x1, y + 2, x1 + 3, y + 7, 0xFF000000 | SpeedrunStore.colorOf(id));
				gfx.text(this.font, NucleusMod.SPEEDRUN.displayName(id), x1 + 8, y, 0xFFE8E8F0, false);
				String t = SpeedrunStore.fmt(cum) + " §8(+" + SpeedrunStore.fmt(seg) + ")";
				gfx.text(this.font, t, x2 - this.font.width(t), y, 0xFFFFFF55, false);
				y += 11;
			}
			y += 6;
			NucleusUi.header(gfx, this.font, "BUNDLE LOOT", cx, y, (x2 - x1) / 2, 0xFFD34A);
			y += 14;
			if (r.loot.isEmpty()) {
				gfx.centeredText(this.font, "§8No bundle seen for this run.", cx, y, 0xFFFFFFFF);
			}
			for (String loot : r.loot) {
				long v = DropPrices.lineValue(loot);
				gfx.text(this.font, "§7• §f" + NucleusUi.ellipsize(this.font, loot, x2 - x1 - 80), x1, y, 0xFFFFFFFF,
					false);
				String vs = v > 0 ? "§6" + coins(v, false) : "§8—";
				gfx.text(this.font, vs, x2 - this.font.width(vs), y, 0xFFFFFFFF, false);
				y += 11;
			}
		} finally {
			gfx.disableScissor();
		}
	}

	@Override public void onClose(){if(selected>=0){selected=-1;scroll=0;rebuildWidgets();}else minecraft.gui.setScreen(parent);}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
