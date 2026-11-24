package com.nucleus.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

import com.nucleus.NucleusMod;
import dev.purplepad.KcaEdition;

public class NucleusClient implements ClientModInitializer {
    private static void addHud(String id, HudElement element) {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NucleusMod.MOD_ID, id), (graphics, delta) -> {
            if (dev.purplepad.PurplePadClient.hudEnabled() && !(Minecraft.getInstance().gui.screen() instanceof MoveHudScreen))
                element.extractRenderState(graphics, delta);
        });
    }

    @Override
	public void onInitializeClient() {
        if(KcaEdition.NUCLEUS || KcaEdition.DUNGEONS || KcaEdition.CHAT || KcaEdition.GENERAL){
            ClientReceiveMessageEvents.GAME.register((message,overlay)->{
                if(!overlay)dispatchGameMessage(message.getString());
            });
            ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
            ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->SkyBlockDetector.reset());
        }
        if(KcaEdition.GENERAL)Achievements.migrateOldAchievements();
        if(!KcaEdition.NUCLEUS)return;
        NucleusKeybinds.register();
        RunInventoryControls.register();
        addHud("hollows-map", (g,d)->HollowsMap.render(g));
        addHud("stash", (g,d)->StashHud.draw(g));
        addHud("tool-sets", (g,d)->SpeedrunHud.drawToolSets(g));
        addHud("run-goal", (g,d)->SpeedrunHud.drawGoal(g));
        addHud("crystal-checklist", (g,d)->SpeedrunHud.drawCrystals(g));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SkyBlockDetector.reset();
			resetLobbyState();
			if (NucleusMod.CONFIG.safeMode) {
				NucleusMod.CONFIG.safeMode = false;
				NucleusMod.CONFIG.save();
				KcaChat.chat(client, net.minecraft.network.chat.Component.literal(
					"§d[KCA] §7Safe mode §cOFF §7(left lobby)."));
			}
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			resetLobbyState();
			if (NucleusMod.CONFIG.safeMode) {
				NucleusMod.CONFIG.safeMode = false;
				NucleusMod.CONFIG.save();
			}
			
		});

		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if(!NucleusMod.CONFIG.kcaEnabled)return InteractionResult.PASS;
			try {
				NpcTradeWatch.onInteract(entity);
			} catch (Exception ignored) {
			}
			return InteractionResult.PASS;
		});
		// Handovers also work with a left-click: same touch tracking.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if(!NucleusMod.CONFIG.kcaEnabled)return InteractionResult.PASS;
			try {
				NpcTradeWatch.onInteract(entity);
			} catch (Exception ignored) {
			}
			return InteractionResult.PASS;
		});
		// Shuriken throws (right-click): name-matched so any shuriken counts.
		UseItemCallback.EVENT.register((player, world, hand) -> {
            if(!NucleusMod.CONFIG.kcaEnabled)return InteractionResult.PASS;
			try {
				var stack = player.getItemInHand(hand);
				if (stack != null && !stack.isEmpty()) {
					String name = stack.getHoverName().getString().toLowerCase();
					if (name.contains("shuriken")
						&& HollowsDetector.inKhazadDum(Minecraft.getInstance())) {
						Achievements.unlockFun("nt");
					}
				}
			} catch (Exception ignored) {
			}
			return InteractionResult.PASS;
		});


		ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if(!NucleusMod.CONFIG.kcaEnabled)return;
			try {
				MobHighlight.onEntityLoad(entity, world);
			} catch (Exception ignored) {
			}
		});

		addHud("bal-timer", BalTimerHud.INSTANCE);
		addHud("jackpot", JackpotHud.INSTANCE);
		addHud("speedrun", SpeedrunHud.INSTANCE);
		addHud("lobby-day", LobbyDayHud.INSTANCE);
		addHud("scavenger", ScavengerHud.INSTANCE);
		addHud("terror-stacks", TerrorStacksHud.INSTANCE);
		addHud("alerts", AlertHud.INSTANCE);
		addHud("dye", DyeHud.INSTANCE);
		addHud("bal-pet", BalPetDrop.INSTANCE);

		NucleusWaypointRenderer.register();
		HideBundleBox.register();
		MobHighlightRenderer.register();
		DivanChestRenderer.register();

		
	}

	/** Per-lobby state: wiped on both join and disconnect. */
	private static boolean wasOnSkyBlock;
    public static void resetLobbyState() {
        NucleusPartyAlerts.reset();
        StashHud.reset();
        wasOnSkyBlock=false;
		HollowsDetector.reset();
		LobbyDay.reset();
		NucleusWaypoints.clear();
		NucleusLobbyWatcher.onLeave();
		JackpotDetector.reset();
		JackpotAnimation.stop();
		TerrorStacks.reset();
		BalTimer.reset();
		BalWatch.resetLobby();
		SpeedrunManager.onLeave();
		Scavenger.onLeave();
		PetAlert.resetLobby();
		CrystalTracker.reset();
		MobHighlight.reset();
		MobMarkers.clear();
		NpcTradeWatch.reset();
		DyeCelebration.reset();
		DivanDetectorSolver.onLeave();
	}

	/**
	 * Single choke point for game chat: the packet mixin (pre-filter) and the
	 * Fabric event (normal path) both land here. Same text twice within half
	 * a second is the same server line seen twice — process once so counters
	 * (placements) and sounds never double-fire.
	 */
	private static String lastDispatched = "";
	private static long lastDispatchedAt = 0L;

	public static void dispatchGameMessage(String text) {
		if (!NucleusMod.CONFIG.kcaEnabled || text == null) {
			return;
		}
		long now = System.currentTimeMillis();
		synchronized (NucleusClient.class) {
			if (text.equals(lastDispatched) && now - lastDispatchedAt < 500L) {
				return;
			}
			lastDispatched = text;
			lastDispatchedAt = now;
		}
		// One guard per handler: a failure in one (say the speedrun split)
		// must never swallow the others (the objective sounds after it).
		if(KcaEdition.DUNGEONS)guarded("Dungeon messages", () -> dev.purplepad.PurplePadClient.onMessage(text));
		if (KcaEdition.CHAT && NucleusMod.CONFIG.partyChatEnabled) guarded("Party commands", () -> dev.purplepad.PartyChat.receive(text));
		if(KcaEdition.GENERAL || KcaEdition.NUCLEUS)guarded("Achievements", () -> Achievements.onGameMessage(text));
        if(!KcaEdition.NUCLEUS)return;
        StashHud.chat(text);
        guarded("Hollows map", () -> HollowsMap.chat(text));
		guarded("BalTimer", () -> BalTimer.onGameMessage(text));
		guarded("BalWatch", () -> BalWatch.onGameMessage(text));
		guarded("BadLobbyAlert", () -> BadLobbyAlert.onGameMessage(text));
		guarded("JackpotDetector", () -> JackpotDetector.onGameMessage(text));
		guarded("SpeedrunManager", () -> SpeedrunManager.onGameMessage(text));

		guarded("CrystalTracker", () -> CrystalTracker.onGameMessage(text));
		guarded("ObjectiveSounds", () -> ObjectiveSounds.onGameMessage(text));
		guarded("DivanDetectorSolver", () -> DivanDetectorSolver.onGameMessage(text));
		guarded("PetAlert", () -> PetAlert.onGameMessage(text));
	}

	private static void guarded(String name, Runnable handler) {
		try {
			handler.run();
		} catch (Exception e) {
			NucleusMod.LOGGER.warn("{} chat handler failed", name, e);
		}
	}

	private void onTick(Minecraft client) {
		if (KcaEdition.NUCLEUS && client.level != null && client.player != null) {
			JackpotDetector.tickForced();
            if (JackpotAnimation.isActive()) JackpotAnimation.tick(client);
		}
		if (!NucleusMod.CONFIG.kcaEnabled) return;
        SkyBlockDetector.tick(client);
        if(!KcaEdition.NUCLEUS){
            if(KcaEdition.GENERAL && SkyBlockDetector.isOnSkyBlock())Achievements.tick(client);
            return;
        }
        if(!SkyBlockDetector.isOnSkyBlock()){
            if(wasOnSkyBlock)resetLobbyState();
            NucleusLobbyWatcher.tick(client);
            NucleusKeybinds.tickWaypoints(client);
            return;
        }
        if(!wasOnSkyBlock){wasOnSkyBlock=true;DropPrices.refreshIfStale();}
		HollowsDetector.tick(client);
        HollowsMap.sendNext(client);
        NucleusPartyAlerts.tick(client);
		LobbyDay.tick(client);
		BadLobbyAlert.tick(client);
		NucleusKeybinds.tick(client);
		NucleusLobbyWatcher.tick(client);
        NucleusKeybinds.tickWaypoints(client);
		BalTimer.tick(client);
		BalWatch.tick(client);
		SpeedrunManager.tick(client);
		Scavenger.tick(client);
		PetAlert.tick(client);
		Achievements.tick(client);
		MobHighlight.tick(client);
		NpcTradeWatch.tick(client);
		DivanDetectorSolver.tick(client);
		// No outside-GUI text besides waypoints + timers + jackpot by design.
	}
}
