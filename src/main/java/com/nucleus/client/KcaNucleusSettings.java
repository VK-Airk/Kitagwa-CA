package com.nucleus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.InputConstants;

import com.nucleus.NucleusMod;

/**
 * Config GUI for "Kitagawa Codex Addons".
 * Tabbed: "Temple waypoints" first, "More" second. Add more Tab enum
 * values to add more tabs in the future.
 */
public final class KcaNucleusSettings extends Screen {
	public enum Tab {
		FEATURES("Features"),
		WAYPOINTS("Waypoints"),
		MISC("CN Gambling"),
		SOUNDS("Sounds"),
		SPEEDRUNS("Speedruns"),
		ACHIEVEMENTS("Achievements");

		public final String title;

		Tab(String title) {
			this.title = title;
		}
	}

	public static final String MOD_NAME = "Kitagawa Codex Addons";

	private static final int[] PALETTE = {
		0xFF55FFFF, // Aqua
		0xFF55FF55, // Green
		0xFFFFAA00, // Orange
		0xFFFF5555, // Red
		0xFFFFFF55, // Yellow
		0xFFFF55FF, // Pink
		0xFF5555FF, // Blue
		0xFFAA55FF, // Purple
		0xFFFFFFFF, // White
		0xFF00AAAA // Teal
	};

	private static final String[] PALETTE_NAMES = {
		"Aqua", "Green", "Orange", "Red", "Yellow", "Pink", "Blue", "Purple", "White", "Teal"
	};

	private Tab currentTab;

	// Temple tab widgets
	private Button keybindButton;
	private Button color1Button;
	private Button color2Button;
	private Button color3Button;
	private EditBox outlineBox;

	// More tab widgets
	private Button customKeybindButton;
	private Button removeLastKeybindButton;
	private Button customColorButton;
	private EditBox oddsJaderaldBox;
	private EditBox oddsFragmentBox;
	private EditBox oddsMixtureBox;
	private EditBox oddsFlawlessBox;
    private final java.util.Map<String,EditBox> titleFields=new java.util.LinkedHashMap<>();
	private Button divanChestColorButton;
	private EditBox soundIdBox;
	private EditBox customPathBox;

	private boolean listeningForKey = false;
	private boolean listeningForCustomKey = false;
	private boolean listeningForRemoveLastKey = false;
	private boolean listeningForQuickSpinKey = false;
	private Button quickSpinKeyButton;

	private int catBalY = -1;
	private int catMinesY = -1;

	private int catMobsY = -1;

	private int catThemesY = -1;
	private int moreCustomNoteY = -1;
	/** Misc: where the Themes label sits, and each theme button's colours (for its swatch). */
	private int themesLabelY = -1;
	private int soundPacksLabelY = -1;
	private final java.util.Map<Button, NucleusTheme.Theme> themeSwatches = new java.util.IdentityHashMap<>();
	private int catTempleY = -1;
	private int catWaypointsCustomY = -1;
	private int catPlaybackY = -1;
	private int catTriggersY = -1;


	// Scroll state for the long tabs: content widgets keep base Y positions
	// and are shifted by moreScroll. They are drawn zoomed (see contentZoom)
	// inside a scissored viewport, so a row half past the edge is clipped
	// at the edge instead of vanishing. Offsets persist per tab.
	private int moreScroll = 0;
	private int moreMaxScroll = 0;
	private final java.util.Map<AbstractWidget, Integer> moreBaseY = new java.util.HashMap<>();
	/** Scroll offset per section (key = sectionKey()). */
	private final java.util.Map<Integer, Integer> scrollMemory = new java.util.HashMap<>();
	private int achScroll = 0;

	private static boolean scrollable(Tab tab) {
		return tab == Tab.FEATURES || tab == Tab.WAYPOINTS || tab == Tab.MISC || tab == Tab.SOUNDS
			|| tab == Tab.SPEEDRUNS;
	}

    private final Screen parent;
    private int navPage=-1;
    private String pendingSetting;
    public KcaNucleusSettings focusSetting(String label){pendingSetting=label;return this;}

    public static java.util.List<Section> allSections(){return NUCLEUS_SECTIONS;}
    public static Section achievements(){return sections(Tab.ACHIEVEMENTS).getFirst();}
    public KcaNucleusSettings(Section section, Screen parent) {
        super(Component.literal(section.title()));
        currentTab=section.tab();
        currentSection=section.tab()==Tab.WAYPOINTS && section.index()==2 ? 0 : section.index();
        this.parent=parent;
        NucleusUi.onTitleScreenOpened();
        Achievements.onMenuOpened();
    }

    public static java.util.List<Section> sections(Tab tab) {
        return java.util.Arrays.stream(SECTIONS).filter(section->section.tab()==tab).toList();
    }

	/** Small label above an odds box (gambling tab). */
	private void oddsLabel(GuiGraphicsExtractor gfx, EditBox box, String text) {
		if (box != null && box.visible) {
			gfx.text(this.font, text, box.getX(), box.getY() - 12, 0xFFFFFFFF, true);
		}
	}

	/**
	 * Hover hints, drawn by the screen itself (see drawTips) rather than
	 * vanilla's widget tooltips, which would land in the wrong place for
	 * zoomed content.
	 */
	private final java.util.Map<AbstractWidget, Component> tips = new java.util.IdentityHashMap<>();

	/** Short hover hint for a config widget. */
	private void tip(AbstractWidget w, String text) {
		tip(w, Component.literal(text));
	}

	private void tip(AbstractWidget w, Component text) {
		if (w != null && text != null) {
			tips.put(w, text);
		}
	}

	private static Component soundToggleLabel() {
		boolean on = NucleusMod.CONFIG.objectiveSoundOn;
		return Component.literal("Play sound on objective completion: ")
			.append(toggleLabel(on));
	}

	static String colorName(int argb) {
		for (int i = 0; i < PALETTE.length; i++) {
			if (PALETTE[i] == argb) {
				return PALETTE_NAMES[i];
			}
		}
		return "Custom";
	}

	static int nextColor(int current) {
		for (int i = 0; i < PALETTE.length; i++) {
			if (PALETTE[i] == current) {
				return PALETTE[(i + 1) % PALETTE.length];
			}
		}
		return PALETTE[0];
	}

	private static Component colorButtonLabel(int index, int color) {
		String name = colorName(color);
		int rgb = color & 0xFFFFFF;
		return Component.literal("Waypoint " + index + ": ")
			.append(Component.literal(name).withColor(rgb));
	}

	private static Component customColorLabel(int color) {
		String name = colorName(color);
		int rgb = color & 0xFFFFFF;
		return Component.literal("Custom color: ")
			.append(Component.literal(name).withColor(rgb));
	}

	private void refreshColorButtons() {
		if (color1Button != null) {
			color1Button.setMessage(colorButtonLabel(1, NucleusMod.CONFIG.waypoint1Color));
		}
		if (color2Button != null) {
			color2Button.setMessage(colorButtonLabel(2, NucleusMod.CONFIG.waypoint2Color));
		}
		if (color3Button != null) {
			color3Button.setMessage(colorButtonLabel(3, NucleusMod.CONFIG.waypoint3Color));
		}
		if (customColorButton != null) {
			customColorButton.setMessage(customColorLabel(NucleusMod.CONFIG.customWaypointColor));
		}
		if (divanChestColorButton != null) {
			divanChestColorButton.setMessage(divanChestColorLabel(NucleusMod.CONFIG.divanChestColor));
		}
	}

	private void cycleColor(int index) {
		if (index == 1) {
			NucleusMod.CONFIG.waypoint1Color = nextColor(NucleusMod.CONFIG.waypoint1Color);
		} else if (index == 2) {
			NucleusMod.CONFIG.waypoint2Color = nextColor(NucleusMod.CONFIG.waypoint2Color);
		} else {
			NucleusMod.CONFIG.waypoint3Color = nextColor(NucleusMod.CONFIG.waypoint3Color);
		}
		NucleusMod.CONFIG.save();
		refreshColorButtons();
	}

	private void cycleCustomColor() {
		NucleusMod.CONFIG.customWaypointColor = nextColor(NucleusMod.CONFIG.customWaypointColor);
		NucleusMod.CONFIG.save();
		refreshColorButtons();
	}

	private static Component divanChestColorLabel(int color) {
		String name = colorName(color);
		int rgb = color & 0xFFFFFF;
		return Component.literal("Chest color: ")
			.append(Component.literal(name).withColor(rgb));
	}

	private void cycleDivanChestColor() {
		NucleusMod.CONFIG.divanChestColor = nextColor(NucleusMod.CONFIG.divanChestColor);
		NucleusMod.CONFIG.save();
		refreshColorButtons();
	}

	private static Component trackRateLabel() {
		return Component.literal("Track in: "
			+ (NucleusMod.CONFIG.scavengerSetsPerHour ? "sets of tools" : "tools"));
	}

	// ------------------------------------------------------------ sections

	/** One sidebar entry: a section of a tab (tabs with one section map 1:1). */
	public record Section(Tab tab, int index, String title, String desc, net.minecraft.world.item.Item icon) {
	}

	/** Sidebar order. Indexes follow the section headers' order inside each tab builder. */
	private static final Section[] SECTIONS = {
		new Section(Tab.FEATURES, 0, "Tool Finder", "Metal Detector chest locator and scavenged tool tracker.",
			net.minecraft.world.item.Items.GOLDEN_PICKAXE),
		new Section(Tab.FEATURES, 1, "NPC highlights", "Highlight NPCs and clear markers.",
			net.minecraft.world.item.Items.SPYGLASS),
		new Section(Tab.FEATURES, 2, "HUD & alerts", "Bal timer, lobby day, Terror stacks and Hollows alerts.",
			net.minecraft.world.item.Items.MAGMA_CREAM),
		new Section(Tab.WAYPOINTS, 0, "Jungle Temple", "Preset temple waypoints and their look.",
			net.minecraft.world.item.Items.COMPASS),
		new Section(Tab.WAYPOINTS, 1, "Custom waypoints", "Your own per-lobby waypoints.",
			net.minecraft.world.item.Items.MAP),
		new Section(Tab.WAYPOINTS, 2, "Waypoints", "Jungle Temple and custom waypoint settings.", net.minecraft.world.item.Items.COMPASS),
		new Section(Tab.MISC, 0, "CN Gambling", "Wheel, vaults and briefcase slots, reward endings and previews.",
			net.minecraft.world.item.Items.GOLD_INGOT),
		new Section(Tab.MISC, 2, "Titles", "Edit the headings, luck verdicts and reward banners.", net.minecraft.world.item.Items.NAME_TAG),
		new Section(Tab.MISC, 3, "Gambling guide", "How to preview drops or add a game and ending in the source.", net.minecraft.world.item.Items.BOOK),
		new Section(Tab.MISC, 4, "Timing", "Opening sequence, game and reveal speeds, and quick-spin hotkey.", net.minecraft.world.item.Items.CLOCK),
        new Section(Tab.MISC, 5, "Legacy wheel", "Pointer, sparkles and landing zoom.", net.minecraft.world.item.Items.CLOCK),
        new Section(Tab.MISC, 6, "Vaults", "Key paths, crystal trails, lock sounds and accents.", net.minecraft.world.item.Items.TRIAL_KEY),
        new Section(Tab.MISC, 9, "Random", "Choose the games in the random rotation.", net.minecraft.world.item.Items.CHEST),
        new Section(Tab.MISC, 8, "Drops", "Choose an animation for each drop, set odds or animate every bundle.", net.minecraft.world.item.Items.BUNDLE),
		new Section(Tab.SOUNDS, 0, "Sounds", "Mixer, triggers and gambling sound packs.",
			net.minecraft.world.item.Items.JUKEBOX),
		new Section(Tab.SPEEDRUNS, 0, "Speedruns", "Nucleus run timer, safe mode, splits and run history.",
			net.minecraft.world.item.Items.CLOCK),
		new Section(Tab.ACHIEVEMENTS, 0, "Achievements", "Every achievement and your progress.",
			net.minecraft.world.item.Items.NETHER_STAR),
		new Section(Tab.FEATURES, 4, "Crystals", "Crystal HUD layout and display order.", net.minecraft.world.item.Items.AMETHYST_SHARD),
        new Section(Tab.FEATURES, 3, "Customization", "Menu themes and seasonal effects.",
			net.minecraft.world.item.Items.PAINTING),
	};
	private static final java.util.List<Section> NUCLEUS_SECTIONS=java.util.Arrays.stream(SECTIONS).filter(s->s.tab()!=Tab.ACHIEVEMENTS).toList();
    private static final java.util.List<Section> SIDEBAR=NUCLEUS_SECTIONS.stream().filter(s->switch(s.tab()) {
        case WAYPOINTS -> s.index()==2;
        case MISC -> s.index()==0;
        case FEATURES -> s.index()!=1 && s.index()!=4;
        default -> true;
    }).toList();
    private int currentSection = 0;
	/** Base-Y range of the shown section inside its tab, and how far it was lifted to the top. */
	private int sectionStart = Integer.MIN_VALUE;
	private int sectionEnd = Integer.MAX_VALUE;
	private int sectionShift = 0;

	private Section currentSectionInfo() {
		for (Section sec : SECTIONS) {
			if (sec.tab() == currentTab && sec.index() == currentSection) {
				return sec;
			}
		}
		for (Section sec : SECTIONS) {
			if (sec.tab() == currentTab) {
				return sec;
			}
		}
		return SECTIONS[0];
	}

	private int sectionKey() {
		return currentTab.ordinal() * 32 + currentSection;
	}

	/** Section header Ys of a tab, in sidebar order (filled while the tab builds). */
	private int[] sectionHeaders(Tab tab) {
		return switch (tab) {
			case FEATURES -> currentSection == 4 ? new int[0] : new int[] { catMinesY, catMobsY, catBalY, catThemesY };
			case WAYPOINTS -> currentSection==2?new int[0]:new int[] { catTempleY, catWaypointsCustomY };
			case SOUNDS -> new int[] { catPlaybackY };
			default -> new int[0];
		};
	}

	private static final int CONTENT_PANEL_TOP = 46;
	/** Room the centred content needs (widgets span cx-155..cx+155). */

	private int contentX1 = 0;
	private int contentX2 = 0;

	/** Sidebar when there is room for it next to the content, else a top tab row. */
	private int navLeft(){return Math.max(5,(width-590)/2);}
    private int navWidth(){return Math.clamp(width/4,78,119);}
    private void layout() {
		contentX1 = currentTab==Tab.ACHIEVEMENTS?navLeft():navLeft()+navWidth()+6;
        contentX2 = width-navLeft();
		contentZoom = pickZoom();
	}

	// ------------------------------------------------------------ content zoom

	/** Top of the scrolling content (screen and virtual space share it). */
	private boolean hasSubtabs(){return currentTab==Tab.WAYPOINTS || currentTab==Tab.SPEEDRUNS || currentTab==Tab.MISC || currentTab==Tab.FEATURES && (currentSection==1 || currentSection==2 || currentSection==4);}
    private int contentTop(){return currentTab==Tab.MISC && (currentSection>=1 && currentSection<=3 || currentSection>=5 && currentSection<=6)?94:hasSubtabs()?72:52;}
	/** Width the content column needs in virtual space (widgets span cx-155..cx+155). */
	private static final int ZOOM_NEED_W = 322;
	/** Virtual viewport height the zoom must leave (keeps a few rows on screen). */
	private static final int ZOOM_NEED_H = 150;
	/**
	 * Scale for the scrolling tabs' content. Widgets are laid out at
	 * the normal size and drawn magnified about (contentCx, contentTop()),
	 * so the panel stays the same while its contents fill it more: a
	 * gentle 1.25x, only when the panel has room for it.
	 */
	private float contentZoom = 1f;
	private static final float ZOOM = 1.25f;

	private float pickZoom() {
		int contentW = contentX2 - contentX1;
		float max = Math.min((contentW - 8) / (float) ZOOM_NEED_W,
			(this.height - 48 - contentTop()) / (float) ZOOM_NEED_H);
		float z = Math.min(ZOOM, max);
		return Math.max(0.5f,z);
	}

	private boolean zoomed() {
		return scrollable(currentTab);
	}

	private float zoomNow() {
		return zoomed() ? contentZoom : 1f;
	}

	private double toVirtualX(double sx) {
		int ox = contentCx();
		return ox + (sx - ox) / zoomNow();
	}

	private double toVirtualY(double sy) {
		return contentTop() + (sy - contentTop()) / zoomNow();
	}

	/** Screen-space bottom of the scrolling viewport. */
	private int screenViewportBottom() {
		return this.height - 48;
	}

	private boolean inViewport(double sx, double sy) {
		return sx >= contentX1 && sx < contentX2 && sy >= contentTop() && sy < screenViewportBottom();
	}

	private boolean isContent(Object o) {
		return o instanceof AbstractWidget w && moreBaseY.containsKey(w);
	}

	private net.minecraft.client.input.MouseButtonEvent toVirtual(net.minecraft.client.input.MouseButtonEvent e) {
		return new net.minecraft.client.input.MouseButtonEvent(toVirtualX(e.x()), toVirtualY(e.y()), e.buttonInfo());
	}

	/** Runs {@code body} with the zoomed content hidden from vanilla's screen-space handling. */
	private <T> T withContentHidden(java.util.function.Supplier<T> body) {
		if (!zoomed() || moreBaseY.isEmpty()) {
			return body.get();
		}
		java.util.Map<AbstractWidget, Boolean> was = new java.util.IdentityHashMap<>();
		for (AbstractWidget w : moreBaseY.keySet()) {
			was.put(w, w.visible);
			w.visible = false;
		}
		try {
			return body.get();
		} finally {
			for (var e : was.entrySet()) {
				e.getKey().visible = e.getValue();
			}
		}
	}

	private static final String TITLE_TEXT = "KCA";
	private static final float TITLE_Y = 8f;

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
		if (event.button() == 0) {
			int[] tb = new int[] {width/2-20,6,width/2+20,25};
			if (event.x() >= tb[0] && event.x() < tb[2] && event.y() >= tb[1] && event.y() < tb[3]) {
				int result = NucleusUi.titleClicked();
				if (result >= 0) {
					Achievements.onLogoClick(result == 1);
				}
				return true;
			}
		}
		if (currentTab == Tab.ACHIEVEMENTS && event.button() == 0 && achClick(event.x(), event.y())) {
			return true;
		}
		if (zoomed() && inViewport(event.x(), event.y())) {
			var ve = toVirtual(event);
			for (var child : this.children()) {
				if (isContent(child) && child.isMouseOver(ve.x(), ve.y())) {
					if (child.mouseClicked(ve, doubled)) {
						if (child.shouldTakeFocusAfterInteraction()) {
							setFocused(child);
						}
						if (event.button() == 0) {
							setDragging(true);
						}
						return true;
					}
					break;
				}
			}
		}
		return withContentHidden(() -> super.mouseClicked(event, doubled));
	}

	@Override
	public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
		var focused = getFocused();
		if (zoomed() && isContent(focused)) {
			if (event.button() == 0) {
				setDragging(false);
			}
			return focused.mouseReleased(toVirtual(event));
		}
		return withContentHidden(() -> super.mouseReleased(event));
	}

	@Override
	public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
		var focused = getFocused();
		if (zoomed() && isContent(focused)) {
			if (isDragging() && event.button() == 0) {
				float z = zoomNow();
				return focused.mouseDragged(toVirtual(event), dx / z, dy / z);
			}
			return false;
		}
		return withContentHidden(() -> super.mouseDragged(event, dx, dy));
	}

	/** Centre of the content panel (everything on a tab is laid out around it). */
	private int contentCx() {
		if (contentX2 <= contentX1) {
			layout();
		}
		return (contentX1 + contentX2) / 2;
	}

	private static int tabAccent(Tab t) {
		return (t==Tab.ACHIEVEMENTS?dev.purplepad.KcaTheme.ROSE:NucleusMod.CONFIG.nucleusAccent) & 0xFFFFFF;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		dev.purplepad.KcaTheme.backdrop(gfx,width,height,currentTab==Tab.ACHIEVEMENTS?dev.purplepad.KcaTheme.Branch.GENERAL:dev.purplepad.KcaTheme.Branch.NUCLEUS);
		if(currentTab!=Tab.ACHIEVEMENTS)dev.purplepad.KcaTheme.panel(gfx,navLeft(),CONTENT_PANEL_TOP,navWidth(),this.height-46-CONTENT_PANEL_TOP);
        dev.purplepad.KcaTheme.panel(gfx,contentX1,CONTENT_PANEL_TOP,contentX2-contentX1,this.height-46-CONTENT_PANEL_TOP);
	}

	@Override
	protected void init() {
        cachedAchievements=null;
		// Clear per-tab references so stale widgets can't be touched after a tab switch.
		keybindButton = null;
		outlineBox = null;
		color1Button = null;
		color2Button = null;
		color3Button = null;
		customKeybindButton = null;
		removeLastKeybindButton = null;
		quickSpinKeyButton = null;
		listeningForQuickSpinKey = false;
		customColorButton = null;
		divanChestColorButton = null;
		oddsJaderaldBox = null;
		oddsFragmentBox = null;
		oddsMixtureBox = null;
		oddsFlawlessBox = null;
		soundIdBox = null;
		customPathBox = null;
		catBalY = -1;
		catMinesY = -1;

		catMobsY = -1;

		catThemesY = -1;
		moreCustomNoteY = -1;
		themesLabelY = -1;
		soundPacksLabelY = -1;
		catTempleY = -1;
		catWaypointsCustomY = -1;
		catPlaybackY = -1;
		catTriggersY = -1;

		moreBaseY.clear();
        titleFields.clear();
		tips.clear();

		layout();
		int cx = contentCx();

		int contentY = contentTop();
		if (currentTab == Tab.FEATURES) {
			if (currentSection == 4) buildCrystalHud(cx, contentY);
            else buildFeaturesTab(cx, contentY);
		} else if (currentTab == Tab.WAYPOINTS) {
			buildWaypointsTab(cx, contentY);
		} else if (currentTab == Tab.MISC) {
			if(currentSection==2) buildGambleTitles(cx,contentY);
			else if(currentSection==3) buildGambleGuide(cx,contentY);
			else if(currentSection==4) buildGambleTiming(cx,contentY);
			else if(currentSection==9) buildShuffle(cx,contentY);
            else if(currentSection==8) buildDropRules(cx,contentY);
            else if(currentSection>=5)buildGameStyle(cx,contentY);
			else buildMiscTab(cx, contentY);
		} else if (currentTab == Tab.SOUNDS) {
			buildSoundsTab(cx, contentY);
		} else if (currentTab == Tab.SPEEDRUNS) {
			buildSpeedrunTab(cx, contentY);
		} else if (currentTab == Tab.ACHIEVEMENTS) {
			buildAchievementsTab(cx, contentY);
		} else {
			buildWaypointsTab(cx, contentY);
		}
		if(currentTab!=Tab.ACHIEVEMENTS)addNavigation();
        if(hasSubtabs())addSubtabs();
        // Sliders wear the tab's color, like the rest of the page.
		int accent = tabAccent(currentTab);
		for (var child : children()) {
			if(child instanceof NucleusUi.NToggle toggle)toggle.accent=accent;
            if(child instanceof NucleusUi.NButton button)button.accent=accent;
            if (child instanceof IntSlider slider) {
				slider.accent = accent;
			}
		}
	}

    private Section sidebarSection(){
        if(currentTab==Tab.WAYPOINTS)return sections(Tab.WAYPOINTS).get(2);
        if(currentTab==Tab.MISC)return sections(Tab.MISC).getFirst();
        if(currentTab==Tab.FEATURES && hasSubtabs())return sections(Tab.FEATURES).get(2);
        return currentSectionInfo();
    }
    private void selectSection(Tab tab,int section){
        if(currentTab==tab && currentSection==section)return;
        saveFields();scrollMemory.put(sectionKey(),moreScroll);
        currentTab=tab;currentSection=tab==Tab.WAYPOINTS && section==2 ? 0 : section;rebuildWidgets();
    }
    private void addSubtabs(){
        if(currentTab==Tab.SPEEDRUNS){
            RunHistoryScreen.tabs(this::addRenderableWidget,contentCx(),contentX2-contentX1-8,0,i->{
                if(i>0){saveFields();minecraft.gui.setScreen(new RunHistoryScreen(this,i==1));}
            });return;
        }
        if(currentTab==Tab.MISC){
            int cell=Math.min(75,(contentX2-contentX1-28)/6), step=cell+4;
            int left=contentCx()-(6*cell+20)/2;
            boolean styles=currentSection>=5 && currentSection<=6, extras=currentSection>=1 && currentSection<=3;
            subtab("Animation",0,left,cell);
            addRenderableWidget(dev.purplepad.KcaTheme.tab("Style",left+step,49,cell,styles,tabAccent(currentTab),()->selectSection(Tab.MISC,6)));
            subtab("Drops",8,left+2*step,cell);
            subtab("Timing",4,left+3*step,cell);
            addRenderableWidget(dev.purplepad.KcaTheme.tab("More",left+4*step,49,cell,extras,tabAccent(currentTab),()->selectSection(Tab.MISC,2)));
            subtab("Random",9,left+5*step,cell);
            if(styles || extras){
                int count=2;
                int subW=Math.min(94,(contentX2-contentX1-18)/count),x=contentCx()-(count*subW+(count-1)*4)/2;
                for(int id:styles?new int[]{5,6}:new int[]{2,3}){
                    var info=sections(Tab.MISC).stream().filter(v->v.index()==id).findFirst().orElseThrow();
                    addRenderableWidget(dev.purplepad.KcaTheme.tab(id==3?"Guide":info.title(),x,71,subW,currentSection==id,tabAccent(currentTab),()->selectSection(Tab.MISC,id)));
                    x+=subW+4;
                }
            }
            return;
        }
        int w=Math.min(140,(contentX2-contentX1-10)/2),x=contentCx()-w-2;
        if(currentTab==Tab.WAYPOINTS){subtab("Jungle Temple",0,x,w);subtab("Custom waypoints",1,x+w+4,w);return;}
        int cell = Math.min(100, (contentX2-contentX1-16)/3);
        int left = contentCx() - (3*cell+8)/2;
        subtab("Alerts",2,left,cell);
        subtab("NPC highlights",1,left+cell+4,cell);
        subtab("Crystals",4,left+2*(cell+4),cell);
    }
    private void subtab(String label,int section,int x,int w){
        addRenderableWidget(dev.purplepad.KcaTheme.tab(label,x,49,w,currentSection==section,tabAccent(currentTab),()->selectSection(currentTab,section)));
    }
    private String breadcrumb(){
        if(currentTab==Tab.MISC)return "Nucleus / CN Gambling / "+(currentSection==0?"Animation":currentSectionInfo().title());
        if(currentTab==Tab.WAYPOINTS)return "Nucleus / Waypoints / "+currentSectionInfo().title();
        if(currentTab==Tab.SPEEDRUNS)return "Nucleus / Speedruns / Timer";
        if(hasSubtabs())return "Nucleus / HUD & alerts / "+(currentSection==2?"Alerts":currentSectionInfo().title());
        return (currentTab==Tab.ACHIEVEMENTS?"General / ":"Nucleus / ")+currentSectionInfo().title();
    }
    private void addNavigation(){
        int rows=Math.clamp((height-122)/18,1,SIDEBAR.size());
        int pages=(SIDEBAR.size()+rows-1)/rows;
        int selected=SIDEBAR.indexOf(sidebarSection());
        if(navPage<0)navPage=selected/rows;
        navPage=Math.min(navPage,pages-1);
        for(int r=0;r<rows && navPage*rows+r<SIDEBAR.size();r++){
            Section section=SIDEBAR.get(navPage*rows+r);
            var button=NucleusUi.NButton.create(Component.literal(section.title()),b->selectSection(section.tab(),section.index())).pos(navLeft()+2,49+r*18).size(navWidth()-4,17).build();
            button.selected(section==sidebarSection());tip(button,section.desc());addRenderableWidget(button);
        }
        if(pages>1){
            int half=(navWidth()-6)/2;
            var prev=addRenderableWidget(NucleusUi.NButton.create(Component.literal("<"),b->{saveFields();navPage--;rebuildWidgets();}).pos(navLeft()+2,height-68).size(half,18).build());
            var next=addRenderableWidget(NucleusUi.NButton.create(Component.literal(">"),b->{saveFields();navPage++;rebuildWidgets();}).pos(navLeft()+4+half,height-68).size(half,18).build());
            prev.active=navPage>0;next.active=navPage+1<pages;
        }
    }
    /** Bottom row on every tab: unified mover next to Done. */
	public static void openSheet(net.minecraft.client.gui.screens.Screen parent){
        net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(parent,"https://docs.google.com/spreadsheets/d/1sTm-t2bRrebxKQRq77jafBvu_YcuQsNqZ8-HZFVuqCs/edit?gid=0#gid=0");
    }

	private void addDoneRow(int cx, int y) {
        boolean gui = java.util.Set.of("Tool Finder","HUD & alerts","Speedruns").contains(currentSectionInfo().title());
        boolean achievements=currentTab==Tab.ACHIEVEMENTS;
        int count=gui&&!achievements?3:2;
        int cell=Math.min(135,(width-20)/count-4), x=width/2-(count*(cell+4)-4)/2;
        if(achievements){
            addRenderableWidget(NucleusUi.NButton.create(Component.literal("Reset achievements"),b->ConfirmResetScreen.open(this,ConfirmResetScreen.Mode.ACHIEVEMENTS)).pos(x,y).size(cell,20).build());
        }else{
            if(gui){
                addRenderableWidget(NucleusUi.NButton.create(Component.literal("GUI settings"),b->{saveFields();minecraft.gui.setScreen(new dev.purplepad.GuiSettingsScreen(this,dev.purplepad.GuiSettingsScreen.Element.NUCLEUS));}).pos(x,y).size(cell,20).build());
                x+=cell+4;
            }
            addRenderableWidget(NucleusUi.NButton.create(Component.literal("Spreadsheet"),b->{saveFields();openSheet(this);}).pos(x,y).size(cell,20).build());
        }
        addRenderableWidget(NucleusUi.NButton.create(Component.literal("Back"),b->saveAndClose()).pos(x+cell+4,y).size(cell,20).build());
	}

	private void buildAchievementsTab(int cx, int startY) {
        dev.purplepad.KcaMenu.generalTabs(this::addRenderableWidget,cx,contentX2-contentX1,2,i->{if(i!=2)dev.purplepad.KcaMenu.generalTabFrom(parent,i);});
		// Filter chips, section headers and cards are drawn in
		// extractRenderState (scissored viewport) and hit-tested in
		// mouseClicked off the same layout, so they can never drift apart.
		addDoneRow(cx, this.height - 40);
	}

	/** -1 = All, -2 = Unlocked, -3 = Locked, else a tier index. */
	private static int achFilter = -1;
    private static int achBranch;
    private static final String[] ACH_BRANCHES={"All achievements","Dungeons","Nucleus","General"};

	/** Split row: rename box (25 chars) + ON/OFF toggle + optional move arrows. */
	private int addSplitRow(int cx, int y, String splitId, boolean movable) {
		final String id = splitId;
		EditBox nameBox = new NucleusUi.NEditBox(this.font, cx - 150, y, 168, 18,
			Component.literal("Split name"));
		nameBox.setMaxLength(25);
		nameBox.setValue(NucleusMod.SPEEDRUN.displayName(id));
		nameBox.setResponder(text -> NucleusMod.SPEEDRUN.setSplitName(id, text));
		nameBox.setTextColor(com.nucleus.SpeedrunStore.colorOf(id) | 0xFF000000);
		tip(nameBox, "Rename this split.");
		addRenderableWidget(nameBox);
        Button toggleButton=NucleusUi.NButton.create(toggleLabel(NucleusMod.SPEEDRUN.isEnabled(id)),btn->{
            boolean next=!NucleusMod.SPEEDRUN.isEnabled(id);
            NucleusMod.SPEEDRUN.setEnabled(id,next);btn.setMessage(toggleLabel(next));
        }).pos(cx+22,y).size(52,20).build();
        tip(toggleButton,id.equals("place")?"Off ends the timer at your last enabled checkpoint. With all splits off, five placements finish the timer.":"Include this checkpoint in new runs.");
		addRenderableWidget(toggleButton);
		if (movable) {
			Button upButton = NucleusUi.NButton.create(Component.literal("▲"), btn -> {
				NucleusMod.SPEEDRUN.moveUp(id);
				rebuildWidgets();
			}).pos(cx + 78, y).size(24, 20).build();
			tip(upButton, "Move split up.");
			addRenderableWidget(upButton);
			Button downButton = NucleusUi.NButton.create(Component.literal("▼"), btn -> {
				NucleusMod.SPEEDRUN.moveDown(id);
				rebuildWidgets();
			}).pos(cx + 106, y).size(24, 20).build();
			tip(downButton, "Move split down.");
			addRenderableWidget(downButton);
		}
		return y + 22;
	}

	private static Component toggleLabel(boolean on) {
		return on ? Component.literal("ON").withColor(0x55FF55)
			: Component.literal("OFF").withColor(0xFF5555);
	}

	private int runCountField(int cx,int y,String label,java.util.function.LongSupplier current,long minimum,java.util.function.LongConsumer apply){
        var box=new NucleusUi.NEditBox(font,cx-150,y,142,18,Component.literal(label));
        box.setMaxLength(20);
        box.setValue(Long.toString(current.getAsLong()));
        tip(box,"Enter a number to set the count, or +50 to add 50 to it.");
        var button=NucleusUi.NButton.create(Component.literal(label),b->{
            long value=com.nucleus.SpeedrunStore.editedRunCount(box.getValue(),current.getAsLong(),minimum);
            if(value<0)return;
            apply.accept(value);NucleusMod.SPEEDRUN.save();rebuildWidgets();
        }).pos(cx-4,y).size(154,18).build();
        box.setResponder(text->button.active=com.nucleus.SpeedrunStore.editedRunCount(text,current.getAsLong(),minimum)>=0);
        addRenderableWidget(box);addRenderableWidget(button);return y+22;
    }
    private void downtimeTextField(int x, int y, String label, String value, String fallback,
                                   java.util.function.Consumer<String> change) {
        var caption = new net.minecraft.client.gui.components.MultiLineTextWidget(x,y,Component.literal(label),font);
        addRenderableWidget(caption);
        var box = new NucleusUi.NEditBox(font,x,y+12,146,18,Component.literal(label));
        box.setMaxLength(64);
        box.setValue(value);
        box.setHint(Component.literal(fallback));
        box.setResponder(text -> change.accept(text.isBlank() ? fallback : text.strip()));
        tip(box,"Clear this field to restore “" + fallback + "”.");
        addRenderableWidget(box);
    }

    private void buildSpeedrunTab(int cx, int startY) {
		int y = startY;
		int rowH = 22;

		NucleusUi.NToggle timerBox = NucleusUi.NToggle.builder(Component.literal("Speedrun timer"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.speedrunEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.speedrunEnabled = val;
				if (!val) {
					SpeedrunManager.onLeave();
				}
				NucleusMod.CONFIG.save();
			}).build();
		tip(timerBox, "Time Nucleus runs using your chosen checkpoints.");
		addRenderableWidget(timerBox);
		y += rowH;

        var store=NucleusMod.SPEEDRUN;
        y=option(cx,y,"Run goal HUD",NucleusMod.CONFIG.showRunGoal,v->NucleusMod.CONFIG.showRunGoal=v,"Show your lifetime Nucleus run total against a target in the Crystal Hollows.");
        y=runCountField(cx,y,"Set run goal",()->store.runGoal,1,n->store.runGoal=n);
        y=guideLine(cx,y,"/kca runs scan checks your active SkyBlock profile and updates the total on your HUD.",false);
        y=runCountField(cx,y,"Set manual total",store::displayedRuns,0,n->store.manualRunTotal=n);
        var sync=NucleusUi.NButton.create(Component.literal("Use tracked total ("+store.completedRuns+")"),b->{
            store.manualRunTotal=-1;store.save();rebuildWidgets();
        }).pos(cx-150,y).size(300,18).build();
        sync.active=store.manualRunTotal>=0;addRenderableWidget(sync);y+=22;
        y=guideLine(cx,y,store.manualRunTotal>=0?"Manual total is active. Each completed run adds one. Use tracked total to return to the synced count.":"Using your tracked total. Set manual total to start counting from a number of your choice.",false);
        var distance=new IntSlider(cx-150,y,300,"Start distance",0,64,NucleusMod.CONFIG.speedrunStartOffset," blocks",v->NucleusMod.CONFIG.speedrunStartOffset=v);
        tip(distance,"Extra blocks beyond the Nucleus start area before the timer starts. Zero uses the original boundary.");
        addRenderableWidget(distance);y+=rowH;
		NucleusUi.NToggle lobbySummaryBox = NucleusUi.NToggle.builder(Component.literal("Show summary after lobby leave"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.lobbySummary)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.lobbySummary = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(lobbySummaryBox, "Prints average, fastest and runs/hour\nwhen leaving a lobby with 2+ runs.\nCheck anytime with /kca nucleus lobbyStats.");
		addRenderableWidget(lobbySummaryBox);
		y += rowH;

		// --- Safe mode ---

		NucleusUi.NToggle safeBox = NucleusUi.NToggle.builder(Component.literal("Safe mode"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.safeMode)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.safeMode = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(safeBox, "Blocks every /warp except /warp nucleus|cn.\nToggle with /kca nucleus safe.");
		addRenderableWidget(safeBox);
		y += rowH;

		NucleusUi.NToggle autoSafeBox = NucleusUi.NToggle.builder(Component.literal("Auto safe mode"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.autoSafeMode)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.autoSafeMode = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(autoSafeBox, "Turns safe mode on automatically after N\nfinished runs in the same lobby.");
		addRenderableWidget(autoSafeBox);
		y += rowH;

		IntSlider safeRunsSlider = new IntSlider(cx - 150, y, 300, "Runs in lobby",
			1, 20, NucleusMod.CONFIG.autoSafeModeRuns, v -> NucleusMod.CONFIG.autoSafeModeRuns = v);
		tip(safeRunsSlider, "Finished runs in one lobby before safe\nmode switches itself on.");
		addRenderableWidget(safeRunsSlider);
		y += rowH;

        var pauseRun = NucleusUi.NButton.create(Component.literal(SpeedrunManager.manuallyPaused() ? "Resume run" : "Downtime"), btn -> {
            KcaChat.chat(Minecraft.getInstance(),Component.literal(SpeedrunManager.toggleDowntime()));
            rebuildWidgets();
        }).pos(cx-150,y).size(300,18).build();
        pauseRun.active = SpeedrunManager.state() == SpeedrunManager.State.RUNNING;
        tip(pauseRun,"Pause the run clock, checkpoints and downtime warning. Click again to resume.");
        addRenderableWidget(pauseRun);
        y+=rowH;
        y=option(cx,y,"Downtime alert",NucleusMod.CONFIG.downtimeAlert,v->NucleusMod.CONFIG.downtimeAlert=v,
            "Warn once when the run is 10 seconds over the average of your last 50 completed timed runs.");
        var cfg = NucleusMod.CONFIG;
        downtimeTextField(cx-150,y,"Warning headline",cfg.downtimeHeadline,com.nucleus.NucleusConfig.DEFAULT_DOWNTIME_HEADLINE,
            text -> cfg.downtimeHeadline=text);
        downtimeTextField(cx+4,y,"Small subtitle",cfg.downtimeSubtitle,com.nucleus.NucleusConfig.DEFAULT_DOWNTIME_SUBTITLE,
            text -> cfg.downtimeSubtitle=text);
        y+=36;
        y=option(cx,y,"Run counter",NucleusMod.CONFIG.showRunCounter,v->NucleusMod.CONFIG.showRunCounter=v,"Show completed runs this lobby and your saved total in the timer HUD.");
        y=guideLine(cx,y,"Open your Crystal Hollows inventory to start, pause or stop at the tracker, skip a split, or edit your total. Use /kca pause or /kca resume mid-run. Stop discards the unfinished attempt. Use /kca hud to move the timer and crystals separately. Set checkpoints for your next run below. To skip a stuck checkpoint mid-run, use Skip split, /kca nucleus skip, or bind Skip Nucleus split in Minecraft Controls.",false);
		for (String id : new java.util.ArrayList<>(NucleusMod.SPEEDRUN.order)) {
			y = addSplitRow(cx, y, id, true);
		}

		// Placement stays last when enabled.
		y = addSplitRow(cx, y, "place", false);

		Button skipButton = NucleusUi.NButton.create(Component.literal("Skip split"), btn -> SpeedrunManager.skipSplit())
			.pos(cx - 150, y).size(145, 20).build();
		tip(skipButton, "Skip the current split.");
		addRenderableWidget(skipButton);
		Button resetRunButton = NucleusUi.NButton.create(Component.literal("Reset run"), btn -> {
			SpeedrunManager.resetRun();
			rebuildWidgets();
		}).pos(cx + 5, y).size(145, 20).build();
		tip(resetRunButton, "Cancel the current run.");
		addRenderableWidget(resetRunButton);
		y += rowH;

		finishScrollable(Tab.SPEEDRUNS, cx, y);
	}

	private void buildWaypointsTab(int cx, int startY) {
		int y = startY;
		int rowH = 22;

		// --- Jungle Temple ---
		catTempleY = y;
		y += 14;

		boolean auto = NucleusMod.CONFIG.templeAutoPlace;
		Button modeButton = NucleusUi.NButton.create(
			Component.literal("Waypoint mode: " + (auto ? "Automatic" : "Manual")), btn -> {
			boolean next = !NucleusMod.CONFIG.templeAutoPlace;
			NucleusMod.CONFIG.templeAutoPlace = next;
			NucleusWaypoints.clearTemple();
			NucleusMod.CONFIG.save();
			rebuildWidgets();
		}).pos(cx - 150, y).size(300, 20).build();
		tip(modeButton, "Automatic finds both Door Guardians and places\nthe waypoints itself. Manual uses the hotkey.\nSwitching clears temple waypoints.");
		addRenderableWidget(modeButton);
		y += rowH;

		Button offsets = NucleusUi.NButton.create(Component.literal("Offsets: " + (NucleusMod.CONFIG.useReferenceOffsets ? "Right guardian" : "Original")), btn -> {
                NucleusMod.CONFIG.useReferenceOffsets=!NucleusMod.CONFIG.useReferenceOffsets;
                NucleusWaypoints.clearTemple(); NucleusMod.CONFIG.save(); rebuildWidgets();
            }).pos(cx-150,y).size(300,20).build();
            tip(offsets,"Right guardian: (+4,+10,+65), (+29,-32,+65), (+29,-32,+48).\nOriginal first point: (+3,+8,+63). Manual anchors at your feet.");
            addRenderableWidget(offsets); y+=rowH;
		if (!auto) {
			// Hotkey row: rebind button + set-now + clear (Manual only)
			keybindButton = NucleusUi.NButton.create(keybindLabel(), btn -> {
				listeningForCustomKey = false;
				listeningForRemoveLastKey = false;
				listeningForKey = true;
			}).pos(cx - 150, y).size(150, 20).build();
			tip(keybindButton, "Key that captures the temple waypoints.");
			addRenderableWidget(keybindButton);
			Button setNowButton = NucleusUi.NButton.create(Component.literal("Set now"), btn -> {
				Minecraft client = Minecraft.getInstance();
				if (client.player != null) {
					NucleusKeybinds.captureFromPlayer(client);
				}
			}).pos(cx + 5, y).size(70, 20).build();
			tip(setNowButton, "Capture the waypoints right now.");
			addRenderableWidget(setNowButton);
			Button clearButton = NucleusUi.NButton.create(Component.literal("Clear"), btn -> {
				NucleusWaypoints.clear();
				KcaChat.chat(Minecraft.getInstance(), Component.literal("§d[KCA] §fWaypoints cleared."));
			}).pos(cx + 80, y).size(70, 20).build();
			tip(clearButton, "Remove all temple waypoints.");
			addRenderableWidget(clearButton);
			y += rowH;
		}

		NucleusUi.NToggle throughWallsBox = NucleusUi.NToggle.builder(Component.literal("Waypoints visible through walls"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.waypointsThroughWalls)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.waypointsThroughWalls = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(throughWallsBox, "See waypoints through blocks.");
		addRenderableWidget(throughWallsBox);
		y += rowH;

		NucleusUi.NToggle dimFarBox = NucleusUi.NToggle.builder(Component.literal("Dim waypoints out of etherwarp range"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.waypointDimBeyondEtherwarp)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.waypointDimBeyondEtherwarp = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(dimFarBox, "Waypoints further than 61 blocks\nrender darker.");
		addRenderableWidget(dimFarBox);
		y += rowH;

		y += 16; // extra room for the Outline thickness label above the field

		NucleusUi.NToggle textBox = NucleusUi.NToggle.builder(Component.literal("Waypoint text labels"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.waypointText)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.waypointText = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(textBox, "Show distance labels on waypoints.");
		addRenderableWidget(textBox);

		outlineBox = new NucleusUi.NEditBox(this.font, cx + 5, y, 145, 18, Component.literal("Outline width"));
		outlineBox.setMaxLength(4);
		outlineBox.setValue(String.valueOf(NucleusMod.CONFIG.waypointOutlineWidth));
		outlineBox.setHint(Component.literal("e.g. 3.0"));
		tip(outlineBox, "Thickness of the waypoint boxes.");
		addRenderableWidget(outlineBox);
		y += rowH;

		NucleusUi.NToggle linkBox = NucleusUi.NToggle.builder(Component.literal("Link jungle waypoints"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.templeLinkLines)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.templeLinkLines = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(linkBox, "Draws lines WP1 -> WP2 -> WP3.\nJungle Temple waypoints only, never custom ones.");
		addRenderableWidget(linkBox);
		y += rowH;

		NucleusUi.NToggle show1Box = NucleusUi.NToggle.builder(Component.literal("Show Waypoint 1 "+(NucleusMod.CONFIG.useReferenceOffsets?"(+4,+10,+65)":"(+3,+8,+63)")), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.showWaypoint1)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.showWaypoint1 = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(show1Box, "Show or hide waypoint 1.");
		addRenderableWidget(show1Box);
		y += rowH;

		NucleusUi.NToggle show2Box = NucleusUi.NToggle.builder(Component.literal("Show Waypoint 2 (+29,-32,+65)"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.showWaypoint2)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.showWaypoint2 = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(show2Box, "Show or hide waypoint 2.");
		addRenderableWidget(show2Box);
		y += rowH;

		NucleusUi.NToggle show3Box = NucleusUi.NToggle.builder(Component.literal("Show Waypoint 3 (+29,-32,+48)"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.showWaypoint3)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.showWaypoint3 = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(show3Box, "Show or hide waypoint 3.");
		addRenderableWidget(show3Box);
		y += rowH;

		color1Button = NucleusUi.NButton.create(colorButtonLabel(1, NucleusMod.CONFIG.waypoint1Color), btn -> cycleColor(1))
			.pos(cx - 150, y).size(145, 20).build();
		tip(color1Button, "Change waypoint 1's color.");
		addRenderableWidget(color1Button);
		color2Button = NucleusUi.NButton.create(colorButtonLabel(2, NucleusMod.CONFIG.waypoint2Color), btn -> cycleColor(2))
			.pos(cx + 5, y).size(145, 20).build();
		tip(color2Button, "Change waypoint 2's color.");
		addRenderableWidget(color2Button);
		y += rowH;

		color3Button = NucleusUi.NButton.create(colorButtonLabel(3, NucleusMod.CONFIG.waypoint3Color), btn -> cycleColor(3))
			.pos(cx - 150, y).size(300, 20).build();
		tip(color3Button, "Change waypoint 3's color.");
		addRenderableWidget(color3Button);
		y += rowH;

		// --- Custom waypoints (same line settings as above) ---
		catWaypointsCustomY = y;
		y += 14;

		// Full-width rebind so long key names never clip.
		customKeybindButton = NucleusUi.NButton.create(customKeybindLabel(), btn -> {
			listeningForKey = false;
			listeningForRemoveLastKey = false;
			listeningForCustomKey = true;
		}).pos(cx - 150, y).size(300, 20).build();
		tip(customKeybindButton, "Key that drops a waypoint at your feet.");
		addRenderableWidget(customKeybindButton);
		y += rowH;

		Button setCustomButton = NucleusUi.NButton.create(Component.literal("Set custom waypoint"), btn -> {
			Minecraft client = Minecraft.getInstance();
			if (client.player != null) {
				NucleusKeybinds.captureCustomFromPlayer(client);
			}
		}).pos(cx - 150, y).size(145, 20).build();
		tip(setCustomButton, "Drop a waypoint where you stand.");
		addRenderableWidget(setCustomButton);

		NucleusUi.NToggle showCustomBox = NucleusUi.NToggle.builder(Component.literal("Show custom waypoints"), this.font)
			.pos(cx + 5, y).selected(NucleusMod.CONFIG.showCustomWaypoint)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.showCustomWaypoint = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(showCustomBox, "Show your custom waypoints.");
		addRenderableWidget(showCustomBox);
		y += rowH;

		removeLastKeybindButton = NucleusUi.NButton.create(removeLastKeybindLabel(), btn -> {
			listeningForKey = false;
			listeningForCustomKey = false;
			listeningForRemoveLastKey = true;
		}).pos(cx - 150, y).size(300, 20).build();
		tip(removeLastKeybindButton, "Key that removes the newest waypoint.");
		addRenderableWidget(removeLastKeybindButton);
		y += rowH;

		Button removeLastButton = NucleusUi.NButton.create(Component.literal("Remove last waypoint"), btn -> {
			Minecraft client = Minecraft.getInstance();
			NucleusKeybinds.removeLastCustom(client);
		}).pos(cx - 150, y).size(145, 20).build();
		tip(removeLastButton, "Remove the newest waypoint.");
		addRenderableWidget(removeLastButton);
		Button removeAllButton = NucleusUi.NButton.create(Component.literal("Remove all waypoints"), btn -> {
			NucleusWaypoints.clearCustom();
			NucleusMod.CONFIG.save();
			KcaChat.chat(Minecraft.getInstance(), Component.literal("§d[KCA] §fCustom waypoints cleared."));
		}).pos(cx + 5, y).size(145, 20).build();
		tip(removeAllButton, "Remove all custom waypoints.");
		addRenderableWidget(removeAllButton);
		y += rowH;

		customColorButton = NucleusUi.NButton.create(customColorLabel(NucleusMod.CONFIG.customWaypointColor),
			btn -> cycleCustomColor())
			.pos(cx - 150, y).size(300, 20).build();
		tip(customColorButton, "Change the custom waypoint color.");
		addRenderableWidget(customColorButton);
		y += rowH;

		// Footnote for this section, drawn right above the next header.
		moreCustomNoteY = y;
		y += 14;

		Button exportButton = NucleusUi.NButton.create(Component.literal("Export waypoints"), btn -> {
			String data = NucleusWaypoints.exportCustom();
			if (data.isEmpty()) {
				KcaChat.chat(Minecraft.getInstance(),
					Component.literal("§d[KCA] §7No custom waypoints to export."));
				return;
			}
			try {
				Minecraft.getInstance().keyboardHandler.setClipboard(data);
				KcaChat.chat(Minecraft.getInstance(), Component.literal(
					"§d[KCA] §fExported §e" + NucleusWaypoints.customCount()
						+ " §fcustom waypoints to clipboard."));
			} catch (Exception ignored) {
			}
		}).pos(cx - 150, y).size(145, 20).build();
		tip(exportButton, "Copies your current waypoint data to clipboard.");
		addRenderableWidget(exportButton);
		Button importButton = NucleusUi.NButton.create(Component.literal("Import waypoints"), btn -> {
			String data = null;
			try {
				data = Minecraft.getInstance().keyboardHandler.getClipboard();
			} catch (Exception ignored) {
			}
			int n = NucleusWaypoints.importCustom(data);
			if (n <= 0) {
				KcaChat.chat(Minecraft.getInstance(),
					Component.literal("§d[KCA] §7Clipboard has no waypoint data."));
			} else {
				KcaChat.chat(Minecraft.getInstance(), Component.literal(
					"§d[KCA] §fImported §e" + n + " §fcustom waypoints."));
			}
		}).pos(cx + 5, y).size(145, 20).build();
		tip(importButton, "Imports waypoints from your clipboard.");
		addRenderableWidget(importButton);
		y += rowH + 4;

		finishScrollable(Tab.WAYPOINTS, cx, y);
	}

	private int partyMessageField(int cx, int y, String label, String value, java.util.function.Consumer<String> change) {
        var field = new NucleusUi.NEditBox(font, cx - 150, y, 300, 18, Component.literal(label));
        field.setMaxLength(220);
        field.setValue(value == null ? "" : value);
        field.setHint(Component.literal(label));
        field.setResponder(change);
        tip(field, "Edit the party message. Use {coords} or {player}. For a rare drop, {drop} is the item name.");
        addRenderableWidget(field);
        return y + 22;
    }

    private void buildFeaturesTab(int cx, int startY) {
		int y = startY;
		int rowH = 22;

		// --- Mines of Divan (tool solver + tools tracker) ---
		catMinesY = y;
		y += 14;

		NucleusUi.NToggle divanChestBox = NucleusUi.NToggle.builder(Component.literal("Divan chest locator"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.divanChestEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.divanChestEnabled = val;
				if (!val) {
					DivanDetectorSolver.clearSpots();
				}
				NucleusMod.CONFIG.save();
			}).build();
		tip(divanChestBox, "Does math to find the position\nof MoD treasure chests!");
		addRenderableWidget(divanChestBox);
		y += rowH;

		NucleusUi.NToggle divanTextBox = NucleusUi.NToggle.builder(Component.literal("Chest distance text"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.divanChestText)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.divanChestText = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(divanTextBox, "Show distance labels on chests.");
		addRenderableWidget(divanTextBox);
		y += rowH;

		NucleusUi.NToggle divanMuteBox = NucleusUi.NToggle.builder(Component.literal("Mute metal detector"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.divanMuteDetector)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.divanMuteDetector = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(divanMuteBox, "Silences the detector harp ding\nwhile the detector is held.");
		addRenderableWidget(divanMuteBox);
		y += rowH;

		NucleusUi.NToggle scavengerBox = NucleusUi.NToggle.builder(Component.literal("Scavenger tracker"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.scavengerEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.scavengerEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(scavengerBox, "Tracks your mines of divan's tool farming performence");
		addRenderableWidget(scavengerBox);
		y += rowH;

		Button trackRateButton = NucleusUi.NButton.create(trackRateLabel(), btn -> {
			boolean next = !NucleusMod.CONFIG.scavengerSetsPerHour;
			NucleusMod.CONFIG.scavengerSetsPerHour = next;
			NucleusMod.CONFIG.save();
			btn.setMessage(trackRateLabel());
		}).pos(cx - 150, y).size(300, 20).build();
		tip(trackRateButton, "Tracker rate line counts tools or full sets per hour.");
		addRenderableWidget(trackRateButton);
		y += rowH;

		divanChestColorButton = NucleusUi.NButton.create(divanChestColorLabel(NucleusMod.CONFIG.divanChestColor),
			btn -> cycleDivanChestColor())
			.pos(cx - 150, y).size(300, 20).build();
		tip(divanChestColorButton, "Change the chest box color.");
		addRenderableWidget(divanChestColorButton);
		y += rowH;

		// --- Mob highlights ---
		catMobsY = y;
		y += 14;

		NucleusUi.NToggle mobBox = NucleusUi.NToggle.builder(Component.literal("Highlight NPCs"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.mobHighlightEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.mobHighlightEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(mobBox, "Mark King Yolkar, Professor Robot, the Keepers and Door Guardians when spotted.");
		addRenderableWidget(mobBox);
		y += rowH;

		Button mobClearButton = NucleusUi.NButton.create(Component.literal("Clear markers"), btn -> {
			MobMarkers.clear();
			KcaChat.chat(Minecraft.getInstance(), Component.literal("§d[KCA] §7Mob markers cleared."));
		}).pos(cx - 150, y).size(300, 20).build();
		tip(mobClearButton, "Forget every recorded mob spot.");
		addRenderableWidget(mobClearButton);
		y += rowH;

		// --- Trackers and texts ---
		catBalY = y;
		y += 14;

		// --- Alerts ---
        y=option(cx,y,"Crystal Hollows map",NucleusMod.CONFIG.hollowsMap,v->NucleusMod.CONFIG.hollowsMap=v,
            "Show regions, your position and known landmarks. Learns from visits, named coordinates in chat and available Skyblocker pins. Move or resize it in the HUD editor.");
        var mapSettings=NucleusUi.NButton.create(Component.literal("Map layers and landmarks"), b ->
            minecraft.gui.setScreen(new HollowsMapSettings(this))).pos(cx-150,y).size(300,20).build();
        addRenderableWidget(mapSettings);
        y+=22;
        y=option(cx,y,"Stash HUD",NucleusMod.CONFIG.showStash,v->NucleusMod.CONFIG.showStash=v,
            "Show item and material counts from SkyBlock stash messages. ? means the server has not reported a count.");
        y=option(cx,y,"Stash only in Crystal Hollows",NucleusMod.CONFIG.stashHollowsOnly,v->NucleusMod.CONFIG.stashHollowsOnly=v,
            "Restrict the stash HUD to the Crystal Hollows. KCA still tracks stash messages elsewhere on SkyBlock.");
        var stashMode=NucleusUi.NButton.create(Component.literal("Stash counts - "+StashHud.mode().label),b->{
            StashHud.cycleMode();
            b.setMessage(Component.literal("Stash counts - "+StashHud.mode().label));
        }).pos(cx-150,y).size(300,20).build();
        tip(stashMode,"Cycle between both counts, items only and materials only. Open your inventory and click [Edit stash] to move or resize the HUD.");
        addRenderableWidget(stashMode);
        y+=22;
        y=option(cx,y,"Tool sets HUD",NucleusMod.CONFIG.showToolSets,v->NucleusMod.CONFIG.showToolSets=v,
            "Open your inventory in the Hollows to edit your Divan tool sets or scan your backpacks.");
        var cfg = NucleusMod.CONFIG;
        y = option(cx, y, "Party message at Bal", cfg.balPartyAlert, v -> cfg.balPartyAlert = v,
            "Send to party chat when you enter Khazad-dûm. Use {coords} for your location.");
        y = partyMessageField(cx, y, "Bal arrival message", cfg.balPartyText, v -> cfg.balPartyText = v);
        y = option(cx, y, "Party message for rare drops", cfg.rarePartyAlert, v -> cfg.rarePartyAlert = v,
            "Announce your RARE DROP messages, Bal pets and rare Nucleus bundle drops. Use {drop} for the item.");
        y = partyMessageField(cx, y, "Rare drop message", cfg.rarePartyText, v -> cfg.rarePartyText = v);

		NucleusUi.NToggle petAlertBox = NucleusUi.NToggle.builder(Component.literal("Wrong pet alert"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.petAlertEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.petAlertEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(petAlertBox, "Warns you when you're using the wrong pet, *Requires the Pet tab widget to work!");
		addRenderableWidget(petAlertBox);
		y += rowH;

		NucleusUi.NToggle lowToolsBox = NucleusUi.NToggle.builder(Component.literal("Low tools alert"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.lowToolsAlertEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.lowToolsAlertEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(lowToolsBox, "Warns when you're low or out\nof scavenged tools.");
		addRenderableWidget(lowToolsBox);
		y += rowH;

        y=option(cx,y,"Mole check on tool reminder",NucleusMod.CONFIG.moleToolReminder,v->NucleusMod.CONFIG.moleToolReminder=v,
            "Include your active pet in the low-tools warning. Enable the Pet tab widget for detection before a pet swap.");

		NucleusUi.NToggle badLobbyBox = NucleusUi.NToggle.builder(Component.literal("Bad lobby alert"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.badLobbyAlertEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.badLobbyAlertEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(badLobbyBox, "Warns when you warp into a Hollows lobby you were\nalready in, or one over 14 days old (day "
			+ BadLobbyAlert.OLD_DAY + "+).");
		addRenderableWidget(badLobbyBox);
		y += rowH;

		NucleusUi.NToggle lobbyDayBox = NucleusUi.NToggle.builder(Component.literal("Lobby day display"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.lobbyDayEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.lobbyDayEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(lobbyDayBox, "Show the lobby day and time.");
		addRenderableWidget(lobbyDayBox);
		y += rowH;

		NucleusUi.NToggle balTimerBox = NucleusUi.NToggle.builder(Component.literal("Bal timer"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.balTimerEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.balTimerEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(balTimerBox, "Countdown to the next Bal respawn.");
		addRenderableWidget(balTimerBox);
		NucleusUi.NToggle balDetectorBox = NucleusUi.NToggle.builder(Component.literal("Bal Detector"), this.font)
			.pos(cx + 5, y).selected(NucleusMod.CONFIG.balDetectorEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.balDetectorEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(balDetectorBox, (Component.literal(
			"A Bal detector that shows you extra information about the state of Bal. ")
			.append(Component.literal("WARNING : This could maybe be seen as an unfair advantage.").withColor(0xFF5555))));
		addRenderableWidget(balDetectorBox);
		y += rowH;

		NucleusUi.NToggle balPetBox = NucleusUi.NToggle.builder(Component.literal("Bal pet drop animation"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.balPetAnimEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.balPetAnimEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(balPetBox, "Quick pop showing the rarity\nwhen you get a Bal pet drop.");
		addRenderableWidget(balPetBox);
		Button balPetTest = NucleusUi.NButton.create(Component.literal("Test"), btn -> {
			BalPetDrop.reset();
			BalPetDrop.start(Math.random() < 0.5);
			this.onClose();
		}).pos(cx + 5, y).size(145, 20).build();
		tip(balPetTest, "Preview the Bal pet drop animation.");
		addRenderableWidget(balPetTest);
		y += rowH;

		NucleusUi.NToggle terrorStacksBox = NucleusUi.NToggle.builder(Component.literal("Terror stacks overlay"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.terrorStacksEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.terrorStacksEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(terrorStacksBox, "Shows your Terror (Hydra Strike)\narmor stacks from the action bar.");
		addRenderableWidget(terrorStacksBox);
		y += rowH;

		IntSlider timerBgSlider = new IntSlider(cx - 150, y, 300, "Timer background", 0, 100,
			NucleusMod.CONFIG.timerBg, "%", v -> NucleusMod.CONFIG.timerBg = v);
		tip(timerBgSlider, "Background shade behind timer text.");
		addRenderableWidget(timerBgSlider);
		y += rowH;

		// --- Themes (its own sidebar tab): unlocked by the share of achievements you have ---
		catThemesY = y;
		y += 14;
		themesLabelY = y;
		y += 13;
		themeSwatches.clear();
		int pct = NucleusTheme.achievementPct();
		NucleusTheme.Theme active = NucleusTheme.current();
		for (NucleusTheme.Theme th : NucleusTheme.THEMES) {
			final NucleusTheme.Theme theme = th;
			boolean open = NucleusTheme.unlocked(th);
			String label = th.name() + (th == active ? "  \u2714" : open ? "" : "  \u00a78" + th.unlockPct() + "%");
			NucleusUi.NButton b = NucleusUi.NButton.create(Component.literal(label), btn -> {
				NucleusTheme.select(theme);
				rebuildWidgets();
			}).pos(cx - 150, y).size(300, 20).accent(th.title()[0]).build();
			b.selected(th == active);
			b.active = open;
			tip(b, th.desc() + (open ? "" : "\nUnlocks at " + th.unlockPct() + "% of achievements (you have " + pct + "%)."));
			addRenderableWidget(b);
			themeSwatches.put(b, th);
			y += rowH;
		}
		y += 4;

		NucleusUi.NToggle seasonBox = NucleusUi.NToggle.builder(Component.literal("Seasonal effects"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.seasonalEffects)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.seasonalEffects = val;
				NucleusMod.CONFIG.save();
			}).build();
		NucleusSeason.Season today = NucleusSeason.today();
		StringBuilder seasonTip = new StringBuilder("Little extras on top of any theme, by date:");
		for (NucleusSeason.Season se : NucleusSeason.Season.values()) {
			if (se != NucleusSeason.Season.NONE) {
				seasonTip.append("\n").append(se == today ? "\u00a7a\u25b6 " : "\u00a77  ").append(se.label)
					.append(": \u00a78").append(se.desc);
			}
		}
		seasonTip.append("\n\u00a7r").append(today == NucleusSeason.Season.NONE ? "Nothing on right now."
			: "Now: " + today.label);
		tip(seasonBox, seasonTip.toString());
		addRenderableWidget(seasonBox);
		y += rowH + 6;

		NucleusUi.NToggle wheelFxBox = NucleusUi.NToggle.builder(Component.literal("Themed gambling visuals"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.themedWheelFx)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.themedWheelFx = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(wheelFxBox, "Dress the wheel animation in your theme: its colours,\ndrifting light bands, side glows and particles.\nOff: the plain dark backdrop.");
		addRenderableWidget(wheelFxBox);
		y += rowH + 6;

		finishScrollable(Tab.FEATURES, cx, y);
	}

    private void buildCrystalHud(int cx, int y) {
        var cfg = NucleusMod.CONFIG;
        y = option(cx,y,"Crystal checklist",cfg.showCrystalChecklist,v -> cfg.showCrystalChecklist=v,
            "Show the crystals you collect or place during a Nucleus run.");
        var layout = NucleusUi.NButton.create(Component.literal("Layout - " + cfg.crystalLayout.label), b -> {
            var choices = com.nucleus.NucleusConfig.CrystalLayout.values();
            cfg.crystalLayout = choices[(cfg.crystalLayout.ordinal()+1)%choices.length];
            cfg.save();
            rebuildWidgets();
        }).pos(cx-150,y).size(300,18).build();
        tip(layout,"Choose text, a vertical list with names, or a row of icons that light up as you collect each crystal.");
        addRenderableWidget(layout);
        y = guideLine(cx,y+25,"Display order",true);
        for (int i=0; i<cfg.crystalOrder.size(); i++) {
            String name = cfg.crystalOrder.get(i).label;
            guideLine(cx,y+4,name,false);
            for (int step : new int[]{-1,1}) {
                int from=i, to=i+step;
                var button=NucleusUi.NButton.create(Component.literal(step<0?"Earlier":"Later"), b -> {
                    cfg.moveCrystal(from,to);
                    rebuildWidgets();
                }).pos(cx+(step<0?0:78),y).size(72,18).build();
                button.active=to>=0 && to<cfg.crystalOrder.size();
                tip(button,"Move " + name + (step<0?" earlier":" later") + " on the crystal HUD.");
                addRenderableWidget(button);
            }
            y+=22;
        }
        finishScrollable(Tab.FEATURES,cx,y);
    }

    private int option(int cx,int y,String label,boolean initial,java.util.function.Consumer<Boolean> change,String description){
        var toggle=NucleusUi.NToggle.builder(Component.literal(label),font).pos(cx-150,y).selected(initial)
            .onValueChange((box,value)->{change.accept(value);NucleusMod.CONFIG.save();}).build();
        tip(toggle,description);addRenderableWidget(toggle);return y+22;
    }

    private int guideLine(int cx,int y,String text,boolean heading){
        var label=new net.minecraft.client.gui.components.MultiLineTextWidget(cx-146,y,
            Component.literal(text).withColor(heading?NucleusMod.CONFIG.nucleusAccent&0xFFFFFF:0xDFD2D9),font).setMaxWidth(292);
        addRenderableWidget(label);return y+label.getHeight()+(heading?5:12);
    }
    private int gamblingVolume(int cx,int y,int rowHeight){
        var slider=new IntSlider(cx-150,y,300,"Gambling animation volume",0,200,
            NucleusMod.CONFIG.jackpotVolume,"%",volume -> NucleusMod.CONFIG.jackpotVolume=volume);
        tip(slider,"Music and effects for previews and real drops. 0% mutes the animation. 100% is normal.");
        addRenderableWidget(slider);
        var finale=new IntSlider(cx-150,y+rowHeight,300,"Final reveal volume",0,100,
            NucleusMod.CONFIG.finalRevealVolume,"%",volume -> NucleusMod.CONFIG.finalRevealVolume=volume);
        tip(finale,"Fanfare and effects after the roll. 25% plays at a quarter of the animation volume. 0% mutes the ending.");
        addRenderableWidget(finale);
        return y+2*rowHeight;
    }

    private void buildGambleGuide(int cx,int y){
        y=guideLine(cx,y,"Playing a reveal",true);
        y=guideLine(cx,y,"Mado (Madobrick) created the original wheel animation and final drop reveal for Nucleus Utilities.",false);
        y=guideLine(cx,y,"Choose a game and an ending in Animation. Auto pairs each game with its own ending. Open Timing to adjust the three speeds, bind quick-spin or skip the opening sequence.",false);
        y=guideLine(cx,y,"Crystal vault turns each crystal in its lock to light the outer geodes and open the treasure chamber. Briefcase slots spins two banks of three item reels. Listen for the metal latch after the sixth match. Trial vault ejects loot through its top shutter before the final reveal. Turn off Vault key loops, Vault light trails, or Vault lock sounds under Style. Your drop comes from the loot announcement, so another roll cannot change an Alloy into a different item.",false);
        y=guideLine(cx,y,"Select a Preview drop and press Test animation in a world. Titles edits the wording. Confetti controls the particles. Gambling animation volume controls the music and effects. The same slider is also under Sounds.",false);
        y=guideLine(cx,y,"Working with the mod source",true);
        y=guideLine(cx,y,"1. In src/main/java/com/nucleus/client, JackpotDetector reads bundle and drop messages and passes a DropType to JackpotAnimation.start. startPreview supplies the same type for testing.",false);
        y=guideLine(cx,y,"2. To add a game, extend NucleusConfig.Game, select its timing and sound cues in JackpotAnimation, then draw it from JackpotHud during Stage.WHEEL. CrystalVaultHud animates five keys into fixed sockets before its gate opens.",false);
        y=guideLine(cx,y,"3. For an ending, extend NucleusConfig.Ending and map Auto in start. Draw the backdrop in JackpotHud.drawMaxWin. fireTotem shows the detected item and WinRollup provides the value and title tier.",false);
        y=guideLine(cx,y,"4. Use playJackpot for a timed sound so the volume setting applies. Run build.ps1 with Java 25, replace the test profile jar, and preview a fixed Alloy, Jade Dye and low-value drop.",false);
        finishScrollable(Tab.MISC,cx,y);
    }
    private void buildGambleTitles(int cx,int y){
        y=guideLine(cx,y,"Type a new title. Clear the field to use its default. Edits apply to all games.",false);
        var labels=java.util.List.of("LEGENDARY WIN","SUPER LUCKY!","BIG WIN","SUPER WIN","MEGA WIN","EPIC WIN",
            "INSANE WIN","MAX LOSE","LUCK","Super unlucky","Unlucky","Average","Lucky");
        var cfg=NucleusMod.CONFIG;
        int i=0;
        for(String original:labels){
            int x=cx-150+(i%2)*154;
            EditBox box=new NucleusUi.NEditBox(font,x,y+14+(i/2)*36,146,18,Component.literal(original));
            box.setMaxLength(28);box.setValue(cfg.gambleTitles.getOrDefault(original,""));
            box.setHint(Component.literal(com.nucleus.NucleusConfig.defaultTitle(original)));
            box.setResponder(value->{if(value.isBlank())cfg.gambleTitles.remove(original);else cfg.gambleTitles.put(original,value.strip());});
            titleFields.put(original,box);addRenderableWidget(box);i++;
        }
        finishScrollable(Tab.MISC,cx,y+(i+1)/2*36+6);
    }
    private void buildGambleTiming(int cx,int y) {
        var cfg=NucleusMod.CONFIG;
        int rowH=22;
        y=option(cx,y,"Opening sequence",cfg.wheelOpening,v->cfg.wheelOpening=v,"Turn off to start the selected game immediately.");
        y=option(cx,y,"Luck roll",cfg.wheelRollScreens,v->cfg.wheelRollScreens=v,"Show the luck verdict after the opening title.");
        y=guideLine(cx,y,"The opening shows the title, then the optional luck roll. Switch off Opening sequence to go straight to your game.",false);
		quickSpinKeyButton = NucleusUi.NButton.create(quickSpinKeyLabel(), btn -> {
			listeningForKey = false;
			listeningForCustomKey = false;
			listeningForRemoveLastKey = false;
			listeningForQuickSpinKey = true;
			btn.setMessage(quickSpinKeyLabel());
		}).pos(cx - 150, y).size(300, 20).build();
		tip(quickSpinKeyButton, "Choose a key. Esc cancels rebinding.");
		addRenderableWidget(quickSpinKeyButton);
		y += rowH;

        y=guideLine(cx,y,"During a roll, press this key to jump to its last second. The landing and final reveal still play.",false);
        y=guideLine(cx,y,"100% is normal speed. Lower values play more slowly. Opening speed controls the title and rolls, Game speed controls the main animation, and Final reveal speed controls the reward celebration.",false);
		IntSlider speedEarlySlider = new IntSlider(cx - 150, y, 300, "Opening speed",
			50, 200, NucleusMod.CONFIG.jackpotSpeedEarlyPct, "%", v -> NucleusMod.CONFIG.jackpotSpeedEarlyPct = v);
		tip(speedEarlySlider, "Speed of the opening title and luck roll. Higher is faster.\n100% is normal speed.");
		addRenderableWidget(speedEarlySlider);
		y += rowH;

		IntSlider speedWheelSlider = new IntSlider(cx - 150, y, 300, "Game speed",
			50, 200, NucleusMod.CONFIG.jackpotSpeedWheelPct, "%", v -> NucleusMod.CONFIG.jackpotSpeedWheelPct = v);
		tip(speedWheelSlider, "Speed of the selected game.\nHigher is faster.\n100% is normal speed.");
		addRenderableWidget(speedWheelSlider);
		y += rowH;

		addRenderableWidget(new IntSlider(cx-150,y,300,"Crystal vault opening",500,3500,cfg.crystalGateMs," ms",v->cfg.crystalGateMs=v));
        y+=rowH;
        IntSlider speedCelebSlider = new IntSlider(cx - 150, y, 300, "Final reveal speed",
			50, 200, NucleusMod.CONFIG.jackpotSpeedCelebPct, "%", v -> NucleusMod.CONFIG.jackpotSpeedCelebPct = v);
		tip(speedCelebSlider, "Speed of the celebration screen.\nHigher is faster.\n100% is normal speed.");
		addRenderableWidget(speedCelebSlider);
		y += rowH;

        finishScrollable(Tab.MISC,cx,y);
    }
    private void buildGameStyle(int cx,int y){
        var cfg=NucleusMod.CONFIG;
        int rowH=22;
        if(currentSection==5){
        y=option(cx,y,"Original wheel arrow",cfg.originalWheelPointer,v->cfg.originalWheelPointer=v,"Use the large orange arrow on the wheel. Off uses a small rose-coloured peg.");
        y=option(cx,y,"Gold pointer mount",cfg.wheelMount,v->cfg.wheelMount=v,"Show the large gold disc above the pointer.");
        y=option(cx,y,"Wheel sparkles",cfg.wheelSparkles,v->cfg.wheelSparkles=v,"Twinkling highlights on the rare drop slices.");
		IntSlider zoomSlider = new IntSlider(cx - 150, y, 300, "Zoom level",
			0, 100, NucleusMod.CONFIG.jackpotZoomPct, "%", v -> NucleusMod.CONFIG.jackpotZoomPct = v);
		tip(zoomSlider, "How hard the camera pushes in at the landing.\n0% turns the zoom off.");
		addRenderableWidget(zoomSlider);
		y += rowH;

        }else if(currentSection==6){
        y=option(cx,y,"Vault key loops",cfg.vaultKeyLoops,v->cfg.vaultKeyLoops=v,"Loop every Trial key and the Crystal vault Topaz. Off uses the shorter curved paths.");
        y=option(cx,y,"Vault light trails",cfg.vaultKeyTrails,v->cfg.vaultKeyTrails=v,"Show a colored ribbon behind each flying key.");
        y=option(cx,y,"Vault lock sounds",cfg.vaultLockSounds,v->cfg.vaultLockSounds=v,"Play the key clicks and the shutter sound when a vault unlocks.");
        y=option(cx,y,"Cool trial vault accents",cfg.coolTrialAccent,v->cfg.coolTrialAccent=v,"Switch the trial vault frame and door trim from KCA pink to blue amethyst.");
        }
        finishScrollable(Tab.MISC,cx,y);
    }
    private void buildShuffle(int cx,int y){
        var cfg=NucleusMod.CONFIG;
        y=option(cx,y,"Randomize each animation",cfg.shuffleGames,v->cfg.shuffleGames=v,"Picks again each time a drop uses Default.");
        y=guideLine(cx,y,"Include the games you want. Keep at least one selected. A named game on the Drops tab takes priority.",false);
        for(var game:com.nucleus.NucleusConfig.PLAYABLE){
            boolean included=!cfg.excludedGames.contains(game);
            var toggle=NucleusUi.NToggle.builder(Component.literal(game.toString()),font)
                .pos(cx-150,y).selected(included).onValueChange((button,value)->{
                    if(value)cfg.excludedGames.remove(game);else cfg.excludedGames.add(game);
                    cfg.save();rebuildWidgets();
                }).build();
            toggle.active=!included || cfg.excludedGames.size()<com.nucleus.NucleusConfig.PLAYABLE.length-1;
            addRenderableWidget(toggle);y+=22;
        }
        finishScrollable(Tab.MISC,cx,y);
    }
    private void buildDropRules(int cx,int y){
        var cfg=NucleusMod.CONFIG;
        int rowH=22;
        y=option(cx,y,"Animate every bundle",cfg.everyBundle,v->cfg.everyBundle=v,"Show one reveal per bundle, even when it contains ordinary loot.");
        y=guideLine(cx,y,"Every bundle uses its highest-priority recognized drop. Other bundles show their loot bundle. With this off, the four odds below control eligible loot lines.",false);
		y += 16; // room for the odds labels above the fields (label sits 12px above box)
		oddsJaderaldBox = oddsBox(cx - 150, y, NucleusMod.CONFIG.jackpotOddsJaderald, "Jaderald 1-in-N odds.");
		oddsFragmentBox = oddsBox(cx + 5, y, NucleusMod.CONFIG.jackpotOddsFragment, "Divan Fragment 1-in-N odds.");
		y += rowH;
		y += 16; // labels for the second row sit clear of the first boxes
		oddsMixtureBox = oddsBox(cx - 150, y, NucleusMod.CONFIG.jackpotOddsMixture, "Gemstone Mixture 1-in-N odds.");
		oddsFlawlessBox = oddsBox(cx + 5, y, NucleusMod.CONFIG.jackpotOddsFlawless, "Flawless Gemstone 1-in-N odds.");
		y += rowH;

        y=guideLine(cx,y,"Default follows Animation. Random vault chooses Crystal or Trial each time. Auto ending follows the game that was picked.",false);
        for(var drop:JackpotAnimation.DropType.values()){
            String id=drop.name();
            var b=NucleusUi.NButton.create(dropRuleLabel(drop),btn->{
                var games=com.nucleus.NucleusConfig.Game.values();
                var current=cfg.dropGames.get(id);
                if(current==null)cfg.dropGames.put(id,games[0]);
                else if(current.ordinal()+1==games.length)cfg.dropGames.remove(id);
                else cfg.dropGames.put(id,games[current.ordinal()+1]);
                cfg.save();btn.setMessage(dropRuleLabel(drop));
            }).pos(cx-150,y).size(300,20).build();
            addRenderableWidget(b);y+=rowH;
        }
        finishScrollable(Tab.MISC,cx,y);
    }
    private Component dropRuleLabel(JackpotAnimation.DropType drop){
        var game=NucleusMod.CONFIG.dropGames.get(drop.name());
        return Component.literal(drop.displayName+" - "+(game==null?"Default":game.toString()));
    }
	private void buildMiscTab(int cx, int startY) {
		int y = startY;
		int rowH = 22;

		NucleusUi.NToggle gamblingBox = NucleusUi.NToggle.builder(Component.literal("Gambling animation"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.jackpotEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.jackpotEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(gamblingBox, "Play the selected animation on rare drops.");
		addRenderableWidget(gamblingBox);
		y += rowH;

        var cfg=NucleusMod.CONFIG;
        Button style = NucleusUi.NButton.create(Component.literal("Animation - "+cfg.selectedGame()), btn -> {
            var games=com.nucleus.NucleusConfig.PLAYABLE;
            cfg.gambleGame=games[(cfg.selectedGame().ordinal()+1)%games.length];
            cfg.save();
            btn.setMessage(Component.literal("Animation - "+cfg.selectedGame()));
        }).pos(cx-150,y).size(300,20).build();
        tip(style,"Select the game that plays before the drop appears.");
        addRenderableWidget(style);
        y+=rowH;
        Button end=NucleusUi.NButton.create(Component.literal("Ending - "+cfg.selectedEnding()),btn->{
            var ends=com.nucleus.NucleusConfig.Ending.values();
            cfg.gambleEnding=ends[(cfg.selectedEnding().ordinal()+1)%ends.length];
            cfg.save();btn.setMessage(Component.literal("Ending - "+cfg.selectedEnding()));
        }).pos(cx-150,y).size(300,20).build();
        tip(end,"Auto matches the game. Pick any named ending to use it with every game.");
        addRenderableWidget(end);y+=rowH;
        y=gamblingVolume(cx,y,rowH);
		Button previewButton = NucleusUi.NButton.create(Component.literal(
				"Preview drop: " + JackpotAnimation.previewLabel(NucleusMod.CONFIG.jackpotPreview)), btn -> {
			String next = JackpotAnimation.nextPreview(NucleusMod.CONFIG.jackpotPreview);
			NucleusMod.CONFIG.jackpotPreview = next;
			NucleusMod.CONFIG.save();
			btn.setMessage(Component.literal(
				"Preview drop: " + JackpotAnimation.previewLabel(next)));
		}).pos(cx - 150, y).size(300, 20).build();
		tip(previewButton, "Which drop the test animation shows.\nRandom gambles like the real thing.");
		addRenderableWidget(previewButton);
		y += rowH;

		Button testButton = NucleusUi.NButton.create(Component.literal("Test animation"), btn -> {
			// Out of the config first so the stage has the full screen.
			saveFields();
            minecraft.gui.setScreen(null);
			JackpotAnimation.DropType shown =
				JackpotAnimation.startPreview(NucleusMod.CONFIG.jackpotPreview);
			KcaChat.chat(Minecraft.getInstance(), Component.literal(
				"§d[KCA] §7" + JackpotAnimation.game + " preview..."));
		}).pos(cx - 150, y).size(300, 20).build();
		testButton.active=minecraft.level!=null && cfg.showGameplayHud && cfg.jackpotEnabled;
        tip(testButton, minecraft.level==null?"Join a world to preview the animation.":!cfg.showGameplayHud || !cfg.jackpotEnabled?"Enable Gameplay HUD and Gambling animation to see the preview.":"Close settings and preview the jackpot animation.");
		addRenderableWidget(testButton);
		y += rowH;

        y=option(cx,y,"Legacy 3D reward",cfg.legacyRewardPopup,v->cfg.legacyRewardPopup=v,"Use the original spinning item. Turn this off to show the bundled drop icon if rewards appear blank.");
        y=option(cx,y,"Reward banner",cfg.revealBanner,v->cfg.revealBanner=v,"Show your custom win or loss heading during the final reveal.");
        y=option(cx,y,"Reveal flashes",cfg.revealFlashes,v->cfg.revealFlashes=v,"Show full-screen flashes and the flashing border during the final reveal.");
        Button confetti=NucleusUi.NButton.create(Component.literal("Confetti - "+cfg.confettiPercent+"%"),btn->{
            cfg.confettiPercent=cfg.confettiPercent==100?35:cfg.confettiPercent==0?100:0;
            cfg.save();btn.setMessage(Component.literal("Confetti - "+cfg.confettiPercent+"%"));
        }).pos(cx-150,y).size(300,20).build();
        tip(confetti,"Amount of falling and bursting confetti during the reward.");addRenderableWidget(confetti);y+=rowH;
		NucleusUi.NToggle hideBundleBox = NucleusUi.NToggle.builder(Component.literal("Hide bundle"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.hideBundle)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.hideBundle = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(hideBundleBox, "Black 3D box over the Nucleus while\nthe wheel spins, gone at the reveal.");
		addRenderableWidget(hideBundleBox);
		y += rowH + 4;

		// --- Misc ---

		Button resetButton = NucleusUi.NButton.create(Component.literal("Reset to defaults"), btn ->
			ConfirmResetScreen.open(this)).pos(cx - 155, y).size(150, 20).build();
		tip(resetButton, "Restore default settings.");
		addRenderableWidget(resetButton);
		
		y += rowH + 4;

		finishScrollable(Tab.MISC, cx, y);
	}

	/** Numeric 1-in-N field for one consolation lottery. */
	private EditBox oddsBox(int x, int y, int initial, String tipText) {
		EditBox box = new NucleusUi.NEditBox(this.font, x, y, 145, 18, Component.literal("1 in N"));
		box.setMaxLength(7);
		box.setValue(String.valueOf(initial));
		box.setHint(Component.literal("e.g. 13"));
		tip(box, tipText);
		addRenderableWidget(box);
		return box;
	}

	private void buildSoundsTab(int cx, int startY) {
		int y = startY;
		int rowH = 22;

		// --- Playback ---
		catPlaybackY = y;
		y += 14;

		Button soundToggleButton = NucleusUi.NButton.create(soundToggleLabel(), btn -> {
			boolean next = !NucleusMod.CONFIG.objectiveSoundOn;
			NucleusMod.CONFIG.objectiveSoundOn = next;
			NucleusMod.CONFIG.save();
			btn.setMessage(soundToggleLabel());
		}).pos(cx - 150, y).size(300, 20).build();
		tip(soundToggleButton, "Plays a sound whenever you complete an objective\nof the Nucleus Run. Example: give Professor Robot\nthe Precursor Apparatus or collect a crystal.");
		addRenderableWidget(soundToggleButton);
		y += rowH;

		IntSlider volumeSlider = new IntSlider(cx - 150, y, 300, "Sound volume", 0, 200,
			NucleusMod.CONFIG.objectiveSoundVolume, "%", v -> NucleusMod.CONFIG.objectiveSoundVolume = v);
		tip(volumeSlider, "How loud the objective sound plays.\nWith the bypass below ON, MC sliders are ignored.");
		addRenderableWidget(volumeSlider);
		y += rowH;

		NucleusUi.NToggle bypassBox = NucleusUi.NToggle.builder(Component.literal("Overwrite Minecraft sound menu"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.bypassMinecraftVolume)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.bypassMinecraftVolume = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(bypassBox, "ON: mod sounds ignore Minecraft's volume sliders\nand use only the mod's own sliders.\nOFF: sounds go through the vanilla mixer\n(Master and category sliders apply).\nCustom .wav files always play directly.");
		addRenderableWidget(bypassBox);
		y += rowH;

        y = gamblingVolume(cx, y, rowH);


		IntSlider achVolumeSlider = new IntSlider(cx - 150, y, 300, "Achievement unlock volume", 0, 200,
			NucleusMod.CONFIG.achievementVolume, "%", v -> NucleusMod.CONFIG.achievementVolume = v);
		tip(achVolumeSlider, "How loud the achievement unlock jingle plays.\nWith the bypass above ON, MC sliders are ignored.\n0% silences it.");
		addRenderableWidget(achVolumeSlider);
		y += rowH + 10; // room for the sound label above the field

		soundIdBox = new NucleusUi.NEditBox(this.font, cx - 150, y, 300, 18, Component.literal("Minecraft sound"));
		soundIdBox.setMaxLength(80);
		soundIdBox.setValue(NucleusMod.CONFIG.objectiveSoundId);
		soundIdBox.setHint(Component.literal("minecraft:block.bone_block.place"));
		tip(soundIdBox, "Any Minecraft sound, as its id.");
		addRenderableWidget(soundIdBox);
		y += rowH;

		NucleusUi.NToggle customBox = NucleusUi.NToggle.builder(Component.literal("Custom sound file"), this.font)
			.pos(cx - 150, y).selected(NucleusMod.CONFIG.customSoundEnabled)
			.onValueChange((box, val) -> {
				NucleusMod.CONFIG.customSoundEnabled = val;
				NucleusMod.CONFIG.save();
			}).build();
		tip(customBox, "Play a .wav file instead of a Minecraft sound.");
		addRenderableWidget(customBox);
		Button previewButton = NucleusUi.NButton.create(Component.literal("Preview"), btn ->
			ObjectiveSounds.preview()).pos(cx + 5, y).size(145, 20).build();
		tip(previewButton, "Hear the selected sound right now.");
		addRenderableWidget(previewButton);
		y += rowH;

		customPathBox = new NucleusUi.NEditBox(this.font, cx - 150, y, 145, 18, Component.literal("Sound file"));
		customPathBox.setMaxLength(260);
		customPathBox.setValue(NucleusMod.CONFIG.customSoundPath);
		customPathBox.setHint(Component.literal("C:\\...\\sound.wav"));
		tip(customPathBox, "Path to your .wav file.");
		addRenderableWidget(customPathBox);
		Button browseButton = NucleusUi.NButton.create(Component.literal("Browse..."), btn -> {
			String picked = ObjectiveSounds.browseWav();
			if (picked != null) {
				customPathBox.setValue(picked);
				NucleusMod.CONFIG.customSoundPath = picked;
				NucleusMod.CONFIG.customSoundEnabled = true;
				NucleusMod.CONFIG.save();
				KcaChat.chat(Minecraft.getInstance(), Component.literal(
					"§d[KCA] §7Custom sound set. Press Preview to hear it."));
			}
		}).pos(cx + 5, y).size(145, 20).build();
		tip(browseButton, "Pick a .wav file from your computer.");
		addRenderableWidget(browseButton);
		y += rowH;

		// --- Triggers (each one gateable, like the Speedruns tab) ---
		catTriggersY = y;
		y += 14;

		y = addTriggerRow(cx, y, "King Yolkar's stench", "yolkar",
			NucleusMod.CONFIG.soundYolkar,
			"Sound for the foul stench handover.");
		y = addTriggerRow(cx, y, "Apparatus delivered", "apparatus",
			NucleusMod.CONFIG.soundApparatus,
			"Sound for giving Robot the apparatus or parts.");
		y = addTriggerRow(cx, y, "Keeper tool returned", "tool",
			NucleusMod.CONFIG.soundTool,
			"Sound for each scavenged tool handover.");
		y = addTriggerRow(cx, y, "Jungle Key delivered", "key",
			NucleusMod.CONFIG.soundKey,
			"Sound for opening the Jungle Temple door.");
		y = addTriggerRow(cx, y, "Bal defeated", "bal",
			NucleusMod.CONFIG.soundBal,
			"Sound for the Bal kill.");
		y = addTriggerRow(cx, y, "Crystal obtained", "crystal",
			NucleusMod.CONFIG.soundCrystal,
			"Sound for picking up any of the 5 crystals.");
		y = addTriggerRow(cx, y, "Crystal placed", "place",
			NucleusMod.CONFIG.soundPlace,
			"Sound for each crystal placed in the Nucleus.");
		y = addTriggerRow(cx, y, "Divan treasure chest", "chest",
			NucleusMod.CONFIG.soundChest,
			"Sound for opening a Mines of Divan treasure chest.");

		// --- Gambling sound packs: same unlock shares as the themes ---
		soundPacksLabelY = y;
		y += 13;
		NucleusSoundPack.Pack activePack = NucleusSoundPack.current();
		for (NucleusSoundPack.Pack pk : NucleusSoundPack.PACKS) {
			final NucleusSoundPack.Pack pack = pk;
			boolean open = NucleusSoundPack.unlocked(pk);
			String label = pk.name() + (pk == activePack ? "  \u2714" : open ? "" : "  \u00a78" + pk.unlockPct() + "%");
			NucleusUi.NButton b = NucleusUi.NButton.create(Component.literal(label), btn -> {
				NucleusSoundPack.select(pack);
				NucleusSoundPack.preview(pack);
				rebuildWidgets();
			}).pos(cx - 150, y).size(300, 20).accent(NucleusSoundPack.accent(pk)).build();
			b.selected(pk == activePack);
			b.active = open;
			tip(b, pk.desc() + (open ? "\nClick to use it (plays a short preview)." : "\nUnlocks at " + pk.unlockPct()
				+ "% of achievements (you have " + NucleusTheme.achievementPct() + "%)."));
			addRenderableWidget(b);
			y += rowH;
		}

		finishScrollable(Tab.SOUNDS, cx, y);
	}

	/** One trigger row: a plain checkbox, one per sound element. */
	private int addTriggerRow(int cx, int y, String label, String id,
		boolean initial, String tipText) {
		NucleusUi.NToggle box = NucleusUi.NToggle.builder(Component.literal(label), this.font)
			.pos(cx - 150, y).selected(initial)
			.onValueChange((b, val) -> {
				writeTrigger(id, val);
				NucleusMod.CONFIG.save();
			}).build();
		tip(box, tipText);
		addRenderableWidget(box);
		return y + 22;
	}

	private static void writeTrigger(String id, boolean value) {
		switch (id) {
			case "yolkar" -> NucleusMod.CONFIG.soundYolkar = value;
			case "crystal" -> NucleusMod.CONFIG.soundCrystal = value;
			case "apparatus" -> NucleusMod.CONFIG.soundApparatus = value;
			case "tool" -> NucleusMod.CONFIG.soundTool = value;
			case "key" -> NucleusMod.CONFIG.soundKey = value;
			case "bal" -> NucleusMod.CONFIG.soundBal = value;
			case "place" -> NucleusMod.CONFIG.soundPlace = value;
			default -> NucleusMod.CONFIG.soundChest = value;
		}
	}

	/**
	 * Snapshot scrollable content (tab bar sits above contentTop, Done stays
	 * pinned below the viewport). Restores this tab's last scroll offset.
	 */
	private void finishScrollable(Tab tab, int cx, int y) {
		int contentTop = contentTop();
		// Each sidebar tab is one section of this tab: drop the widgets of
		// the other sections and lift this one to the top (its header is
		// the sidebar tab itself, so the in-page header is skipped).
		int[] hdrs = sectionHeaders(tab);
		sectionStart = Integer.MIN_VALUE;
		sectionEnd = Integer.MAX_VALUE;
		sectionShift = 0;
		if (hdrs.length > 0) {
			int idx = Math.max(0, Math.min(hdrs.length - 1, currentSection));
			currentSection = idx;
			sectionStart = hdrs[idx];
			sectionEnd = idx + 1 < hdrs.length ? hdrs[idx + 1] : Integer.MAX_VALUE;
			sectionShift = sectionStart + 14 - (contentTop + 4);
			for (var child : new java.util.ArrayList<>(this.children())) {
				if (child instanceof AbstractWidget aw && aw.getY() >= contentTop
					&& (aw.getY() < sectionStart || aw.getY() >= sectionEnd)) {
					aw.visible = false;
					removeWidget(aw);
				}
			}
			if (sectionEnd != Integer.MAX_VALUE) {
				y = sectionEnd;
			}
		}
		moreScroll = scrollMemory.getOrDefault(sectionKey(), 0);
		int viewportBottom = moreViewportBottom();
		int contentBottom = y - sectionShift;
		for (var child : new java.util.ArrayList<>(this.children())) {
			// Only the content column scrolls: the sidebar tabs sit left of
			// the panel (and the top tab row above it) and never move.
			if (child instanceof AbstractWidget aw && aw.getY() >= contentTop) {
				int base = aw.getY() - sectionShift;
				moreBaseY.put(aw, base);
				contentBottom = Math.max(contentBottom, base + aw.getHeight());
			}
		}
		moreMaxScroll = Math.max(0, contentBottom - viewportBottom);
		if(pendingSetting!=null){
            for(var entry:moreBaseY.entrySet()){
                if(entry.getKey().getMessage().getString().equals(pendingSetting)){
                    moreScroll=entry.getValue()-contentTop()-3;break;
                }
                if(entry.getKey().getMessage().getString().startsWith(pendingSetting)){
                    moreScroll=entry.getValue()-contentTop()-3;
                }
            }
            pendingSetting=null;
        }
        moreScroll = Math.min(Math.max(0, moreScroll), moreMaxScroll);

		addDoneRow(cx, this.height - 40);
		applyMoreScroll();
	}

	/** Viewport bottom in content (virtual) space: the zoom shows less content per screen. */
	private int moreViewportBottom() {
		return contentTop() + (int) Math.floor((screenViewportBottom() - contentTop()) / zoomNow());
	}

	private void applyMoreScroll() {
		int viewportBottom = moreViewportBottom();
		for (var entry : moreBaseY.entrySet()) {
			AbstractWidget w = entry.getKey();
			int y = entry.getValue() - moreScroll;
			w.setY(y);
			// Anything touching the viewport stays live; the scissor clips
			// the part that hangs over the edge.
			w.visible = y + w.getHeight() > contentTop() && y < viewportBottom;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollable(currentTab) && inViewport(mouseX, mouseY) && moreMaxScroll > 0) {
			moreScroll = Math.min(moreMaxScroll, Math.max(0, moreScroll - (int) Math.round(scrollY * 12)));
			scrollMemory.put(sectionKey(), moreScroll);
			setFocused(null);
			applyMoreScroll();
			return true;
		}
		if (currentTab == Tab.ACHIEVEMENTS && achMaxScroll() > 0) {
			achScroll = Math.min(achMaxScroll(), Math.max(0, achScroll - (int) Math.round(scrollY * 12)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private int achMaxScroll() {
		AchLayout l = achLayout();
		return Math.max(0, l.contentH() - (l.viewBottom() - l.viewTop()));
	}

	/** Countdown to the next UTC midnight, when the daily hint refreshes. */
	/** Time left until the next hint, e.g. "5h 3m" (callers add the "Hint in" wording). */
	private static String hintCountdown() {
		long ms = 86400000L - (System.currentTimeMillis() % 86400000L);
		long h = ms / 3600000L;
		long m = (ms % 3600000L) / 60000L;
		if (h > 0) {
			return h + "h " + m + "m";
		}
		if (m > 0) {
			return m + "m";
		}
		return Math.max(1L, ms / 1000L) + "s";
	}

	private Component keybindLabel() {
		if (listeningForKey) {
			return Component.literal("> press a key <");
		}
		return Component.literal("Hotkey: ").append(NucleusKeybinds.boundKeyLabel());
	}

	private Component customKeybindLabel() {
		if (listeningForCustomKey) {
			return Component.literal("> press a key <");
		}
		return Component.literal("Set waypoint hotkey: ").append(NucleusKeybinds.customBoundKeyLabel());
	}

	private Component quickSpinKeyLabel() {
		if (listeningForQuickSpinKey) {
			return Component.literal("> press a key <");
		}
		return Component.literal("Quick-spin hotkey: ").append(NucleusKeybinds.quickSpinBoundKeyLabel());
	}

	private Component removeLastKeybindLabel() {
		if (listeningForRemoveLastKey) {
			return Component.literal("> press a key <");
		}
		return Component.literal("Remove last waypoint hotkey: ").append(NucleusKeybinds.removeLastBoundKeyLabel());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listeningForQuickSpinKey) {
			if (event.key() != InputConstants.KEY_ESCAPE) {
				var key = InputConstants.getKey(event);
				if (key != null && NucleusKeybinds.QUICK_SPIN != null) {
					NucleusKeybinds.QUICK_SPIN.setKey(key);
					net.minecraft.client.KeyMapping.resetMapping();
					Minecraft.getInstance().options.save();
				}
			}
			listeningForQuickSpinKey = false;
			if (quickSpinKeyButton != null) {
				quickSpinKeyButton.setMessage(quickSpinKeyLabel());
			}
			return true;
		}
		if (listeningForKey) {
			if (event.key() == InputConstants.KEY_ESCAPE) {
				listeningForKey = false;
				if (keybindButton != null) {
					keybindButton.setMessage(keybindLabel());
				}
				return true;
			}
			var key = InputConstants.getKey(event);
			if (key != null && NucleusKeybinds.SET_WAYPOINTS != null) {
				NucleusKeybinds.SET_WAYPOINTS.setKey(key);
				net.minecraft.client.KeyMapping.resetMapping();
				Minecraft.getInstance().options.save();
			}
			listeningForKey = false;
			if (keybindButton != null) {
				keybindButton.setMessage(keybindLabel());
			}
			return true;
		}
		if (listeningForCustomKey) {
			if (event.key() == InputConstants.KEY_ESCAPE) {
				listeningForCustomKey = false;
				if (customKeybindButton != null) {
					customKeybindButton.setMessage(customKeybindLabel());
				}
				return true;
			}
			var key = InputConstants.getKey(event);
			if (key != null && NucleusKeybinds.SET_CUSTOM != null) {
				NucleusKeybinds.SET_CUSTOM.setKey(key);
				net.minecraft.client.KeyMapping.resetMapping();
				Minecraft.getInstance().options.save();
			}
			listeningForCustomKey = false;
			if (customKeybindButton != null) {
				customKeybindButton.setMessage(customKeybindLabel());
			}
			return true;
		}
		if (listeningForRemoveLastKey) {
			if (event.key() == InputConstants.KEY_ESCAPE) {
				listeningForRemoveLastKey = false;
				if (removeLastKeybindButton != null) {
					removeLastKeybindButton.setMessage(removeLastKeybindLabel());
				}
				return true;
			}
			var key = InputConstants.getKey(event);
			if (key != null && NucleusKeybinds.REMOVE_LAST_CUSTOM != null) {
				NucleusKeybinds.REMOVE_LAST_CUSTOM.setKey(key);
				net.minecraft.client.KeyMapping.resetMapping();
				Minecraft.getInstance().options.save();
			}
			listeningForRemoveLastKey = false;
			if (removeLastKeybindButton != null) {
				removeLastKeybindButton.setMessage(removeLastKeybindLabel());
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		// Vanilla draws the fixed widgets (tabs, Done row); the zoomed
		// content is drawn below with its own transform and clip.
		withContentHidden(() -> {
			super.extractRenderState(gfx, mouseX, mouseY, partialTick);
			return null;
		});
        dev.purplepad.KcaTheme.heading(gfx,font,TITLE_TEXT,width/2,6,2);
        gfx.centeredText(font,breadcrumb(),width/2,30,0xFF000000|tabAccent(currentTab));
		if (zoomed()) {
			drawZoomedContent(gfx, mouseX, mouseY, partialTick);
		} else if (currentTab == Tab.ACHIEVEMENTS) {
			drawAchievementsTab(gfx, mouseX, mouseY);
		}
		drawTips(gfx, mouseX, mouseY);
	}

	/** Scrolling content: magnified about the content's top centre and clipped to the viewport. */
	private void drawZoomedContent(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick) {
		float z = zoomNow();
		int ox = contentCx();
		boolean in = inViewport(mouseX, mouseY);
		int vmx = in ? (int) Math.floor(toVirtualX(mouseX)) : -10000;
		int vmy = in ? (int) Math.floor(toVirtualY(mouseY)) : -10000;
		gfx.enableScissor(contentX1 + 1, contentTop(), contentX2 - 1, screenViewportBottom());
		var pose = gfx.pose();
		pose.pushMatrix();
		try {
			pose.translate(ox, contentTop());
			pose.scale(z, z);
			pose.translate(-ox, -contentTop());
			for (var child : this.children()) {
				if (isContent(child) && child instanceof AbstractWidget w && w.visible) {
					w.extractRenderState(gfx, vmx, vmy, partialTick);
				}
			}
			drawTabOverlay(gfx);
		} finally {
			pose.popMatrix();
			gfx.disableScissor();
		}
	}

	private static boolean inRect(AbstractWidget w, double x, double y) {
		return x >= w.getX() && x < w.getX() + w.getWidth() && y >= w.getY() && y < w.getY() + w.getHeight();
	}

	/** Hover hint for whatever widget is under the mouse (zoom-aware). */
	private void drawTips(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
		boolean in = zoomed() && inViewport(mouseX, mouseY);
		double vx = toVirtualX(mouseX);
		double vy = toVirtualY(mouseY);
		for (var child : this.children()) {
			if (!(child instanceof AbstractWidget w) || !w.visible) {
				continue;
			}
			Component tip = tips.get(w);
			if (tip == null) {
				continue;
			}
			// Plain bounds (not isMouseOver), so disabled widgets like locked
			// themes still explain themselves.
			boolean over = isContent(w) ? in && inRect(w, vx, vy) : inRect(w, mouseX, mouseY);
			if (!over) {
				continue;
			}
			java.util.List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
			if (tip.getSiblings().isEmpty()) {
				for (String line : tip.getString().split("\n")) {
					lines.addAll(this.font.split(Component.literal(line).withStyle(tip.getStyle()), 220));
				}
			} else {
				lines.addAll(this.font.split(tip, 220));
			}
			gfx.setTooltipForNextFrame(this.font, lines, mouseX, mouseY);
			return;
		}
	}

	/** Per-tab labels, headers and colour swatches drawn over the (zoomed) content. */
	private void drawTabOverlay(GuiGraphicsExtractor gfx) {
		// Section headers are the sidebar tabs now; only the labels and
		// colour swatches that belong to widgets of the shown section draw.
		if (currentTab == Tab.WAYPOINTS) {
			if (outlineBox != null && outlineBox.visible) {
				gfx.text(this.font, "Outline thickness", outlineBox.getX(), outlineBox.getY() - 12, 0xFFFFFFFF, true);
			}
			drawButtonPreview(gfx, color1Button, NucleusMod.CONFIG.waypoint1Color);
			drawButtonPreview(gfx, color2Button, NucleusMod.CONFIG.waypoint2Color);
			drawButtonPreview(gfx, color3Button, NucleusMod.CONFIG.waypoint3Color);
			if (moreCustomNoteY >= 0 && moreCustomNoteY >= sectionStart && moreCustomNoteY < sectionEnd) {
				int ny = moreCustomNoteY - sectionShift - moreScroll;
				if (ny >= contentTop() - 10 && ny <= moreViewportBottom()) {
					gfx.centeredText(this.font, "Uses Jungle Temple display settings.",
						contentCx(), ny, 0xFFAAAAAA);
				}
			}
			drawButtonPreview(gfx, customColorButton, NucleusMod.CONFIG.customWaypointColor);
		} else if (currentTab == Tab.FEATURES) {
			drawButtonPreview(gfx, divanChestColorButton, NucleusMod.CONFIG.divanChestColor);
			if (themesLabelY >= 0 && themesLabelY >= sectionStart && themesLabelY < sectionEnd) {
				int ly = themesLabelY - sectionShift - moreScroll;
				int pct = NucleusTheme.achievementPct();
				gfx.text(this.font, "Themes \u00a78\u2022 \u00a77" + pct + "% of achievements unlocked",
					contentCx() - 150, ly, 0xFFE8E8F0, true);
			}
			for (var entry : themeSwatches.entrySet()) {
				Button b = entry.getKey();
				if (!b.visible) {
					continue;
				}
				// Three little colour chips: the theme's light bands.
				int[] bands = entry.getValue().bands();
				int sx = b.getX() + b.getWidth() - 6 - 3 * 7;
				for (int k = 0; k < 3; k++) {
					int c = bands[k % bands.length];
					int x1 = sx + k * 7;
					gfx.fill(x1, b.getY() + 6, x1 + 6, b.getY() + 14, (b.active ? 0xFF000000 : 0x60000000) | c);
				}
			}
		} else if (currentTab == Tab.MISC) {
            titleFields.forEach((label,box)->oddsLabel(gfx,box,label));
			oddsLabel(gfx, oddsJaderaldBox, "Jaderald 1 in");
			oddsLabel(gfx, oddsFragmentBox, "Fragment 1 in");
			oddsLabel(gfx, oddsMixtureBox, "Mixture 1 in");
			oddsLabel(gfx, oddsFlawlessBox, "Flawless 1 in");
        }else if(currentTab==Tab.SOUNDS){
            gfx.text(font,"Sound triggers",contentCx()-150,catTriggersY-sectionShift-moreScroll,0xFFE8E8F0,true);
			if (soundPacksLabelY >= sectionStart && soundPacksLabelY < sectionEnd) {
				int ly = soundPacksLabelY - sectionShift - moreScroll;
				gfx.text(this.font, "Gambling sound packs \u00a78\u2022 \u00a77wheel clicks and roll sounds",
					contentCx() - 150, ly, 0xFFE8E8F0, true);
			}
		}
	}

	// ------------------------------------------------------------ achievements tab

	/** Section order on the tab; {@link #achCategory} sorts every entry into one. */
	private static final String[] ACH_TIER_SHORT = { "Easy", "Medium", "Chall.", "Hard", "V.Hard", "Imposs." };
	private static final int[] ACH_ORDER = {9,16,11,12,13,14,15,10,0,1,2,3,4,5,7,8};
	private static final String[] ACH_CATS = {
		"Runs", "Speed", "Loot", "Wheel", "Streaks", "Bal", "", "Mines", "Secrets", "Dungeon runs", "General", "Dungeon secrets", "Dungeon rooms", "Catacombs floors", "Master Mode floors", "Storm", "Dungeon times"
	};

	private static int achCategory(Achievements.Entry e) {
		String id = e.id();
        if(id.startsWith("kca_d_")){
            if(id.startsWith("kca_d_time_") || id.startsWith("kca_d_split_"))return 16;
            if(id.startsWith("kca_d_floor_"))return id.startsWith("kca_d_floor_M")?14:13;
            if(id.contains("secret"))return 11;
            if(id.contains("room") || id.contains("solo") || id.contains("shared"))return 12;
            if(id.equals("kca_d_storm"))return 15;
            return 9;
        }
        if(id.startsWith("kca_ui_"))return 10;
		if (e.kind() == Achievements.Kind.COUNT) {
			return 0;
		}
		if (e.kind() == Achievements.Kind.TIME || id.equals("slow") || id.equals("cheater")) {
			return 1;
		}
		if (id.equals("nightshift")) {
			return 0;
		}
		if (id.equals("ouch")) {
			return 3;
		}
		if (id.startsWith("g_") || id.equals("double") || id.equals("shouldve") || id.equals("good")
			|| id.equals("rngmeter") || id.equals("chestplate9") || id.equals("sword2")) {
			return 2;
		}
		if (id.startsWith("pow") || id.startsWith("luck")) {
			return 3;
		}
		if (id.startsWith("b2b")) {
			return 4;
		}
		if (id.startsWith("bal_") || id.equals("fistbal")) {
			return 5;
		}
		if (id.equals("dopamine") || id.equals("blimey")) {
			return 7;
		}
		return 8;
	}

	// ------------------------------------------------------------ achievement icons

	private static final java.util.Map<String, net.minecraft.world.item.ItemStack> ACH_ICONS = new java.util.HashMap<>();

	private static net.minecraft.world.item.ItemStack vanilla(net.minecraft.world.item.Item item) {
		return ACH_ICONS.computeIfAbsent("v:" + item, k -> new net.minecraft.world.item.ItemStack(item));
	}

	/** Paper wearing a Hypixel pack model (the real SkyBlock look while the server pack is loaded). */
	private static net.minecraft.world.item.ItemStack packItem(String model) {
		return ACH_ICONS.computeIfAbsent("m:" + model, k -> {
			net.minecraft.world.item.ItemStack st = new net.minecraft.world.item.ItemStack(
				net.minecraft.world.item.Items.PAPER);
			st.set(net.minecraft.core.component.DataComponents.ITEM_MODEL,
				net.minecraft.resources.Identifier.fromNamespaceAndPath("hypixel_skyblock", model));
			return st;
		});
	}

	/** A SkyBlock head (skin hash from the NEU item repo). */
	private static net.minecraft.world.item.ItemStack skyHead(String key, String hash, String uuid, boolean slim) {
		return ACH_ICONS.computeIfAbsent("h:" + key, k -> DyeCelebration.head(hash, uuid, key, slim));
	}

	/**
	 * The pack-model item when Hypixel's resource pack is loaded, else a
	 * vanilla look-alike (a missing model would draw the purple cube).
	 */
	private static net.minecraft.world.item.ItemStack packOr(String model, net.minecraft.world.item.Item fallback) {
		net.minecraft.world.item.ItemStack st = packItem(model);
		try {
			var id = st.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
			if (id != null && !(Minecraft.getInstance().getModelManager().getItemModel(id)
				instanceof net.minecraft.client.renderer.item.MissingItemModel)) {
				return st;
			}
		} catch (Exception ignored) {
		}
		return vanilla(fallback);
	}

	private static net.minecraft.world.item.ItemStack alloyIcon() {
		return packOr("item/uncategorized/divans_alloy", net.minecraft.world.item.Items.NETHERITE_INGOT);
	}

	private static net.minecraft.world.item.ItemStack balIcon() {
		// NEU BAL;3 (Epic Bal pet).
		return skyHead("Bal", "c469ba2047122e0a2de3c7437ad3dd5d31f1ac2d27abde9f8841e1d92a8c5b75",
			"704396fb-e7f0-3a33-bb57-d9bd34d6c7c9", true);
	}

	private static net.minecraft.world.item.ItemStack recallIcon() {
		// NEU RECALL_POTION.
		return skyHead("Recall Potion", "1af8151deb39553292b9a69674832dda8001780ca98180a06eaf9b368930b5bb",
			"e995fdbd-7819-36bc-a5e4-271e3f4eddcc", false);
	}

	/** Icon for one achievement: the actual item it is about wherever there is one. */
	private static net.minecraft.world.item.ItemStack achIcon(Achievements.Entry e) {
		String id = e.id();
		if (e.kind() == Achievements.Kind.COUNT) {
			return vanilla(net.minecraft.world.item.Items.GOLDEN_BOOTS);
		}
		if (e.kind() == Achievements.Kind.TIME) {
			return vanilla(net.minecraft.world.item.Items.CLOCK);
		}
		return switch (id) {
			case "slow" -> vanilla(net.minecraft.world.item.Items.COBWEB);
			case "cheater" -> vanilla(net.minecraft.world.item.Items.BARRIER);
			case "g_fragment", "b2b_frag", "b2b2b_frag" ->
				JackpotAnimation.DropType.DIVAN_FRAGMENT.stack();
			case "g_jaderald", "double", "b2b_jad", "b2b2b_jad" ->
				packOr("item/reforge_stones/jaderald", net.minecraft.world.item.Items.EMERALD);
			case "b2b_mix", "b2b2b_mix" ->
				packOr("item/uncategorized/gemstone_mixture", net.minecraft.world.item.Items.AMETHYST_SHARD);
			case "b2b_flawless" -> packOr("item/collections/gemstone/jade/flawless_jade_gem",
				net.minecraft.world.item.Items.EMERALD);
			case "b2b_pearl" ->
				packOr("item/power_stones/precious_pearl", net.minecraft.world.item.Items.ENDER_PEARL);
			case "g_claw", "b2b_claw" -> JackpotAnimation.DropType.QUICK_CLAW.stack();
			case "g_jade", "showoff" -> JackpotAnimation.DropType.JADE_DYE.stack();
			case "b2b_recall", "terraria" -> recallIcon();
			case "pow100", "pow0" -> vanilla(net.minecraft.world.item.Items.BLAZE_POWDER);
			case "luck100", "luck0" -> vanilla(net.minecraft.world.item.Items.RABBIT_FOOT);
			case "dopamine" -> DyeCelebration.fakeDye();
			case "blimey" -> vanilla(net.minecraft.world.item.Items.DIAMOND_PICKAXE);
			case "why" -> vanilla(net.minecraft.world.item.Items.BLUE_EGG);
			case "boomer" -> vanilla(net.minecraft.world.item.Items.COMPARATOR);
			case "cosmixi" -> vanilla(net.minecraft.world.item.Items.PLAYER_HEAD);
			case "larper" -> skyHead("Royal Pigeon", "c429253b8ed84abcbbd39049cba928cefc3f08b7280d249987402dc7ab2f0a25",
				"abd5bd6b-e321-3d53-b935-4e8c13e23c2d", true);
			case "nt" -> vanilla(net.minecraft.world.item.Items.NETHER_STAR);
			case "logo10", "logo100", "logo1000" -> vanilla(net.minecraft.world.item.Items.STONE_BUTTON);
			case "kaboom" -> vanilla(net.minecraft.world.item.Items.TNT);
			case "phoenix" -> vanilla(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
			case "decorator" -> vanilla(net.minecraft.world.item.Items.PAINTING);
			case "seasons" -> vanilla(net.minecraft.world.item.Items.SNOWBALL);
			case "nightshift" -> vanilla(net.minecraft.world.item.Items.BED.pick(net.minecraft.world.item.DyeColor.BLACK));
			case "ouch" -> alloyIcon();
			default -> {
				if (id.startsWith("bal_") || id.equals("fistbal")) {
					yield balIcon();
				}
				if (id.startsWith("g_") || id.equals("shouldve") || id.equals("good") || id.equals("rngmeter")
					|| id.equals("chestplate9") || id.equals("sword2") || id.equals("luckier")
					|| id.equals("b2b_alloy")) {
					yield alloyIcon();
				}
				yield achCatIcon(achCategory(e));
			}
		};
	}

	/** Section header icon. */
	private static net.minecraft.world.item.ItemStack achCatIcon(int cat) {
		return switch (cat) {
			case 0 -> vanilla(net.minecraft.world.item.Items.GOLDEN_BOOTS);
			case 1 -> vanilla(net.minecraft.world.item.Items.CLOCK);
			case 2 -> alloyIcon();
			case 3 -> vanilla(net.minecraft.world.item.Items.NETHER_STAR);
			case 4 -> vanilla(net.minecraft.world.item.Items.FIRE_CHARGE);
			case 5 -> balIcon();
			case 6 -> vanilla(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE);
			case 7 -> vanilla(net.minecraft.world.item.Items.GOLDEN_PICKAXE);
			default -> vanilla(net.minecraft.world.item.Items.NAME_TAG);
		};
	}

	private static boolean achDone(Achievements.Entry e) {
		return NucleusMod.SPEEDRUN.unlockedFun.contains(e.id());
	}

	/** Event achievements stay a mystery until unlocked or hinted; run/time ones show their goal. */
	private static boolean achHidden(Achievements.Entry e) {
		boolean secret=e.id().startsWith("kca_n_time") ||
            (!e.id().startsWith("kca_") && e.kind()==Achievements.Kind.EVENT && !Achievements.isLogoEntry(e));
        return secret && !achDone(e)
			&& !NucleusMod.SPEEDRUN.revealedHints.contains(e.id());
	}

	private static boolean achPasses(Achievements.Entry e) {
		boolean done = achDone(e);
		return switch (achFilter) {
			case -1 -> true;
			case -2 -> done;
			case -3 -> !done;
            case -4 -> achHidden(e);
            case -5 -> !e.id().startsWith("kca_d_") && NucleusMod.SPEEDRUN.revealedHints.contains(e.id());
			default -> e.tier() == achFilter;
		};
	}

	/** 0..1 progress for run-count / best-time goals (-1 for one-off events). */
	private static float achProgress(Achievements.Entry e) {
		if (achDone(e)) {
			return 1f;
		}
		long counter = Achievements.counterValue(e);
		if (counter >= 0) {
			return Math.min(1f, counter / (float) Math.max(1L, e.threshold()));
		}
		if (e.kind() == Achievements.Kind.COUNT) {
			return Math.min(1f, NucleusMod.SPEEDRUN.completedRuns / (float) Math.max(1L, e.threshold()));
		}
		if (e.kind() == Achievements.Kind.TIME) {
			long best = NucleusMod.SPEEDRUN.bestTotalMs;
			if (best <= 0L) {
				return 0f;
			}
			return Math.min(1f, e.threshold() / (float) best);
		}
		return -1f;
	}

	private record AchChip(int x1, int y1, int x2, int y2, int filter, String label, int rgb) {
	}

	private record AchSection(int cat, int y, int done, int total) {
	}

	/** Card rect in content space (y before scrolling). */
	private record AchCard(Achievements.Entry e, int x1, int y1, int x2, int y2) {
	}

	private record AchLayout(java.util.List<AchChip> chips, int barX1, int barX2, int barY, int viewTop,
		int viewBottom, int gridX1, int gridX2, java.util.List<AchSection> sections, java.util.List<AchCard> cards,
		int contentH,int unlockedTotal) {
	}

	private static final int ACH_CARD_H = 29;
	private static final int ACH_GAP = 4;
	private static final int ACH_SECTION_H = 18;

	/** One layout pass shared by drawing, scrolling and clicking. */
	private AchLayout cachedAchievements;
    private long nextAchievementRefresh;
    private int cachedUnlockCount,cachedHintCount;
    private AchLayout achLayout(){
        var store=NucleusMod.SPEEDRUN;
        int unlocked=store.unlockedFun.size(),hints=store.revealedHints.size();
        long now=System.nanoTime();
        if(cachedAchievements==null || unlocked!=cachedUnlockCount || hints!=cachedHintCount || now-nextAchievementRefresh>=0){
            cachedAchievements=buildAchievementLayout();
            cachedUnlockCount=unlocked;cachedHintCount=hints;
            nextAchievementRefresh=now+1_000_000_000L;
        }
        return cachedAchievements;
    }
    private AchLayout buildAchievementLayout() {
		int cx = contentCx();
		int avail = Math.max(160, contentX2 - contentX1 - 20);
		int cols = avail >= 2 * 200 + ACH_GAP ? 2 : 1;
		int cardW = Math.min(cols == 2 ? 250 : 380, (avail - (cols - 1) * ACH_GAP) / cols);
		int gridW = cols * cardW + (cols - 1) * ACH_GAP;
		int gridX1 = cx - gridW / 2;
		int gridX2 = gridX1 + gridW;

		// Filter chips (wrap onto more rows when the panel is narrow).
		java.util.List<AchChip> chips = new java.util.ArrayList<>();
		int[] filters = achBranch==1 ? new int[]{-6,-1,-2,-3,0,1,2,3,4,5} : new int[]{-6,-1,-2,-3,-4,-5,0,1,2,3,4,5};
		java.util.List<String> labels = new java.util.ArrayList<>();
		java.util.List<Integer> colors = new java.util.ArrayList<>();
		int fullW = 0;
		for (int f : filters) {
			String label;
			int rgb;
			if(f==-6){label=ACH_BRANCHES[achBranch]+" >";rgb=0xFFD6E3;}
            else if(f==-4 || f==-5){label=f==-4?"Hidden":"Hinted";rgb=0xC9A6D7;}
            else if (f == -1) {
				label = "All";
				rgb = tabAccent(Tab.ACHIEVEMENTS);
			} else if (f == -2) {
				label = "Unlocked";
				rgb = 0x62E06A;
			} else if (f == -3) {
				label = "Locked";
				rgb = 0xB0B0C0;
			} else {
				label = ACH_TIER_SHORT[f];
				rgb = f == Achievements.IMPOSSIBLE ? 0x9A9AAA : Achievements.TIER_COLORS[f] & 0xFFFFFF;
			}
			labels.add(label);
			colors.add(rgb);
			fullW += this.font.width(label) + 6 + 2;
		}
		int ax1 = cx - avail / 2;
		int ax2 = ax1 + avail;
		int x = ax1 + Math.max(0, (avail - fullW + 2) / 2);
		int y = 96;
		for (int i = 0; i < filters.length; i++) {
			int w = this.font.width(labels.get(i)) + 6;
			if (x + w > ax2 && x > ax1) {
				x = ax1;
				y += 15;
			}
			chips.add(new AchChip(x, y, x + w, y + 12, filters[i], labels.get(i), colors.get(i)));
			x += w + 2;
		}
		int viewTop = y + 12 + 7;
		int viewBottom = this.height - 50;

		// Sections in a fixed order, cards easiest tier first.
		java.util.List<AchSection> sections = new java.util.ArrayList<>();
		java.util.List<AchCard> cards = new java.util.ArrayList<>();
		int cy = 0;
		for (int cat : ACH_ORDER) {
            if(achBranch==1&&(cat<9||cat==10) || achBranch==2&&cat>=9 || achBranch==3&&cat!=10)continue;
			java.util.List<Achievements.Entry> in = new java.util.ArrayList<>();
			int done = 0;
			int total = 0;
			for (Achievements.Entry e : Achievements.ENTRIES) {
				if (achCategory(e) != cat) {
					continue;
				}
				total++;
				if (achDone(e)) {
					done++;
				}
				if (achPasses(e)) {
					in.add(e);
				}
			}
			if (in.isEmpty()) {
				continue;
			}
			in.sort(java.util.Comparator.comparingInt(Achievements.Entry::tier));
			sections.add(new AchSection(cat, cy, done, total));
			cy += ACH_SECTION_H;
			for (int i = 0; i < in.size(); i++) {
				int col = i % cols;
				int row = i / cols;
				int x1 = gridX1 + col * (cardW + ACH_GAP);
				int y1 = cy + row * (ACH_CARD_H + ACH_GAP);
				cards.add(new AchCard(in.get(i), x1, y1, x1 + cardW, y1 + ACH_CARD_H));
			}
			int rows = (in.size() + cols - 1) / cols;
			cy += rows * (ACH_CARD_H + ACH_GAP) + 6;
		}
		int unlockedTotal=0;
        for(var entry:Achievements.ENTRIES)if(achDone(entry))unlockedTotal++;
		return new AchLayout(chips, gridX1, gridX2, 89, viewTop, viewBottom, gridX1, gridX2, sections, cards, cy, unlockedTotal);
	}

	private static long hintTodayUtc() {
		return System.currentTimeMillis() / 86400000L;
	}

	private static boolean hintAvailable() {
		return NucleusMod.SPEEDRUN.hintDay != hintTodayUtc();
	}

	/** Achievements tab: overall bar, filter chips, then sections of two-column cards. */
	private void drawAchievementsTab(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
		AchLayout l = achLayout();
		int accent = tabAccent(currentTab);
		int cx = contentCx();
		int total = Achievements.ENTRIES.length;
		int doneCount=l.unlockedTotal();
        NucleusUi.header(gfx, this.font, "Achievements  "+doneCount+"/"+total+(achBranch==1?"":"   ·   "+(hintAvailable()?"Hint ready":"Hint in "+hintCountdown())), cx, 76,
			(l.gridX2() - l.gridX1()) / 2, accent);

		// Overall progress bar.
		int bx1 = l.barX1();
		int bx2 = l.barX2();
		int by = l.barY();
		gfx.fill(bx1, by, bx2, by + 3, 0xFF22222E);
		int fillX = bx1 + Math.round((bx2 - bx1) * (doneCount / (float) Math.max(1, total)));
		gfx.fillGradient(bx1, by, fillX, by + 3, 0xFF000000 | NucleusUi.lighten(accent, 0.2f),
			0xFF000000 | NucleusUi.darken(accent, 0.6f));

		// Filter chips.
		for (AchChip c : l.chips()) {
			boolean sel = c.filter() == achFilter || c.filter()==-6 && achBranch!=0;
			boolean hov = mouseX >= c.x1() && mouseX < c.x2() && mouseY >= c.y1() && mouseY < c.y2();
			int bg = sel ? 0xE0000000 | NucleusUi.darken(c.rgb(), 0.35f) : hov ? 0xC0262636 : 0xA0161622;
			gfx.fill(c.x1(), c.y1(), c.x2(), c.y2(), bg);
			int edge = sel ? 0xFF000000 | c.rgb() : hov ? 0x80FFFFFF : 0x30FFFFFF;
			gfx.fill(c.x1(), c.y2() - 1, c.x2(), c.y2(), edge);
			int tc = sel ? 0xFFFFFFFF : NucleusUi.mix(0xFF000000 | NucleusUi.darken(c.rgb(), 0.75f), 0xFFFFFFFF, hov ? 0.4f : 0f);
			gfx.text(this.font, c.label(), c.x1() + 3, c.y1() + 2, tc, true);
		}

		for (AchChip c : l.chips()) {
			if (c.filter() >= 0 && mouseX >= c.x1() && mouseX < c.x2() && mouseY >= c.y1() && mouseY < c.y2()) {
				int all = 0;
				int got = 0;
				for (Achievements.Entry e : Achievements.ENTRIES) {
					if (e.tier() == c.filter()) {
						all++;
						got += achDone(e) ? 1 : 0;
					}
				}
				gfx.setTooltipForNextFrame(this.font, Component.literal(Achievements.TIER_NAMES[c.filter()] + ": "
					+ got + "/" + all + " unlocked").withColor(c.rgb()), mouseX, mouseY);
			}
		}

		int scroll = Math.min(achScroll, Math.max(0, l.contentH() - (l.viewBottom() - l.viewTop())));
		achScroll = scroll;
		int top = l.viewTop() - scroll;
		boolean inView = mouseY >= l.viewTop() && mouseY < l.viewBottom();
		AchCard hovered = null;
		gfx.enableScissor(contentX1, l.viewTop(), contentX2, l.viewBottom());
		try {
			if (l.cards().isEmpty()) {
				gfx.centeredText(this.font, "Nothing here yet.", cx, l.viewTop() + 20, 0xFF777788);
			}
			for (AchSection s : l.sections()) {
				int sy = top + s.y();
				if (sy > l.viewBottom() || sy + ACH_SECTION_H < l.viewTop()) {
					continue;
				}
				drawAchSection(gfx, s, l.gridX1(), l.gridX2(), sy, accent);
			}
			for (AchCard c : l.cards()) {
				int y1 = top + c.y1();
				int y2 = top + c.y2();
				if (y1 > l.viewBottom() || y2 < l.viewTop()) {
					continue;
				}
				boolean hov = inView && mouseX >= c.x1() && mouseX < c.x2() && mouseY >= y1 && mouseY < y2;
				if (hov) {
					hovered = c;
				}
				drawAchCard(gfx, c.e(), c.x1(), y1, c.x2(), y2, hov, mouseX, mouseY);
			}
		} finally {
			gfx.disableScissor();
		}
		if (scroll < l.contentH() - (l.viewBottom() - l.viewTop())) {
			drawScrollHint(gfx);
		}
		if (hovered != null) {
			gfx.setComponentTooltipForNextFrame(this.font, achTooltip(hovered.e()), mouseX, mouseY);
		}
	}

	private static final net.minecraft.resources.Identifier ACH_ALLOY = net.minecraft.resources.Identifier.fromNamespaceAndPath("kca","textures/alloy.png");
    private static final net.minecraft.resources.Identifier ACH_SWORD = net.minecraft.resources.Identifier.fromNamespaceAndPath("kca","textures/hyperion.png");
    private static final net.minecraft.resources.Identifier ACH_GENERAL = dev.purplepad.KcaTheme.SETTINGS_ICON;
    private void drawAchievementIcon(GuiGraphicsExtractor g,int category,Achievements.Entry entry,int x,int y){
        // Item stacks need world-bound components. The packaged branch icons also work on the title screen.
        if(category>=9 || minecraft.level==null){
            var texture=category==10?ACH_GENERAL:category>=9?ACH_SWORD:ACH_ALLOY;
            g.blit(texture,x,y,x+16,y+16,0,1,0,texture==ACH_ALLOY?.25f:1f);
        }else g.item(entry==null?achCatIcon(category):achIcon(entry),x,y);
    }
    private void drawAchSection(GuiGraphicsExtractor gfx, AchSection s, int x1, int x2, int y, int accent) {
		var pose = gfx.pose();
		pose.pushMatrix();
		try {
			pose.translate(x1, y + 2);
			pose.scale(0.75f, 0.75f);
			drawAchievementIcon(gfx,s.cat(),null,0,0);
		} finally {
			pose.popMatrix();
		}
		String title = ACH_CATS[s.cat()].toUpperCase(java.util.Locale.ROOT);
		int tx = x1 + 16;
		gfx.text(this.font, title, tx, y + 4, 0xFF000000 | NucleusUi.lighten(accent, 0.35f), true);
		String count = s.done() + "/" + s.total();
		int cx = tx + this.font.width(title) + 5;
		boolean all = s.done() == s.total();
		gfx.text(this.font, count, cx, y + 4, all ? 0xFF62E06A : 0xFF8A8AA0, true);
		int lx = cx + this.font.width(count) + 6;
		if (lx < x2) {
			gfx.fill(lx, y + 8, x2, y + 9, 0x50000000 | accent);
		}
	}

	/**
	 * Achievement name in its tier gradient, one glyph at a time with a
	 * plain ARGB colour each (a multi-part styled Component could come out
	 * with an odd letter here and there).
	 */
	private void drawGradientName(GuiGraphicsExtractor gfx, Achievements.Entry e, boolean done, int x, int y) {
		String text = e.name();
		int[] cols = Achievements.nameColors(e, done);
		boolean edges = done && Achievements.hasGlitchEdges(e);
		int edgeCol = 0xFF000000 | (Achievements.TIER_COLORS[e.tier()] & 0xFFFFFF);
		Component glitch = Component.literal("#").withStyle(net.minecraft.ChatFormatting.OBFUSCATED);
		int px = x;
		if (edges) {
			gfx.text(this.font, glitch, px, y, edgeCol, true);
			px += this.font.width("# ");
		}
		for (int i = 0; i < text.length(); i++) {
			String ch = String.valueOf(text.charAt(i));
			gfx.text(this.font, ch, px, y, 0xFF000000 | cols[i], true);
			px += this.font.width(ch);
		}
		if (edges) {
			px += this.font.width(" ");
			gfx.text(this.font, glitch, px, y, edgeCol, true);
		}
	}

	private void drawAchCard(GuiGraphicsExtractor gfx, Achievements.Entry e, int x1, int y1, int x2, int y2,
		boolean hover, int mouseX, int mouseY) {
		boolean done = achDone(e);
		boolean hidden = achHidden(e);
		int tier = e.tier();
		int tierRgb = tier == Achievements.IMPOSSIBLE ? 0xB070FF : Achievements.TIER_COLORS[tier] & 0xFFFFFF;

		// Card body: tier-tinted glass when unlocked, flat dark when locked.
		if (done) {
			gfx.fillGradient(x1, y1, x2, y2, 0xD0000000 | NucleusUi.darken(tierRgb, 0.22f), 0xD0101018);
		} else {
			gfx.fill(x1, y1, x2, y2, 0xC0101018);
		}
		int edge = hover ? (done ? 0xFF000000 | tierRgb : 0x90FFFFFF) : (done ? 0x90000000 | tierRgb : 0x28FFFFFF);
		gfx.fill(x1, y1, x2, y1 + 1, edge);
		gfx.fill(x1, y2 - 1, x2, y2, done ? 0x50000000 | tierRgb : 0x18FFFFFF);
		gfx.fill(x1, y1, x1 + 1, y2, edge);
		gfx.fill(x2 - 1, y1, x2, y2, edge);
		gfx.fill(x1 + 1, y1 + 1, x1 + 3, y2 - 1, 0xFF000000 | (done ? tierRgb : NucleusUi.darken(tierRgb, 0.35f)));

		// Icon: the section's item once unlocked, a padlock until then.
		int ix = x1 + 7;
		int iy = y1 + (y2 - y1 - 16) / 2;
		if (done) {
			drawAchievementIcon(gfx,achCategory(e),e,ix,iy);
		} else {
			drawPadlock(gfx, ix, iy, hover ? 0xFF9A9AAE : 0xFF6A6A7C);
		}

		int tx = x1 + 28;
		int right = x2 - 6;

		// Tier tag, top right.
		String tag = Achievements.TIER_NAMES[tier].toUpperCase(java.util.Locale.ROOT);
		int tagW = this.font.width(tag);
		var pose = gfx.pose();
		pose.pushMatrix();
		try {
			pose.translate(right - tagW * 0.75f, y1 + 4);
			pose.scale(0.75f, 0.75f);
			gfx.text(this.font, tag, 0, 0, 0xFF000000 | (done ? tierRgb : NucleusUi.darken(tierRgb, 0.55f)), false);
		} finally {
			pose.popMatrix();
		}
		int nameMax = right - (int) (tagW * 0.75f) - 6 - tx;

		// Name: tier gradient when unlocked, dim when locked; plain text if it won't fit.
		Component name = done ? Achievements.titleComponent(e, false) : Achievements.lockedTitleComponent(e);
		if(hidden){gfx.text(font,"???",tx,y1+4,0xFFADA3B7,true);}
        else if (this.font.width(name) <= nameMax) {
			drawGradientName(gfx, e, done, tx, y1 + 4);
		} else {
			int nc = done ? 0xFF000000 | NucleusUi.lighten(tierRgb, 0.2f) : 0xFF8A8A9A;
			gfx.text(this.font, NucleusUi.ellipsize(this.font, e.name(), nameMax), tx, y1 + 4, nc, true);
		}

		// Second line: progress, description, or the daily hint button.
		float prog = achProgress(e);
		if (!done && !hidden && prog >= 0f) {
			String p = Achievements.progressText(e);
			gfx.text(this.font, NucleusUi.ellipsize(this.font, p, right - tx), tx, y1 + 15, 0xFFB0B0C0, true);
			gfx.fill(tx, y1 + 24, right, y1 + 26, 0xFF22222E);
			gfx.fill(tx, y1 + 24, tx + Math.round((right - tx) * prog), y1 + 26, 0xFF000000 | tierRgb);
		} else if (hidden) {
			gfx.text(this.font, "???", tx, y1 + 16, 0xFF55556A, true);
			int[] r = achHintRect(x2, y1);
			boolean avail = hintAvailable();
			boolean hov = avail && mouseX >= r[0] && mouseX < r[2] && mouseY >= r[1] && mouseY < r[3];
			String label = avail ? "Reveal hint" : "Hint in " + hintCountdown();
			int lw = this.font.width(label);
			int bx1 = r[2] - lw - 8;
			gfx.fill(bx1, r[1], r[2], r[3], avail ? (hov ? 0xFF4A4420 : 0xFF2E2A16) : 0xFF1A1A22);
			gfx.fill(bx1, r[3] - 1, r[2], r[3], avail ? 0xFFFFD34A : 0x30FFFFFF);
			gfx.text(this.font, label, bx1 + 4, r[1] + 2, avail ? 0xFFFFE27A : 0xFF6A6A7C, false);
		} else {
			int dc = done ? 0xFFB8B8C8 : 0xFF8A8A9A;
			int descMax = right - tx - (done ? 12 : 0);
			gfx.text(this.font, NucleusUi.ellipsize(this.font, e.desc(), descMax), tx, y1 + 16, dc, false);
		}
		if (done) {
			String check = "✔";
			gfx.text(this.font, check, right - this.font.width(check), y1 + 16, 0xFF62E06A, true);
		}
	}

	/** Hint button rect {x1, y1, x2, y2} for a card whose right edge is x2 (button grows left to fit). */
	private int[] achHintRect(int cardX2, int cardY1) {
		String label = hintAvailable() ? "Reveal hint" : "Hint in " + hintCountdown();
		int x2 = cardX2 - 6;
		return new int[] { x2 - this.font.width(label) - 8, cardY1 + 14, x2, cardY1 + 25 };
	}

	private static void drawPadlock(GuiGraphicsExtractor gfx, int x, int y, int color) {
		gfx.fill(x + 5, y + 2, x + 11, y + 3, color);
		gfx.fill(x + 4, y + 3, x + 5, y + 8, color);
		gfx.fill(x + 11, y + 3, x + 12, y + 8, color);
		gfx.fill(x + 3, y + 8, x + 13, y + 15, color);
		gfx.fill(x + 7, y + 10, x + 9, y + 13, 0xFF101018);
	}

	private java.util.List<Component> achTooltip(Achievements.Entry e) {
		java.util.List<Component> out = new java.util.ArrayList<>();
		boolean done = achDone(e);
		out.add(done ? Achievements.titleComponent(e, false) : Component.literal(achHidden(e)?"???":e.name()).withColor(0xC0C0D0));
		int tierRgb = e.tier() == Achievements.IMPOSSIBLE ? 0xB070FF : Achievements.TIER_COLORS[e.tier()] & 0xFFFFFF;
		out.add(Component.literal(Achievements.TIER_NAMES[e.tier()]).withColor(tierRgb)
			.append(Component.literal("  ·  " + ACH_CATS[achCategory(e)]).withColor(0x8A8AA0)));
		if (achHidden(e)) {
			out.add(Component.literal("Hidden until you unlock it or reveal a hint.").withColor(0x8A8AA0));
			out.add(Component.literal(hintAvailable() ? "You have a hint available today."
				: "Next hint in " + hintCountdown() + ".").withColor(0xFFE27A));
		} else {
			out.add(Component.literal(e.desc()).withColor(0xD0D0E0));
		}
		if (!achHidden(e) && (e.kind() != Achievements.Kind.EVENT || Achievements.counterValue(e) >= 0)) {
			out.add(Component.literal(Achievements.progressText(e)).withColor(0xA0A0B4));
		}
		out.add(done ? Component.literal("✔ Unlocked").withColor(0x62E06A)
			: Component.literal("Locked").withColor(0x8A8AA0));
		return out;
	}

	/** Chip and hint clicks for the achievements tab. */
	private boolean achClick(double mx, double my) {
		AchLayout l = achLayout();
		for (AchChip c : l.chips()) {
			if (mx >= c.x1() && mx < c.x2() && my >= c.y1() && my < c.y2()) {
				if(c.filter()==-6){achBranch=(achBranch+1)%ACH_BRANCHES.length;if(achBranch==1 && (achFilter==-4||achFilter==-5))achFilter=-1;cachedAchievements=null;achScroll=0;playClick();return true;}
                if (achFilter != c.filter()) {
					achFilter = c.filter();
                    cachedAchievements=null;
					achScroll = 0;
					playClick();
				}
				return true;
			}
		}
		if (my < l.viewTop() || my >= l.viewBottom()) {
			return false;
		}
		int top = l.viewTop() - achScroll;
		for (AchCard c : l.cards()) {
			if (!achHidden(c.e())) {
				continue;
			}
			int[] r = achHintRect(c.x2(), top + c.y1());
			if (mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]) {
				if (hintAvailable()) {
					NucleusMod.SPEEDRUN.revealedHints.add(c.e().id());
					NucleusMod.SPEEDRUN.hintDay = hintTodayUtc();
					NucleusMod.SPEEDRUN.save();
					playClick();
				}
				return true;
			}
		}
		return false;
	}

	private void playClick() {
		try {
			Minecraft.getInstance().getSoundManager().play(
				net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
		} catch (Exception ignored) {
		}
	}


	/** "More below": a dark fade over the viewport's bottom edge and a pulsing arrow in the panel corner. */
	private void drawScrollHint(GuiGraphicsExtractor gfx) {
		int bottom = this.height - 47;
		gfx.fillGradient(contentX1 + 1, bottom - 12, contentX2 - 1, bottom, 0x00000000, 0xC00C0C16);
		float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 220.0));
		int a = (int) (0x70 + 0x8F * pulse);
		gfx.text(this.font, "▼", contentX2 - 12, bottom - 10 + (int) (pulse * 2), (a << 24) | tabAccent(currentTab), true);
	}

	private void drawButtonPreview(GuiGraphicsExtractor gfx, Button button, int color) {
		if (button == null || !button.visible) {
			return;
		}
		int x2 = button.getX() + button.getWidth() - 3;
		int x1 = x2 - 16;
		int y1 = button.getY() + 3;
		int y2 = y1 + 14;
		gfx.fill(x1, y1, x2, y2, color);
	}

	/** Parses a 1-in-N odds field, falling back on bad input. */
	private static int parseOdds(String raw, int fallback) {
		try {
			int v = Integer.parseInt(raw.trim());
			if (v >= 1 && v <= 1000000) {
				return v;
			}
		} catch (NumberFormatException ignored) {
		}
		return fallback;
	}

	private void saveAndClose() {saveFields();Minecraft.getInstance().gui.setScreen(parent);}
    private void saveFields() {
		try {
			if (outlineBox != null) {
				float w = Float.parseFloat(outlineBox.getValue().trim());
				if (w >= 0.5f && w <= 10.0f) {
					NucleusMod.CONFIG.waypointOutlineWidth = w;
				}
			}
		} catch (NumberFormatException ignored) {
		}
		try {
			if (oddsJaderaldBox != null) {
				NucleusMod.CONFIG.jackpotOddsJaderald = parseOdds(oddsJaderaldBox.getValue(),
					NucleusMod.CONFIG.jackpotOddsJaderald);
			}
			if (oddsFragmentBox != null) {
				NucleusMod.CONFIG.jackpotOddsFragment = parseOdds(oddsFragmentBox.getValue(),
					NucleusMod.CONFIG.jackpotOddsFragment);
			}
			if (oddsMixtureBox != null) {
				NucleusMod.CONFIG.jackpotOddsMixture = parseOdds(oddsMixtureBox.getValue(),
					NucleusMod.CONFIG.jackpotOddsMixture);
			}
			if (oddsFlawlessBox != null) {
				NucleusMod.CONFIG.jackpotOddsFlawless = parseOdds(oddsFlawlessBox.getValue(),
					NucleusMod.CONFIG.jackpotOddsFlawless);
			}
		} catch (NumberFormatException ignored) {
		}
		if (soundIdBox != null) {
			String id = soundIdBox.getValue().trim();
			if (!id.isEmpty() && id.length() <= 80) {
				NucleusMod.CONFIG.objectiveSoundId = id;
			}
		}
		if (customPathBox != null) {
			String path = customPathBox.getValue().trim();
			if (path.length() <= 260) {
				NucleusMod.CONFIG.customSoundPath = path;
			}
		}
		NucleusMod.CONFIG.save();
	}

	@Override
	public void onClose() {
		saveAndClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
