package com.nucleus.client;

import com.nucleus.NucleusMod;
import net.minecraft.client.Minecraft;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class NucleusPartyAlerts {
    private static final Pattern RARE = Pattern.compile("^(?:(?:VERY|CRAZY|INSANE|EXTREMELY) )?RARE DROP!\\s+(.+?)(?:\\s+\\(\\+[^()]*Magic Find\\))?[!.]?$", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Long> recent = new HashMap<>();
    private static boolean atBal;
    private static long lastBal;

    static void reset() {
        atBal = false;
        recent.clear();
    }

    static void tick(Minecraft mc) {
        if (!NucleusMod.CONFIG.balPartyAlert) {
            atBal = false;
            return;
        }
        boolean inside = HollowsDetector.isInCrystalHollows() && HollowsDetector.inKhazadDum(mc);
        long now = System.currentTimeMillis();
        if (inside && !atBal && now - lastBal >= 30000) {
            send(mc, NucleusMod.CONFIG.balPartyText, "");
            lastBal = now;
        }
        atBal = inside;
    }

    static String personalDrop(String stripped) {
        String line = stripped.strip();
        if (line.equalsIgnoreCase("A Bal pet has dropped!") || line.equalsIgnoreCase("A Bal pet dropped!")) return "Bal Pet";
        var match = RARE.matcher(line);
        return match.matches() ? match.group(1).strip() : null;
    }

    static void drop(String item) {
        if (item == null || !NucleusMod.CONFIG.rarePartyAlert || !SkyBlockDetector.isOnSkyBlock()) return;
        long now = System.currentTimeMillis();
        recent.values().removeIf(time -> now - time >= 3000);
        if (recent.putIfAbsent(item.toLowerCase(Locale.ROOT), now) == null)
            send(Minecraft.getInstance(), NucleusMod.CONFIG.rarePartyText, item);
    }

    private static void send(Minecraft mc, String template, String item) {
        if (template == null || template.isBlank() || mc.player == null || mc.getConnection() == null) return;
        var pos = mc.player.blockPosition();
        String message = template.replace("{coords}", pos.getX() + " " + pos.getY() + " " + pos.getZ())
            .replace("{player}", mc.player.getGameProfile().name()).replace("{drop}", item)
            .replaceAll("[\\p{Cntrl}§]", "").strip();
        if (!message.isEmpty()) mc.getConnection().sendCommand("pchat " + message.substring(0, Math.min(message.length(), 248)));
    }
}
