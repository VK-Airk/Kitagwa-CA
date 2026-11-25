package com.nucleus;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Field initializers are the defaults: Gson builds through the no-arg
 * constructor, so keys missing from the file keep them, and reset() just
 * copies a fresh instance.
 */
public class NucleusConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final Path CONFIG_PATH = FabricLoader.getInstance()
		.getConfigDir()
		.resolve("nucleus")
		.resolve("nucleus.json");

	// --- Jungle Temple waypoints ---
	@com.google.gson.annotations.SerializedName(value="kcaEnabled", alternate={"madoBrickEnabled"})
	public boolean kcaEnabled = true;
	public boolean partyChatEnabled = false, partyAllInviteOff = false;
	public String partyTriggers = "!", partyChannelMarkers = "Party >,.pc", partyReplyRoute = "pchat", partyProfitSource = "Auto";
	public java.util.Set<String> partyDisabled = new java.util.HashSet<>();
	public boolean shortcutsEnabled = true;
    public boolean shortcutCommands = true, shortcutArguments = true, shortcutKeys = true;
	public boolean testCommands = false;
    public boolean showGameplayHud = true, menuEffects = true;
    public boolean wheelMount = false, wheelSparkles = true, wheelRollScreens = true;
    public boolean wheelOpening = true;
    public boolean vaultAnimation = false, originalWheelPointer = false;
    public enum Game {
        Wheel, Vault, CrystalVault, Briefcase, RandomVault, Random;
        @Override public String toString() { return switch (this) {
            case RandomVault -> "Random vault"; case Random -> "Random game"; case Vault -> "Trial vault";
            case Wheel -> "Legacy wheel"; case CrystalVault -> "Crystal vault"; case Briefcase -> "Briefcase slots"; default -> name();
        }; }
    }
    public enum Ending {
        Auto, Classic, Vault, CrystalVault, Briefcase;
        @Override public String toString() { return this == Auto || this == Classic ? name() : Game.valueOf(name()).toString(); }
    }
    public boolean vaultKeyLoops = true, vaultKeyTrails = true, vaultLockSounds = true;
    public boolean coolTrialAccent = false;
    public boolean rarityBackgrounds, rarityCircular, rarityLegacy;
    public boolean rarityHotbar=true;
    public boolean rarityStorage=true;
    public enum RarityShape { Square, Circle, Diamond, Frame, Corners }
    public record ItemBackground(int color, RarityShape shape) {}
    public RarityShape rarityShape;
    public RarityShape backgroundShape(){
        return rarityShape!=null?rarityShape:rarityCircular?RarityShape.Circle:RarityShape.Square;
    }
    public java.util.Map<String,ItemBackground> rarityItems=new java.util.TreeMap<>();
    public int rarityOpacity=60;
    public java.util.Map<String,Integer> rarityColors=new java.util.HashMap<>();
    public boolean showRunGoal;
    public int runGoalX=12, runGoalY=174;
    public float runGoalScale=1;
    public boolean everyBundle, showRunCounter=true, showCrystalChecklist=true;
    private Boolean verticalCrystalChecklist; // Legacy layout, read once on load.
    public boolean downtimeAlert = true;
    public static final String DEFAULT_DOWNTIME_HEADLINE = "HOOOLLLYY DT";
    public static final String DEFAULT_DOWNTIME_SUBTITLE = "LOCCCK INN";
    public String downtimeHeadline = DEFAULT_DOWNTIME_HEADLINE;
    public String downtimeSubtitle = DEFAULT_DOWNTIME_SUBTITLE;
    public boolean hollowsMap;
    public boolean mapRegions=true, mapLegend=true, mapWaypoints=true, mapNucleus=true;
    public long hiddenMapPins, highlightedMapPins;
    public enum MapIcon { Letter, Item, Diamond, Cross, Square }
    public java.util.Map<String,MapIcon> mapIcons=new java.util.HashMap<>();
    public enum PinColor {
        Auto(0), Rose(0xFF97BB), Coral(0xFF806E), Orange(0xFFAF66), Gold(0xFFDA81),
        Lemon(0xF9F899), Lime(0xB3EB7B), Mint(0x76E6B0), Aqua(0x76E9E9),
        Sky(0x82CCFF), Blue(0x91AAFF), Lilac(0xC3A0FF), Orchid(0xF79AF2), White(0xFFFFFF);
        public final int rgb;
        PinColor(int rgb) { this.rgb = rgb; }
    }
    public java.util.Map<String,PinColor> mapPinColors = new java.util.HashMap<>();
    public int mapMarkerPercent = 100;
    public int hollowsMapX=8, hollowsMapY=8;
    public float hollowsMapScale=1;
    public boolean showBetaBadges = true;
    public boolean compactDamageNumbers = false;
    public boolean showDamageSplashes = true;
    public int damageSignificantFigures = 2;
    public boolean showDamageSymbols = true;
    public boolean lowercaseDamageUnits = false;
    public enum DamageColor { Original, Gold, Aqua, Pink, Red, White }
    public DamageColor damageColor = DamageColor.Original;
    public boolean showStash = true, moleToolReminder = true;
    public enum StashCounts {
        BOTH("Items + materials"), ITEMS("Items"), MATERIALS("Materials");
        public final String label;
        StashCounts(String label) { this.label=label; }
    }
    public StashCounts stashCounts=StashCounts.BOTH;
    public boolean stashHollowsOnly;
    public int stashX = 12, stashY = 225;
    public float stashScale = 1;
    public boolean showToolSets = true;
    public int toolHudX = 12, toolHudY = 204;
    public float toolHudScale = 1;
    public enum CrystalLayout {
        COMPACT("Compact text"), VERTICAL("Vertical icons"), HORIZONTAL("Fancy horizontal");
        public final String label;
        CrystalLayout(String label) { this.label = label; }
    }
    public enum Gem {
        AMBER("Amber"), SAPPHIRE("Sapphire"), JADE("Jade"), AMETHYST("Amethyst"), TOPAZ("Topaz");
        public final String label, id;
        Gem(String label) {
            this.label = label;
            id = label.toLowerCase(java.util.Locale.ROOT);
        }
    }
    public CrystalLayout crystalLayout = CrystalLayout.COMPACT;
    public java.util.List<Gem> crystalOrder = java.util.List.of(Gem.values());

    public void moveCrystal(int from, int to) {
        if (from < 0 || to < 0 || from >= crystalOrder.size() || to >= crystalOrder.size()) return;
        var order = new java.util.ArrayList<>(crystalOrder);
        java.util.Collections.swap(order, from, to);
        crystalOrder = java.util.List.copyOf(order);
        save();
    }
    public java.util.Map<String,Game> dropGames=new java.util.HashMap<>();
    public boolean shuffleGames, revealFlashes=true, revealBanner=true;
    public boolean legacyRewardPopup;
    public int crystalGateMs=1400, crystalHudX=12, crystalHudY=150;
    public float crystalHudScale=1;
    public java.util.Set<Game> excludedGames=new java.util.HashSet<>(java.util.Set.of(Game.Wheel));
    public static final Game[] PLAYABLE={Game.Wheel,Game.Vault,Game.CrystalVault,Game.Briefcase};
    public Game resolveGame(String drop){
        Game rule=dropGames.get(drop);
        if(rule==null)rule=shuffleGames?Game.Random:selectedGame();
        var random=java.util.concurrent.ThreadLocalRandom.current();
        return switch(rule){
            case RandomVault -> random.nextBoolean()?Game.Vault:Game.CrystalVault;
            case Random -> {
                Game pick=Game.Wheel;
                int count=0;
                for(Game candidate:PLAYABLE)
                    if(!excludedGames.contains(candidate) && random.nextInt(++count)==0)pick=candidate;
                yield pick;
            }
            default -> rule;
        };
    }
    public Game gambleGame;
    public Ending gambleEnding = Ending.Auto;
    public int confettiPercent=100;
    public java.util.Map<String,String> gambleTitles=new java.util.HashMap<>();
    public Game selectedGame(){return gambleGame==null?(vaultAnimation?Game.Vault:Game.CrystalVault):gambleGame;}
    public Ending selectedEnding(){return gambleEnding==null?Ending.Auto:gambleEnding;}
    public String gambleTitle(String original){return gambleTitles.getOrDefault(original,defaultTitle(original));}
    public static String defaultTitle(String original){return "LEGENDARY WIN".equals(original)?"RARE DROP!":original;}
    public boolean subtleMenuEffects = false;
    public dev.purplepad.HudStyle nucleusHudStyle = dev.purplepad.HudStyle.KCA;
    public int paletteVersion = 0;
	public int dungeonAccent = 0xFFB7AACD, nucleusAccent = 0xFFDCB7B0, timerColor = 0xFFF499B8;
	public char secretColor = 'b', roomColor = 'd', lowestColor = 'f';
	public boolean useReferenceOffsets = false;
	/** Automatic temple waypoints (from both Door Guardians) vs manual hotkey. */
	public boolean templeAutoPlace = true;
	public boolean waypointsThroughWalls = true;
	public boolean waypointText = false;
	/** Link lines WP1 -> WP2 -> WP3 (jungle temple waypoints only). */
	public boolean templeLinkLines = false;
	public boolean showWaypoint1 = true;
	public boolean showWaypoint2 = true;
	public boolean showWaypoint3 = true;
	/** Outline thickness for the waypoint block highlight. */
	public float waypointOutlineWidth = 3.0f;
	/** ARGB colors for the three waypoints. */
	public int waypoint1Color = 0xFF55FFFF;
	public int waypoint2Color = 0xFF55FF55;
	public int waypoint3Color = 0xFFFFAA00;

	// --- Custom waypoint (under player feet, shares line settings above) ---
	public boolean showCustomWaypoint = true;
	public int customWaypointColor = 0xFFFFFFFF;
	/** Dims waypoints past etherwarp range (61 blocks) so range reads at a glance. */
	public boolean waypointDimBeyondEtherwarp = true;

	// --- Bal timer (Crystal Hollows) ---
	public boolean balTimerEnabled = false;
	/** "Bal: spawned/despawned/ded" line, drawn with the Bal timer HUD. */
	public boolean balDetectorEnabled = false;
	/** Quick celebration (rarity + 3D pop) when a Bal pet drops. */
	public boolean balPetAnimEnabled = true;
	/** Run profit: you hand Professor Robot a Precursor Apparatus instead of the 6 parts. */
	public boolean nucleusUsesApparatus = false;
	public int balTimerX = 10;
	public int balTimerY = 80;
	/** HUD text scale 0.5-3.0. */
	public float balTimerScale = 1.0f;

	// --- Lobby day display (sidebar date, anywhere) ---
	public boolean lobbyDayEnabled = false;
	public int lobbyDayX = 10;
	public int lobbyDayY = 100;
	public float lobbyDayScale = 1.0f;

	// --- Scavenger tool tracker (Mines of Divan) ---
	public boolean scavengerEnabled = false;
	public int scavengerX = 10;
	public int scavengerY = 120;
	public float scavengerScale = 1.0f;
	/** Tracker rate line: false = tools/hour, true = sets/hour. */
	public boolean scavengerSetsPerHour = false;

	// --- Terror (Hydra Strike) armor stacks (Crimson Isles) ---
	public boolean terrorStacksEnabled = false;
	public int terrorStacksX = 10;
	public int terrorStacksY = 140;
	public float terrorStacksScale = 2.0f;
	/** One-time 1.4.0 migration: the overlay's 1x default became 2x. */
	public boolean terrorStacksScaleBumped = false;

	// --- Divan chest locator (Mines of Divan, packet-driven) ---
	public boolean divanChestEnabled = false;
	public boolean divanChestText = true;
	/** Mutes the metal-detector harp ding while the detector is held. */
	public boolean divanMuteDetector = false;
	public int divanChestColor = 0xFF55FF55;

	// --- Hollows alerts ---
	/** Wrong-pet warning in the Mines without the Mole (needs Pet tab widget). */
	public boolean balPartyAlert = false;
    public String balPartyText = "I'm at Bal! {coords}";
    public boolean rarePartyAlert = false;
    public String rarePartyText = "I dropped {drop}!";
    public boolean petAlertEnabled = false;
	public boolean lowToolsAlertEnabled = false;
	/** Alert position as an offset from screen center (keeps centered on resize). */
	public int alertOffX = 0;
	public int alertOffY = -70;
	public float alertScale = 1.0f;

	// --- Objective sounds ---
	/** Master toggle: false = nothing ever plays, true = every enabled trigger plays. */
	public boolean objectiveSoundOn = true;
	/** Objective sound volume 0-200 percent. */
	public int objectiveSoundVolume = 80;
	/** Achievement unlock jingle volume 0-200 percent. */
	public int achievementVolume = 80;
	/**
	 * When true, vanilla mod sounds play straight to the OS mixer (ignoring
	 * Minecraft's volume sliders). When false, everything goes through the
	 * vanilla mixer (Master + category sliders apply). Off by default.
	 */
	public boolean bypassMinecraftVolume = false;
	/** Minecraft sound id, e.g. "minecraft:block.bone_block.place". */
	public String objectiveSoundId = "minecraft:block.bone_block.place";
	public boolean customSoundEnabled = false;
	public String customSoundPath = "";

	// --- Mob highlights (colors follow the speedrun splits) ---
	public boolean mobHighlightEnabled = false;

	// --- Jackpot rare-drop animation ---
	public boolean jackpotEnabled = true;
	/** Jackpot sound volume 0-200 percent (OS mixer: ignores Minecraft's sliders). */
	public int jackpotVolume = 100;
    public int finalRevealVolume = 25;
	/** First-part speed percent 50-200: feature text, spin-power and luck (100 = normal). */
	public int jackpotSpeedEarlyPct = 100;
	/** Second-part speed percent 50-200: the wheel spin (100 = normal). */
	public int jackpotSpeedWheelPct = 100;
	/** Third-part speed percent 50-200: the celebration screen (100 = normal). */
	public int jackpotSpeedCelebPct = 100;

	/** 1-in-N odds for the consolation lottery on bundle loot lines. */
	public int jackpotOddsJaderald = 3;
	public int jackpotOddsFragment = 8;
	public int jackpotOddsMixture = 3;
	public int jackpotOddsFlawless = 1;

	/** Zoom level percent 0-100 for the wheel finale (0 = off). */
	public int jackpotZoomPct = 50;

	/** Black box over chat loot during the pre-reveal stages. */
	public boolean hideBundle = false;

	/** Test-animation drop: random, alloy, claw, jade, jaderald, fragment, mixture. */
	public String jackpotPreview = "random";

	/** Timer background tint 0-100 (Bal + speedrun HUD text boxes). */
	public int timerBg = 50;

	/** When true the mod sends no chat messages at all. */
	public boolean quietChat = false;

	/** Master switch for the crystal-run speedrun timer. */
	public boolean speedrunEnabled = true;
	public int speedrunStartOffset = 0;

	/** Prints a lobby run summary in chat when leaving a lobby with 2+ runs. */
	public boolean lobbySummary = true;

	/** SAFE MODE: blocks every /warp except /warp nucleus|cn. */
	public boolean safeMode = false;
	/** Config menu theme id (see NucleusTheme); locked themes fall back to "space". */
	public String menuTheme = "space";
	/** Gambling wheel sound pack id (see NucleusSoundPack). */
	public String gambleSoundPack = "classic";
	/** Theme colours, light bands and particles behind the gambling animation. */
	public boolean themedWheelFx = true;
	/** Seasonal touches in the menu (snow at Christmas, bats at Halloween...). */
	public boolean seasonalEffects = true;
	/** Warn on entering a Hollows lobby you were already in, or one over 2 weeks old. */
	public boolean badLobbyAlertEnabled = true;

	/** Auto safe mode: switches safe mode on after N runs in one lobby. */
	public boolean autoSafeMode = false;

	/** Finished runs in one lobby before auto safe mode triggers (1-20). */
	public int autoSafeModeRuns = 3;

	/** Warns when the tab Info widget can't be found (dismissable in chat). */
	public boolean tabWarnEnabled = true;

	// --- Objective sound triggers (Sounds tab; all on by default) ---
	public boolean soundYolkar = true;
	public boolean soundCrystal = true;
	public boolean soundApparatus = true;
	public boolean soundTool = true;
	public boolean soundKey = true;
	public boolean soundBal = true;
	public boolean soundPlace = true;
	public boolean soundChest = true;

	/** Restores every setting to defaults (records like run history live elsewhere). */
	public void reset() {
		copyFrom(new NucleusConfig());
		save();
	}

	public void load() {
		if (!Files.exists(CONFIG_PATH)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
			NucleusConfig loaded = GSON.fromJson(reader, NucleusConfig.class);
			if (loaded != null) {
				loaded.sanitize();
				copyFrom(loaded);
			}
		} catch (IOException | JsonParseException e) {
			NucleusMod.LOGGER.warn("Failed to read Nucleus config: {}", e.getMessage());
		}
	}

	public void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(CONFIG_PATH, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			NucleusMod.LOGGER.warn("Failed to save Nucleus config: {}", e.getMessage());
		}
	}

	/** Out-of-range or hand-broken values fall back to their defaults. */
	private void sanitize() {
        if (downtimeHeadline == null || downtimeHeadline.isBlank()) downtimeHeadline = DEFAULT_DOWNTIME_HEADLINE;
        if (downtimeSubtitle == null || downtimeSubtitle.isBlank()) downtimeSubtitle = DEFAULT_DOWNTIME_SUBTITLE;
        mapMarkerPercent = Math.clamp(mapMarkerPercent, 50, 200);
        if (mapPinColors == null) mapPinColors = new java.util.HashMap<>();
        mapPinColors.values().removeIf(java.util.Objects::isNull);
        if(mapIcons==null)mapIcons=new java.util.HashMap<>();
        mapIcons.values().removeIf(java.util.Objects::isNull);
        if(excludedGames==null)excludedGames=new java.util.HashSet<>();
        excludedGames.remove(null);
        if(gambleGame==Game.Random || gambleGame==Game.RandomVault){
            shuffleGames=true;
            if(gambleGame==Game.RandomVault)for(Game candidate:PLAYABLE)
                if(candidate!=Game.CrystalVault && candidate!=Game.Vault)excludedGames.add(candidate);
            gambleGame=Game.Wheel;
        }
        if(excludedGames.containsAll(java.util.List.of(PLAYABLE)))excludedGames.remove(Game.Wheel);
        crystalGateMs=range(crystalGateMs,500,3500,1400);
        if(rarityItems==null)rarityItems=new java.util.TreeMap<>();
        rarityItems.values().removeIf(java.util.Objects::isNull);
        if(rarityColors==null)rarityColors=new java.util.HashMap<>();
        rarityOpacity=range(rarityOpacity,0,100,60);
        if (Boolean.TRUE.equals(verticalCrystalChecklist)) crystalLayout = CrystalLayout.VERTICAL;
        verticalCrystalChecklist = null;
        if (crystalLayout == null) crystalLayout = CrystalLayout.COMPACT;
        var gems = new java.util.LinkedHashSet<Gem>();
        if (crystalOrder != null) for (Gem gem : crystalOrder) if (gem != null) gems.add(gem);
        java.util.Collections.addAll(gems, Gem.values());
        crystalOrder = java.util.List.copyOf(gems);
        hollowsMapScale=scale(hollowsMapScale);
        toolHudScale=scale(toolHudScale);
        crystalHudScale=scale(crystalHudScale);
        runGoalScale=scale(runGoalScale);
        if(dropGames==null)dropGames=new java.util.HashMap<>();
        dropGames.values().removeIf(java.util.Objects::isNull);
        confettiPercent=Math.clamp(confettiPercent,0,100);
        if(gambleTitles==null)gambleTitles=new java.util.HashMap<>();
        gambleTitles.replaceAll((k,v)->v==null?"":v.strip());
        gambleTitles.values().removeIf(v->v.isEmpty() || v.length()>28 || v.chars().anyMatch(Character::isISOControl));
		if (partyDisabled == null) partyDisabled = new java.util.HashSet<>();
		if (partyTriggers == null || partyTriggers.isBlank()) partyTriggers = "!";
		if (partyChannelMarkers == null || partyChannelMarkers.isBlank()) partyChannelMarkers = "Party >,.pc";
		if (!"pc".equals(partyReplyRoute)) partyReplyRoute = "pchat";
		if (!"IQ".equals(partyProfitSource) && !"SBO".equals(partyProfitSource)) partyProfitSource = "Auto";
		NucleusConfig d = new NucleusConfig();
        if(paletteVersion<1){
            if(dungeonAccent==0xFFAAA3FF)dungeonAccent=d.dungeonAccent;
            if(nucleusAccent==0xFFFFC877)nucleusAccent=d.nucleusAccent;
            paletteVersion=1;
        }
		if (!(waypointOutlineWidth > 0.1f && waypointOutlineWidth <= 10.0f)) {
			waypointOutlineWidth = d.waypointOutlineWidth;
		}
		if (customWaypointColor == 0) {
			customWaypointColor = d.customWaypointColor;
		}
		// 0 is unset; the old orange default migrates to green.
		if (divanChestColor == 0 || divanChestColor == 0xFFFFAA00) {
			divanChestColor = d.divanChestColor;
		}
		balTimerScale = scale(balTimerScale);
		lobbyDayScale = scale(lobbyDayScale);
		scavengerScale = scale(scavengerScale);
		terrorStacksScale = scale(terrorStacksScale);
		if (!terrorStacksScaleBumped) {
			terrorStacksScale = d.terrorStacksScale;
			terrorStacksScaleBumped = true;
		}
		alertScale = scale(alertScale);
		objectiveSoundVolume = range(objectiveSoundVolume, 0, 200, d.objectiveSoundVolume);
		achievementVolume = range(achievementVolume, 0, 200, d.achievementVolume);
		jackpotVolume = range(jackpotVolume, 0, 200, d.jackpotVolume);
        finalRevealVolume = range(finalRevealVolume, 0, 100, d.finalRevealVolume);
		jackpotSpeedEarlyPct = range(jackpotSpeedEarlyPct, 50, 200, d.jackpotSpeedEarlyPct);
		jackpotSpeedWheelPct = range(jackpotSpeedWheelPct, 50, 200, d.jackpotSpeedWheelPct);
		jackpotSpeedCelebPct = range(jackpotSpeedCelebPct, 50, 200, d.jackpotSpeedCelebPct);
		jackpotOddsJaderald = range(jackpotOddsJaderald, 1, 1_000_000, d.jackpotOddsJaderald);
		jackpotOddsFragment = range(jackpotOddsFragment, 1, 1_000_000, d.jackpotOddsFragment);
		jackpotOddsMixture = range(jackpotOddsMixture, 1, 1_000_000, d.jackpotOddsMixture);
		jackpotOddsFlawless = range(jackpotOddsFlawless, 1, 1_000_000, d.jackpotOddsFlawless);
		jackpotZoomPct = range(jackpotZoomPct, 0, 100, d.jackpotZoomPct);
		timerBg = range(timerBg, 0, 100, d.timerBg);
		speedrunStartOffset = range(speedrunStartOffset,0,64,0);
		autoSafeModeRuns = range(autoSafeModeRuns, 1, 20, d.autoSafeModeRuns);
		if (objectiveSoundId == null || objectiveSoundId.isBlank()) {
			objectiveSoundId = d.objectiveSoundId;
		}
		if (customSoundPath == null) {
			customSoundPath = d.customSoundPath;
		}
		jackpotPreview = jackpotPreview == null || jackpotPreview.isBlank()
			? d.jackpotPreview : jackpotPreview.trim().toLowerCase();
	}

	private static float scale(float v) {
		return v >= 0.5f && v <= 3.0f ? v : 1.0f;
	}

	private static int range(int v, int min, int max, int fallback) {
		return v >= min && v <= max ? v : fallback;
	}

	private void copyFrom(NucleusConfig src) {
		for (Field f : NucleusConfig.class.getDeclaredFields()) {
			if (Modifier.isStatic(f.getModifiers())) {
				continue;
			}
			try {
				f.set(this, f.get(src));
			} catch (IllegalAccessException e) {
				NucleusMod.LOGGER.warn("Config field {} not copied", f.getName());
			}
		}
	}
}
