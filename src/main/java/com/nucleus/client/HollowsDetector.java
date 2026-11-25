package com.nucleus.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import com.nucleus.NucleusMod;

/**
 * Detects whether the player is in the Crystal Hollows.
 *
 * Primary source is the tab list {@code Area:} line (e.g. "Area: Dwarven
 * Mines" / "Area: Crystal Hollows"), like SkyHanni reads it — authoritative
 * and immune to stat-line collisions. Sidebar keyword matching is only a
 * fallback for when the tab Info widget is off.
 *
 * Gotcha this guards against: Hypixel renders tab-list columns with
 * scoreboard teams too, so unfiltered team scans pick up tab junk like
 * "Fairy Souls: 15/15". Team scans are therefore restricted to teams that
 * back an actual sidebar score.
 */
public final class HollowsDetector {
	private static final int POLL_INTERVAL_TICKS = 20;
	private static final long WARN_COOLDOWN_MS = 300_000L;
	private static final long LEVEL_GRACE_MS = 20_000L;

	private static final String[] KEYWORDS = {
		"hollows", "crystal", "magma", "deposits", "precursor", "nucleus",
		"khazad", "fairy", "grotto", "jungle", "temple", "holdout",
		"lair", "divan", "sludge", "remnants"
	};

	private static boolean inHollows = false;
	private static boolean inMines = false;
	private static boolean inNucleus = false;
	private static boolean inKhazad = false;
	private static int ticksSinceLastCheck = 0;
	private static long lastWarnAt = 0L;
	private static ClientLevel warnLevel = null;
	private static long levelStartAt = 0L;

	private HollowsDetector() {
	}

	public static void reset() {
        HollowsMap.reset();
		inHollows = false;
		inMines = false;
		inNucleus = false;
		inKhazad = false;
		ticksSinceLastCheck = 0;
		lastWarnAt = 0L;
		warnLevel = null;
		levelStartAt = 0L;
	}

	public static boolean isInCrystalHollows() {
		return inHollows;
	}

	/**
	 * Live check (no poll cache): is the player's current sidebar location
	 * the Jungle Temple? Hypixel builds each sidebar line from a team
	 * (prefix + player + suffix), so "Jungle" and "Temple" can arrive in
	 * different fields — combine each team's parts before matching.
	 */
	public static boolean isInJungleTemple(Minecraft client) {
		if (client == null || client.level == null) {
			return false;
		}
        String all=String.join("\n",sidebarLines(client));
        return all.contains("jungle") && all.contains("temple");
	}

	public static void tick(Minecraft client) {
		ticksSinceLastCheck++;
		if (ticksSinceLastCheck < POLL_INTERVAL_TICKS) {
			return;
		}
		ticksSinceLastCheck = 0;

		// One scoreboard read per poll. Recombined team lines are included
		// because a location can straddle a prefix/suffix boundary ("Mines of
		// Divan" is 17 chars with its icon).
		Optional<String> area = tabArea(client);
        if(area.isPresent() && !area.get().toLowerCase(java.util.Locale.ROOT).contains("crystal hollows")){
            if(inHollows)HollowsMap.reset();
            inHollows=inMines=inNucleus=inKhazad=false;
            return;
        }
        List<String> lines = sidebarLines(client);
		if (area.isPresent()) {
			inHollows = area.get().toLowerCase().contains("crystal hollows");
		} else {
			inHollows = anyLine(lines, KEYWORDS);
			if (inHollows && SkyBlockDetector.isOnSkyBlock()) maybeWarnNoArea(client);
		}
		// Location details share the 1Hz poll so HUDs (per-frame) and
		// trackers never scan the scoreboard themselves.
		inMines = anyLine(lines, "mines of divan");
		inNucleus = anyLine(lines, "crystal nucleus");
		inKhazad = anyLine(lines, "khazad");
        HollowsMap.update(client,lines);
	}

	private static boolean anyLine(List<String> lines, String... needles) {
		for (String line : lines) {
			for (String needle : needles) {
				if (line.contains(needle)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Hollows keyword test for one stripped, lowercased line (debug listing). */
	public static boolean matches(String strippedLower) {
		return strippedLower != null && anyLine(List.of(strippedLower), KEYWORDS);
	}

	/**
	 * Tab-list "Area:" value, e.g. "Dwarven Mines" / "Crystal Hollows".
	 * Empty when the tab Info widget is off or the list hasn't populated yet.
	 */
	public static Optional<String> tabArea(Minecraft client) {
		if (client == null) {
			return Optional.empty();
		}
		try {
			var connection = client.getConnection();
			if (connection == null) {
				return Optional.empty();
			}
			for (var info : connection.getOnlinePlayers()) {
				if (info == null) {
					continue;
				}
				Component display;
				try {
					display = info.getTabListDisplayName();
				} catch (Exception ignored) {
					continue;
				}
				if (display == null) {
					continue;
				}
				String s = stripFormatting(display.getString()).trim();
				int idx = s.toLowerCase().indexOf("area:");
				if (idx >= 0) {
					String value = s.substring(idx + 5).trim();
					if (!value.isEmpty()) {
						return Optional.of(value);
					}
				}
			}
		} catch (Exception ignored) {
		}
		return Optional.empty();
	}

	private static void maybeWarnNoArea(Minecraft client) {
		if (client == null || client.gui == null) {
			return;
		}
		if (!NucleusMod.CONFIG.tabWarnEnabled) {
			return;
		}
		if (!NucleusMod.CONFIG.kcaEnabled
			&& !NucleusMod.CONFIG.balTimerEnabled
			&& !NucleusMod.CONFIG.jackpotEnabled) {
			return;
		}
		long now = System.currentTimeMillis();
		if (client.level != warnLevel) {
			warnLevel = client.level;
			levelStartAt = now;
			return;
		}
		if (now - levelStartAt < LEVEL_GRACE_MS || now - lastWarnAt < WARN_COOLDOWN_MS) {
			return;
		}
		lastWarnAt = now;
		Component dismiss = Component.literal(" §c§l[Don't show again]").withStyle(style -> style
			.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/kca disableTabwarn"))
			.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
				Component.literal("§7Hide this warning forever"))));
		KcaChat.chat(client, Component.literal(
			"§d[KCA] §7Tab Area widget not found — run §e/tab §7and enable the Info widget.")
			.append(dismiss));
	}


	/** True when the sidebar location is the Mines of Divan (1Hz cached). */
	public static boolean inMinesOfDivan(Minecraft client) {
		return inMines;
	}

	/** True when the sidebar location is the Crystal Nucleus (1Hz cached). */
	public static boolean inCrystalNucleus(Minecraft client) {
		return inNucleus;
	}

	/** True when the sidebar location is Khazad-dum (1Hz cached). */
	public static boolean inKhazadDum(Minecraft client) {
		return inKhazad;
	}



	/** Stripped sidebar lines (lowercase, formatting removed) for detection + debug. */
	public static List<String> sidebarLines(Minecraft client) {
        if(client==null || client.level==null)return List.of();
        Scoreboard board=client.level.getScoreboard();
        Objective sidebar=board.getDisplayObjective(DisplaySlot.SIDEBAR);
        if(sidebar==null)return List.of();
        List<String> lines=new ArrayList<>();
        Set<net.minecraft.world.scores.PlayerTeam> seen=new HashSet<>();
        for(var entry:board.listPlayerScores(sidebar)){
            addClean(lines,entry.owner());
            if(entry.display()!=null)addClean(lines,entry.display().getString());
            addClean(lines,entry.ownerName().getString());
            var team=board.getPlayersTeam(entry.owner());
            if(team==null || !seen.add(team))continue;
            String prefix=team.getPlayerPrefix().getString(),suffix=team.getPlayerSuffix().getString();
            addClean(lines,prefix);addClean(lines,suffix);addClean(lines,team.getDisplayName().getString());
            // Formatting teams can split a location name in the middle of a word.
            addClean(lines,prefix+String.join("",team.getPlayers())+suffix);
        }
        return lines;
    }

	private static void addClean(List<String> out, String s) {
		if (s == null || s.isEmpty()) {
			return;
		}
		String clean = stripFormatting(s).toLowerCase().trim();
		if (!clean.isEmpty()) {
			out.add(clean);
		}
	}

	public static String stripFormatting(String s) {
		if(s.indexOf('§')<0)return s;
		StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '§' && i + 1 < s.length()) {
				i++;
				continue;
			}
			out.append(c);
		}
		return out.toString();
	}
}
