package com.nucleus.client;

import com.google.gson.JsonObject;
import com.nucleus.NucleusMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

final class ToolSets {
    private static final List<String> TOOLS = List.of("DWARVEN_LAPIS_SWORD","DWARVEN_GOLD_HAMMER",
        "DWARVEN_DIAMOND_AXE","DWARVEN_EMERALD_HAMMER");
    private static long revision;
    private static long lastBundle;

    static void set(long sets) {
        revision++;
        NucleusMod.SPEEDRUN.remainingToolSets = sets;
        NucleusMod.SPEEDRUN.save();
    }

    static void completedBundle() {
        long now = System.currentTimeMillis();
        if (now - lastBundle < 2000) return;
        lastBundle = now;
        if (NucleusMod.SPEEDRUN.remainingToolSets > 0) set(NucleusMod.SPEEDRUN.remainingToolSets - 1);
        else revision++;
    }

    static int scan(Consumer<Component> feedback) {
        long before = revision;
        return NucleusRunScan.lookup(feedback, profile -> {
            var inventory = profile.member().getAsJsonObject("inventory");
            if (inventory == null || !(inventory.get("backpack_contents") instanceof JsonObject pages))
                throw new NucleusRunScan.ScanError("Backpack data is unavailable. Check Inventory API access in SkyBlock settings.");
            return countSets(pages);
        }, count -> {
            if (before != revision) return "Your tool count changed during the scan. Scan again to update it.";
            set(count);
            return "Backpack scan — " + NucleusMod.SPEEDRUN.toolSetLabel() + " complete.";
        });
    }

    static long countSets(JsonObject pages) {
        long[] quantities = new long[TOOLS.size()];
        for (var entry : pages.entrySet()) {
            try {
                String data = entry.getValue().getAsJsonObject().get("data").getAsString();
                if (data.length() > 4_000_000) throw new IOException();
                var bytes = Base64.getDecoder().decode(data);
                var root = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(8L*1024*1024));
                var items = root.getList("i").orElseThrow();
                for (var tag : items) {
                    if (!(tag instanceof CompoundTag stack)) throw new IOException();
                    var attrs = stack.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes");
                    String id = attrs.getStringOr("id","");
                    if (id.isEmpty()) id = stack.getCompoundOrEmpty("components")
                        .getCompoundOrEmpty("minecraft:custom_data").getStringOr("id","");
                    int tool = TOOLS.indexOf(id);
                    if (tool >= 0) {
                        int count = stack.getIntOr("Count",stack.getIntOr("count",1));
                        if (count < 1) throw new IOException();
                        quantities[tool] += count;
                    }
                }
            } catch (IOException | RuntimeException invalid) {
                throw new NucleusRunScan.ScanError("Could not read backpack page " + entry.getKey() + ".");
            }
        }
        return java.util.Arrays.stream(quantities).min().orElse(0);
    }
}
