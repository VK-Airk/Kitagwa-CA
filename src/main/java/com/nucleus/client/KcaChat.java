package com.nucleus.client;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import com.nucleus.NucleusMod;

/** Single choke point for all mod chat output (quiet toggle). */
public final class KcaChat {
	private KcaChat() {
	}

	public static boolean allowed() {
		return NucleusMod.CONFIG.kcaEnabled && !NucleusMod.CONFIG.quietChat;
	}

	public static void chat(Minecraft client, Component text) {
		if (!allowed() || client == null || client.gui == null) {
			return;
		}
		client.gui.hud.getChat().addClientSystemMessage(text);
	}

	public static void feedback(FabricClientCommandSource src, Component text) {
		if (src == null) {
			return;
		}
		src.sendFeedback(text);
	}
}
