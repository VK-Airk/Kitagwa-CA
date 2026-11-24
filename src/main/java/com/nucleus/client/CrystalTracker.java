package com.nucleus.client;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.nucleus.NucleusMod;
import com.nucleus.NucleusConfig.Gem;
import com.nucleus.NucleusConfig.CrystalLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Tracks which of the five crystals (amber, sapphire, jade, amethyst,
 * topaz) were picked up this lobby. The obtain SOUND fires only on a
 * "crystal found" x/5 counter going up — whether it arrives as a title
 * packet or as the "✦ CRYSTAL FOUND (x/5)" chat block (Hypixel sends the
 * latter; both feed the same counter). Chat mentions and title names only
 * mark the set, never chime.
 *
 * <p>Note: there is no CRYSTAL PLACED title — placements arrive only as
 * "You placed the X Crystal!" chat lines, handled by
 * {@link SpeedrunManager}.
 */
public final class CrystalTracker {
	private CrystalTracker() {
	}

	private static final String[] CRYSTALS = { "amber", "sapphire", "jade", "amethyst", "topaz" };

	private static final Set<String> runCrystals=new HashSet<>();
    private static String checklist;
    private static java.util.List<Gem> lastOrder;
    static void startChecklist(){runCrystals.clear();checklist=null;}
    static void noteForRun(String name){if(runCrystals.add(name))checklist=null;}
    static String checklist() {
        var order = NucleusMod.CONFIG.crystalOrder;
        if (checklist == null || lastOrder != order) {
            var cells = new java.util.StringJoiner(" ");
            for (Gem gem : order) cells.add((runCrystals.contains(gem.id) ? "§a+" : "§7-") + gem.label);
            checklist = cells.toString();
            lastOrder = order;
        }
        return checklist;
    }

    private static final java.util.EnumMap<Gem,VaultCrystal> icons = new java.util.EnumMap<>(Gem.class);
    static {
        for (Gem gem : Gem.values()) icons.put(gem, VaultCrystal.valueOf(gem.name()));
    }

    private static final int TILE = 22;
    static int iconWidth(Font font) {
        if (NucleusMod.CONFIG.crystalLayout == CrystalLayout.HORIZONTAL) return TILE * icons.size() - 2;
        int width = 0;
        for (Gem gem : NucleusMod.CONFIG.crystalOrder) width = Math.max(width, font.width(gem.label));
        return width + 25;
    }
    static int iconHeight() {
        return NucleusMod.CONFIG.crystalLayout == CrystalLayout.HORIZONTAL ? 21 : 14 * icons.size();
    }
    static void drawIcons(GuiGraphicsExtractor g, int x, int y, float scale) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        boolean horizontal = NucleusMod.CONFIG.crystalLayout == CrystalLayout.HORIZONTAL;
        var pose = g.pose();
        pose.pushMatrix();
        try {
            pose.translate(x,y);
            pose.scale(scale,scale);
            HudTint.backdrop(g,-3,-3,iconWidth(font)+3,iconHeight()+3);
            int i = 0;
            for (Gem gem : NucleusMod.CONFIG.crystalOrder) {
                var icon = icons.get(gem);
                boolean collected = runCrystals.contains(gem.id);
                int left = horizontal ? TILE * i : 0;
                int top = horizontal ? 0 : 14 * i;
                int size = horizontal ? 16 : 13;
                if (horizontal) {
                    g.fill(left,0,left+20,20,collected ? 0xDB29252C : 0xB319171E);
                    g.fill(left,18,left+20,20,collected ? (0xFF000000 | icon.rgb) : 0xFF53505A);
                }
                g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,icon.sprite,
                    left+(horizontal?2:0),top+(horizontal?1:0),0f,0f,size,size,16,16,16,16,collected?0xFFFFFFFF:0xFF888888);
                if (horizontal) {
                    if (collected) g.text(font,"+",left+14,10,0xFFFFFFFF,true);
                } else {
                    int tint = collected ? 0xFF91EF9A : 0xFFAAAAAA;
                    g.text(font,collected?"+":"-",16,top+3,tint,true);
                    g.text(font,gem.label,25,top+3,tint,true);
                }
                i++;
            }
        } finally { pose.popMatrix(); }
    }
    private static final Set<String> obtained = new HashSet<>();

	private static final Pattern ANY_COUNT = Pattern.compile("(\\d)\\s*/\\s*5");

	/** Latched when 5/5 is seen (names alone can't prove it). */
	private static boolean foundAll = false;
	/** Crystal-titled texts already logged as unmatched (keeps the log quiet). */
	private static final Set<String> loggedMisses = new HashSet<>();

	public static void reset() {
		obtained.clear();
		foundAll = false;
	}

	/** True once all five are picked up (placements never count). */
	public static boolean hasAll() {
		return foundAll || obtained.size() >= CRYSTALS.length;
	}

	/**
	 * Title/subtitle packets: anything reading "crystal found" with an x/5
	 * counter ("CRYSTAL FOUND (1/5)", "Crystal Found! (2/5)", subtitle
	 * variants — punctuation tolerant) chimes on sight; same-tick doubles
	 * collapse in the per-trigger sound debounce. Unmatched crystal titles log once
	 * so a wording change shows up in latest.log.
	 */
	public static void onTitleText(String raw) {
		if (raw == null) {
			return;
		}
		String norm = HollowsDetector.stripFormatting(raw).toLowerCase();
		if (!norm.contains("crystal")) {
			return;
		}
		Matcher cm = ANY_COUNT.matcher(norm);
		if (norm.contains("found") && cm.find()) {
			try {
				onFoundCounter(Integer.parseInt(cm.group(1)));
			} catch (NumberFormatException ignored) {
			}
		} else if (loggedMisses.size() < 20 && loggedMisses.add(norm)) {
			NucleusMod.LOGGER.info("Crystal title without found-counter: '{}'", norm);
		}
		for (String c : CRYSTALS) {
			if (norm.contains(c + " crystal")) {
				// Mark only — the counter owns the sound.
				obtained.add(c);
                if(SpeedrunManager.state()==SpeedrunManager.State.RUNNING)noteForRun(c);
				return;
			}
		}
	}

	public static void onGameMessage(String raw) {
		if (raw == null) {
			return;
		}
		String norm = HollowsDetector.stripFormatting(raw).toLowerCase();
		if (ChatLines.isPlayerChat(norm)) {
			return;
		}
		// The pickup block ("✦ CRYSTAL FOUND (x/5)") arrives as plain
		// chat: same counter, same chime. lastFoundX keeps a simultaneous
		// title packet from double-chiming.
		Matcher cm = ANY_COUNT.matcher(norm);
		if (norm.contains("found") && norm.contains("crystal") && cm.find()) {
			try {
				onFoundCounter(Integer.parseInt(cm.group(1)));
			} catch (NumberFormatException ignored) {
			}
		}
		// Mark only — but never placements: holding-to-place is not picking
		// up, and it must not trip the pet alert. The counter owns that.
		if (norm.contains("you placed the")) {
			return;
		}
		for (String c : CRYSTALS) {
			if (norm.contains(c + " crystal")) {
				obtained.add(c);
                if(SpeedrunManager.state()==SpeedrunManager.State.RUNNING)noteForRun(c);
				return;
			}
		}
	}

	/** Every sighting chimes; back-to-back doubles collapse in the per-trigger 700ms debounce. */
	private static void onFoundCounter(int x) {
		if (x >= 1 && x <= 5) {
			ObjectiveSounds.onObjective(ObjectiveSounds.Trigger.CRYSTAL);
			if (x >= 5) {
				foundAll = true;
			}
		}
	}
}
