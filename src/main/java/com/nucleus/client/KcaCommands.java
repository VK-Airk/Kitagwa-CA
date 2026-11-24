package com.nucleus.client;

import com.mojang.brigadier.context.CommandContext;


import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.nucleus.NucleusMod;

/** Nucleus commands and optional diagnostic tools under /kca. */
public final class KcaCommands {
	private KcaCommands() {
	}

	public static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> root() {
			var nucleus = ClientCommands.literal("nucleus")
				.executes(KcaCommands::openGui)
				.then(ClientCommands.literal("skip").executes(KcaCommands::skip))
                .then(ClientCommands.literal("count").executes(ctx->{ctx.getSource().sendFeedback(Component.literal(SpeedrunManager.counter()));return 1;}))
                .then(ClientCommands.literal("pb").executes(KcaCommands::printPersonalBest))
                .then(ClientCommands.literal("times").executes(KcaCommands::history))
				.then(ClientCommands.literal("runs").executes(KcaCommands::history))
				.then(ClientCommands.literal("stop").executes(KcaCommands::stop))
				.then(ClientCommands.literal("lobbyStats").executes(KcaCommands::lobbyStats))
				.then(ClientCommands.literal("safe").executes(KcaCommands::safe))
				.then(ClientCommands.literal("syncRuns").executes(KcaCommands::syncRuns));
            // Temporary (testing themes): opens every theme until the game closes.
			var unlock = ClientCommands.literal("unlock").executes(dev.purplepad.PurplePadClient.test(ctx -> {
				NucleusTheme.unlockAllForSession();
				KcaChat.feedback(ctx.getSource(), Component.literal(
					"\u00a7d[KCA] \u00a77All themes unlocked until you close the game."));
				return 1;
			}));
			// Temporary (testing seasons): /kca test season <season|auto> until the game closes.
			var seasonCmd = ClientCommands.literal("season");
			seasonCmd.then(ClientCommands.literal("auto").executes(dev.purplepad.PurplePadClient.test(ctx -> {
				NucleusSeason.preview(null);
				KcaChat.feedback(ctx.getSource(), Component.literal("\u00a7d[KCA] \u00a77Seasons follow the date again."));
				return 1;
			})));
			for (NucleusSeason.Season se : NucleusSeason.Season.values()) {
				final NucleusSeason.Season season = se;
				seasonCmd.then(ClientCommands.literal(se.name().toLowerCase(java.util.Locale.ROOT)).executes(dev.purplepad.PurplePadClient.test(ctx -> {
					NucleusSeason.preview(season);
					KcaChat.feedback(ctx.getSource(), Component.literal(
						"\u00a7d[KCA] \u00a77Previewing \u00a7f" + season.label + "\u00a77 until you close the game."));
					return 1;
				})));
			}
			return ClientCommands.literal("kca")
                .then(nucleus)
                .then(ClientCommands.literal("pause").executes(ctx -> reply(ctx, SpeedrunManager.toggleDowntime())))
                .then(ClientCommands.literal("resume").executes(ctx -> reply(ctx, SpeedrunManager.resumeRun())))
                .then(runsCommands())
                .then(mapCommands())
                .then(ClientCommands.literal("stash").executes(ctx -> reply(ctx, StashHud.label())))
                .then(ClientCommands.literal("tools").then(ClientCommands.literal("scan").executes(ctx -> ToolSets.scan(ctx.getSource()::sendFeedback))))
                .then(ClientCommands.literal("speedrun")
                    .executes(ctx -> reply(ctx, "/kca speedrun start, pause, resume, stop, skip, status, pb, history. Use /kca runs to manage your total."))
                    .then(ClientCommands.literal("start").executes(KcaCommands::start))
                    .then(ClientCommands.literal("pause").executes(ctx -> reply(ctx, SpeedrunManager.toggleDowntime())))
                    .then(ClientCommands.literal("resume").executes(ctx -> reply(ctx, SpeedrunManager.resumeRun())))
                    .then(ClientCommands.literal("stop").executes(KcaCommands::stop))
                    .then(ClientCommands.literal("skip").executes(KcaCommands::skip))
                    .then(ClientCommands.literal("status").executes(ctx -> reply(ctx, "Timer: " + (SpeedrunManager.manuallyPaused() ? "PAUSED" : SpeedrunManager.state().name()) + ". " + SpeedrunManager.counter())))
                    .then(ClientCommands.literal("pb").executes(KcaCommands::printPersonalBest))
                    .then(ClientCommands.literal("history").executes(KcaCommands::history))
                    .then(runsCommands()))
                .then(ClientCommands.literal("test")
                    .then(ClientCommands.literal("debug").executes(dev.purplepad.PurplePadClient.test(KcaCommands::debug)))
                    .then(unlock).then(seasonCmd))
                .then(ClientCommands.literal("disableTabwarn").executes(KcaCommands::dismissTabWarn));
	}

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> runsCommands() {
        var runs = ClientCommands.literal("runs")
            .executes(ctx -> reply(ctx, SpeedrunManager.counter() + ". Commands: scan, add <number>, set <total>."))
            .then(ClientCommands.literal("scan").executes(ctx -> NucleusRunScan.scan(ctx.getSource())));
        for (String action : java.util.List.of("add", "set")) {
            boolean add = action.equals("add");
            runs.then(ClientCommands.literal(action).then(ClientCommands.argument("number",
                com.mojang.brigadier.arguments.LongArgumentType.longArg(add ? 1 : 0, Long.MAX_VALUE - 1))
                .executes(ctx -> {
                    long value = com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "number");
                    var store = NucleusMod.SPEEDRUN;
                    if (add && value >= Long.MAX_VALUE - store.displayedRuns()) return reply(ctx, "That total is too large.");
                    store.manualRunTotal = add ? store.displayedRuns() + value : value;
                    store.save();
                    return reply(ctx, SpeedrunManager.counter());
                })));
        }
        return runs;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> mapCommands() {
        var set=ClientCommands.literal("set");
        for (var place:HollowsMap.Place.values()) {
            set.then(ClientCommands.literal(place.name().toLowerCase(java.util.Locale.ROOT))
                .then(ClientCommands.argument("x",com.mojang.brigadier.arguments.IntegerArgumentType.integer(202,823))
                .then(ClientCommands.argument("y",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,255))
                .then(ClientCommands.argument("z",com.mojang.brigadier.arguments.IntegerArgumentType.integer(202,823))
                .executes(ctx->reply(ctx,HollowsMap.set(ctx.getSource().getClient(),place,
                    ctx.getArgument("x",Integer.class),ctx.getArgument("y",Integer.class),ctx.getArgument("z",Integer.class))))))));
        }
        return ClientCommands.literal("map").then(set).then(ClientCommands.literal("share")
            .executes(ctx->reply(ctx,HollowsMap.share(ctx.getSource().getClient()))));
    }

    private static int reply(CommandContext<FabricClientCommandSource> ctx, String message) {
        ctx.getSource().sendFeedback(Component.literal(message));
        return 1;
    }

    private static int start(CommandContext<FabricClientCommandSource> ctx) {
        if (!NucleusMod.CONFIG.kcaEnabled) return reply(ctx, "Enable KCA to start the speedrun timer.");
        if (!HollowsDetector.isInCrystalHollows()) return reply(ctx, "Start the Nucleus timer in the Crystal Hollows.");
        if (SpeedrunManager.state() == SpeedrunManager.State.RUNNING) return reply(ctx, "Your timer is already running.");
        if (!NucleusMod.CONFIG.speedrunEnabled) {
            NucleusMod.CONFIG.speedrunEnabled = true;
            NucleusMod.CONFIG.save();
        }
        SpeedrunManager.startRun(Minecraft.getInstance());
        return KcaChat.allowed() ? 1 : reply(ctx, "Nucleus speedrun started.");
    }

    private static int skip(CommandContext<FabricClientCommandSource> ctx) {
        if (SpeedrunManager.manuallyPaused()) return reply(ctx, "Resume your Nucleus run before skipping a split.");
        String head = SpeedrunManager.currentHead();
        if (head == null) return reply(ctx, "No active checkpoint.");
        SpeedrunManager.skipSplit(false);
        return reply(ctx, "Skipped " + NucleusMod.SPEEDRUN.displayName(head));
    }

    private static int printPersonalBest(CommandContext<FabricClientCommandSource> ctx) {
        long best = NucleusMod.SPEEDRUN.bestTotalMs;
        if (best < 0) return reply(ctx, personalBest());
        ctx.getSource().sendFeedback(Component.literal("Crystal Nucleus  ").withColor(0xFFB2D1)
            .append(Component.literal("PB  ").withColor(0xB0A6AD))
            .append(Component.literal(com.nucleus.SpeedrunStore.fmt(best)).withColor(0xFFE19C)));
        return 1;
    }

    public static String personalBest(){
        long best=NucleusMod.SPEEDRUN.bestTotalMs;
        return best<0?"No completed Nucleus run recorded yet.":"Nucleus PB: "+com.nucleus.SpeedrunStore.fmt(best);
    }

	private static int openGui(CommandContext<FabricClientCommandSource> ctx) {
		dev.purplepad.KcaMenu.openNucleus();
		return 1;
	}

	private static int history(CommandContext<FabricClientCommandSource> ctx) {
		SpeedrunManager.printHistory(ctx.getSource()::sendFeedback);
		return 1;
	}

	private static int stop(CommandContext<FabricClientCommandSource> ctx) {
		SpeedrunManager.resetRun(false);
        ctx.getSource().sendFeedback(Component.literal("Nucleus speedrun reset."));
		return 1;
	}

	private static int lobbyStats(CommandContext<FabricClientCommandSource> ctx) {
		SpeedrunManager.printLobbyStats(ctx.getSource()::sendFeedback);
		return 1;
	}

	/** /kca nucleus syncRuns: adopt the official run total, or say how. */
	private static int syncRuns(CommandContext<FabricClientCommandSource> ctx) {
		try {
			if (Achievements.syncRunsFromOpenMenu()) {
                if(!KcaChat.allowed())ctx.getSource().sendFeedback(Component.literal("Nucleus runs: "+NucleusMod.SPEEDRUN.completedRuns));
				return 1;
			}
		} catch (Exception ignored) {
		}
		KcaChat.feedback(ctx.getSource(), Component.literal(
			"§d[KCA] §7" + Achievements.SYNC_RUNS_HINT));
		return 1;
	}

	/** Dumps everything support would ask for: location, timers, pet, tracker, HUD state. */
	private static int debug(CommandContext<FabricClientCommandSource> ctx) {
		Minecraft client = Minecraft.getInstance();
		java.util.List<String> out = new java.util.ArrayList<>();
		out.add("§d[KCA] §6§lDebug");
		out.add("§7SkyBlock: " + yesNo(SkyBlockDetector.isOnSkyBlock())
			+ " §7| Hollows: " + yesNo(HollowsDetector.isInCrystalHollows()));
		HollowsDetector.tabArea(client).ifPresentOrElse(
			a -> out.add("§7Tab Area: §f" + a),
			() -> out.add("§7Tab Area: §c-"));
		out.add("§7Jungle Temple: " + yesNo(HollowsDetector.isInJungleTemple(client))
			+ " §7| Mines of Divan: " + yesNo(HollowsDetector.inMinesOfDivan(client)));
		if (client.player != null) {
			var pos = client.player.blockPosition();
			out.add("§7Pos: §f" + pos.getX() + " " + pos.getY() + " " + pos.getZ());
		}
		out.add("§7Waypoints: §f" + NucleusWaypoints.snapshot().size()
			+ " §7temple + §f" + NucleusWaypoints.customSnapshot().size() + " §7custom");
		out.add("§7Speedrun: §f" + SpeedrunManager.state()
			+ " §7(§f" + SpeedrunManager.splitIdx() + "§7/§f" + SpeedrunManager.splitCount() + "§7)"
			+ (SpeedrunManager.afkPaused() ? " §e[AFK]" : ""));
		out.add("§7Bal: §f" + BalTimer.displayString());
		out.add("§7Jackpot: " + (JackpotAnimation.isActive()
			? "§aactive §7(" + JackpotAnimation.type().displayName + ")" : "§8idle"));
		out.add("§7Pet widget: " + (PetAlert.widgetFound() ? "§afound" : "§cnot found")
			+ " §7| pet: §f" + (PetAlert.currentPet() == null ? "-" : PetAlert.currentPet()));
		long ago = BalWatch.secondsSinceSeen();
		out.add("§7Bal state: §f" + BalWatch.state()
			+ " §7| last seen: §f" + (ago < 0 ? "never" : ago + "s ago"));
		out.add("§7Scavenger held: §f" + Scavenger.heldTools()
			+ " §7| rate: §f" + String.format("%.1f", Scavenger.toolsPerHour()) + "/h"
			+ " §7| sets: §e" + Scavenger.setsCompleted());
		String day = LobbyDay.displayDay();
		out.add("§7Lobby: §f" + (day.isEmpty() ? "-" : day + " | " + LobbyDay.displayLobby()));
		for (String line : out) {
			KcaChat.feedback(ctx.getSource(), Component.literal(line));
		}
		int shown = 0;
		for (String line : HollowsDetector.sidebarLines(client)) {
			if (shown >= 6) {
				break;
			}
			String match = HollowsDetector.matches(line) ? "§a<==" : "§8--";
			String shortLine = line.length() > 50 ? line.substring(0, 50) : line;
			KcaChat.feedback(ctx.getSource(), Component.literal(match + " §f" + shortLine));
			shown++;
		}
		return 1;
	}

	/** Wire for the [Don't show again] button: no hand-typing. */
	private static int dismissTabWarn(CommandContext<FabricClientCommandSource> ctx) {
		NucleusMod.CONFIG.tabWarnEnabled = false;
		NucleusMod.CONFIG.save();
		KcaChat.feedback(ctx.getSource(),
			Component.literal("§d[KCA] §7the annoying /tab message will no longer bother you :c"));
		return 1;
	}

	private static int safe(CommandContext<FabricClientCommandSource> ctx) {
		boolean next = !NucleusMod.CONFIG.safeMode;
		NucleusMod.CONFIG.safeMode = next;
		NucleusMod.CONFIG.save();
		KcaChat.feedback(ctx.getSource(), Component.literal(
			"§d[KCA] §7Safe mode: " + (next ? "§aON §7(only /warp nucleus|cn)" : "§cOFF")));
		return 1;
	}

	private static String yesNo(boolean v) {
		return v ? "§ayes" : "§cno";
	}
}
