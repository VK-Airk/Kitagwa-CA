package dev.purplepad;

import java.util.List;
import java.util.Map;

final class SettingLabels {
    private SettingLabels() { }
    static final Map<String,List<String>> NUCLEUS = Map.ofEntries(
        Map.entry("Jungle Temple",List.of("Waypoint mode", "Offsets", "Set now", "Clear", "Waypoints visible through walls", "Dim waypoints out of etherwarp range", "Waypoint text labels", "Link jungle waypoints", "Show Waypoint 1", "Show Waypoint 2 (+29,-32,+65)", "Show Waypoint 3 (+29,-32,+48)")),
        Map.entry("Custom waypoints",List.of("Set custom waypoint", "Show custom waypoints", "Remove last waypoint", "Remove all waypoints", "Export waypoints", "Import waypoints")),
        Map.entry("Tool Finder",List.of("Divan chest locator", "Chest distance text", "Mute metal detector", "Scavenger tracker")),
        Map.entry("NPC highlights",List.of("Highlight NPCs", "Clear markers")),
        Map.entry("HUD & alerts",List.of("Crystal Hollows map","Wrong pet alert", "Low tools alert", "Bad lobby alert", "Lobby day display", "Bal timer", "Timer background", "Bal Detector", "Bal pet drop animation", "Test", "Terror stacks overlay")),
        Map.entry("Customization",List.of("Seasonal effects", "Themed gambling visuals")),
        Map.entry("CN Gambling",List.of("Preview drop", "Reset to defaults", "Gambling animation", "Animation - Wheel, Trial vault, Crystal vault, Briefcase slots", "Ending", "Confetti", "Test animation", "Hide bundle")),
        Map.entry("Legacy wheel",List.of("Original wheel arrow","Gold pointer mount","Wheel sparkles","Zoom level")),
        Map.entry("Vaults",List.of("Cool trial vault accents","Vault key loops","Vault light trails","Vault lock sounds")),
        Map.entry("Drops",List.of("Animate every bundle","Divan's Alloy","Precious Pearl","Jaderald")),
        Map.entry("Timing",List.of("Opening sequence", "Power and luck rolls", "Opening speed", "Game speed", "Final reveal speed", "Quick-spin hotkey")),
        Map.entry("Sounds",List.of("Overwrite Minecraft sound menu", "Custom sound file", "Sound volume", "Gambling animation volume", "Achievement unlock volume", "Preview", "Browse...", "King Yolkar's stench", "Apparatus delivered", "Keeper tool returned", "Jungle Key delivered", "Bal defeated", "Crystal obtained", "Crystal placed", "Divan treasure chest")),
        Map.entry("Speedruns",List.of("Start distance", "Safe mode", "Auto safe mode", "Runs in lobby", "Speedrun timer", "Show summary after lobby leave", "Skip split", "Reset run"))
    );
}
