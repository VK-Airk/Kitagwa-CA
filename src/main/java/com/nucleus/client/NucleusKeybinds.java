package com.nucleus.client;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.platform.InputConstants;

import com.nucleus.NucleusMod;

/**
 * Customizable hotkeys: one captures the 3 Temple waypoints,
 * one captures the custom waypoint under the player's feet.
 * Registered as vanilla KeyMappings so they can be rebound both in
 * vanilla Controls and inside the config GUI.
 *
 * Manual waypoint captures work in any loaded world.
 */
public final class NucleusKeybinds {
	public static final KeyMapping.Category KCA_CATEGORY =
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath("kca", "nucleus"));

	public static KeyMapping SET_WAYPOINTS;
	public static KeyMapping SET_CUSTOM;
	/** Unbound by default; removes the most recent custom waypoint. */
	public static KeyMapping REMOVE_LAST_CUSTOM;
	/** Unbound by default; jumps the gambling wheel to its last second. */
	public static KeyMapping QUICK_SPIN, SKIP_SPLIT;

	private NucleusKeybinds() {
	}

	public static void register() {
		SET_WAYPOINTS = KeyMappingHelper.registerKeyMapping(
			new KeyMapping(
				"key.nucleus.set_waypoints",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_G,
				KCA_CATEGORY
			)
		);
		SET_CUSTOM = KeyMappingHelper.registerKeyMapping(
			new KeyMapping(
				"key.nucleus.set_custom_waypoint",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_H,
				KCA_CATEGORY
			)
		);
		REMOVE_LAST_CUSTOM = KeyMappingHelper.registerKeyMapping(
			new KeyMapping(
				"key.nucleus.remove_last_custom",
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(),
				KCA_CATEGORY
			)
		);
        SKIP_SPLIT=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nucleus.skip_split",InputConstants.Type.KEYSYM,InputConstants.UNKNOWN.getValue(),KCA_CATEGORY));
		QUICK_SPIN = KeyMappingHelper.registerKeyMapping(
			new KeyMapping(
				"key.nucleus.quick_spin",
				InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(),
				KCA_CATEGORY
			)
		);
	}

	/** Called every client tick; captures waypoints when a hotkey is pressed. */
	public static void tick(net.minecraft.client.Minecraft client) {
		if (QUICK_SPIN != null) {
			while (QUICK_SPIN.consumeClick()) {
				JackpotAnimation.quickSpin();
			}
		}
		if (client.player == null || client.level == null) {
			return;
		}
		if (!NucleusMod.CONFIG.kcaEnabled) {
			return;
		}
        if(SKIP_SPLIT.consumeClick())SpeedrunManager.skipSplit();
    }

    static void tickWaypoints(net.minecraft.client.Minecraft client){
        if(client.player==null || client.level==null || !NucleusMod.CONFIG.kcaEnabled)return;
		if (SET_WAYPOINTS != null) {
			if (NucleusMod.CONFIG.templeAutoPlace) {
				// Automatic mode places them itself: swallow presses so no
				// stale click fires a manual capture later.
				while (SET_WAYPOINTS.consumeClick()) {
				}
			} else {
				while (SET_WAYPOINTS.consumeClick()) {
					captureFromPlayer(client);
				}
			}
		}
		if (SET_CUSTOM != null) {
			while (SET_CUSTOM.consumeClick()) {
				captureCustomFromPlayer(client);
			}
		}
		if (REMOVE_LAST_CUSTOM != null) {
			while (REMOVE_LAST_CUSTOM.consumeClick()) {
				removeLastCustom(client);
			}
		}
	}

	public static boolean captureFromPlayer(net.minecraft.client.Minecraft client) {
		return captureFromPlayer(client, NucleusMod.CONFIG.useReferenceOffsets, text -> KcaChat.chat(client, text));
	}

	public static boolean captureFromPlayer(net.minecraft.client.Minecraft client, boolean rightGuardian,
			java.util.function.Consumer<Component> feedback) {
		if (!NucleusMod.CONFIG.kcaEnabled) {
			feedback.accept(Component.literal("Enable KCA in General settings to place waypoints."));
			return false;
		}
		if (client.player == null || client.level == null) {
            feedback.accept(Component.literal("Enter a world to place waypoints."));
            return false;
        }
        var origin = client.player.blockPosition();
		NucleusWaypoints.setFromOrigin(origin, rightGuardian);
		NucleusLobbyWatcher.onCapture();
		NucleusMod.LOGGER.info("Temple waypoints set from {}", origin);
		feedback.accept(Component.literal(
			"§d[KCA] §fWaypoints set from §e" + origin.getX() + ", " + origin.getY() + ", " + origin.getZ()));
		for (var wp : NucleusWaypoints.snapshot()) {
			feedback.accept(Component.literal(
				"§7- §f" + wp.label() + " §7at §e"
					+ wp.pos().getX() + ", " + wp.pos().getY() + ", " + wp.pos().getZ()));
		}
		return true;
	}

	public static boolean captureCustomFromPlayer(net.minecraft.client.Minecraft client) {
		if (!NucleusMod.CONFIG.kcaEnabled || client.player == null || client.level == null)return false;
        var feetBelow = client.player.blockPosition().below();
		NucleusWaypoints.addCustom(feetBelow);
		NucleusLobbyWatcher.onCapture();
		NucleusMod.LOGGER.info("Custom waypoint set at {}", feetBelow);
		KcaChat.chat(client, Component.literal(
			"§d[KCA] §fCustom waypoint set at §e"
				+ feetBelow.getX() + ", " + feetBelow.getY() + ", " + feetBelow.getZ()
				+ " §7(" + NucleusWaypoints.customCount() + "/100)"));
		return true;
	}

	public static void removeLastCustom(net.minecraft.client.Minecraft client) {
		var removed = NucleusWaypoints.removeLastCustom();
		if (removed == null) {
			KcaChat.chat(client,
				Component.literal("§d[KCA] §7No custom waypoints to remove."));
			return;
		}
		NucleusMod.LOGGER.info("Custom waypoint removed at {}", removed.pos());
		KcaChat.chat(client, Component.literal(
			"§d[KCA] §fRemoved " + removed.label() + " at §e"
				+ removed.pos().getX() + ", " + removed.pos().getY() + ", " + removed.pos().getZ()));
	}

	public static Component boundKeyLabel() {
		if (SET_WAYPOINTS == null) {
			return Component.literal("unbound");
		}
		return SET_WAYPOINTS.getTranslatedKeyMessage();
	}

	public static Component customBoundKeyLabel() {
		if (SET_CUSTOM == null) {
			return Component.literal("unbound");
		}
		return SET_CUSTOM.getTranslatedKeyMessage();
	}

	public static Component quickSpinBoundKeyLabel() {
		if (QUICK_SPIN == null) {
			return Component.literal("unbound");
		}
		return QUICK_SPIN.getTranslatedKeyMessage();
	}

	public static Component removeLastBoundKeyLabel() {
		if (REMOVE_LAST_CUSTOM == null) {
			return Component.literal("unbound");
		}
		return REMOVE_LAST_CUSTOM.getTranslatedKeyMessage();
	}
}
