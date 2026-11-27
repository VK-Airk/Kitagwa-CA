package dev.purplepad;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nucleus.NucleusMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/** Segment boundaries observed in server chat. Only adjacent observed boundaries form a split. */
public final class LocalDungeonSplits {
    private static final String START="[NPC] Mort: Here, I found this map when I first entered the dungeon.";
    private static final Map<String,String> PHASES=Map.of(
        "[BOSS] Storm: Pathetic Maxor, just like expected.","Storm",
        "[BOSS] Goldor: Who dares trespass into my domain?","Terminals",
        "The Core entrance is opening!","Goldor",
        "[BOSS] Necron: You went further than any human before, congratulations.","Necron",
        "[BOSS] Necron: All this, for nothing...","Dragons",
        "[BOSS] Sadan: ENOUGH!","Giants",
        "[BOSS] Sadan: You did it. I understand now, you have earned my respect.","Sadan");
    private static final java.util.Set<String> WATCHER_OPEN=java.util.Set.of(
        "The BLOOD DOOR has been opened!",
        "[BOSS] The Watcher: Ah, we meet again...",
        "[BOSS] The Watcher: Ah, you've finally arrived.",
        "[BOSS] The Watcher: Congratulations, you made it through the Entrance.",
        "[BOSS] The Watcher: I'm starting to get tired of seeing you around here...",
        "[BOSS] The Watcher: Oh.. hello?",
        "[BOSS] The Watcher: So you made it this far... interesting.",
        "[BOSS] The Watcher: Things feel a little more roomy now, eh?",
        "[BOSS] The Watcher: You've managed to scratch and claw your way here, eh?");
    private static final java.util.Set<String> BOSS_ENTRY=java.util.Set.of(
        "[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable.",
        "[BOSS] Livid: Welcome, you've arrived right on time. I am Livid, the Master of Shadows.",
        "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!",
        "[BOSS] Sadan: So you made it all the way here... Now you wish to defy me? Sadan?!",
        "[BOSS] Scarf: This is where the journey ends for you, Adventurers.",
        "[BOSS] The Professor: I was burdened with terrible news recently...",
        "[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!");
    static boolean isBossEntry(String line){return BOSS_ENTRY.contains(line);}
    private static final List<String> EARLY=List.of("Blood rush","Blood clear","Portal","Boss");
    private static final List<String> SIX=List.of("Blood rush","Blood clear","Portal","Boss","Giants","Sadan");
    private static final List<String> SEVEN=List.of("Blood rush","Blood clear","Portal","Boss","Storm","Terminals","Goldor","Necron");
    private static final List<String> MASTER_SEVEN=List.of("Blood rush","Blood clear","Portal","Boss","Storm","Terminals","Goldor","Necron","Dragons");
    private static final java.util.concurrent.ExecutorService IO=Executors.newSingleThreadExecutor(task->{
        var thread=new Thread(task,"KCA dungeon records");thread.setDaemon(true);return thread;
    });
    static final class Run {
        final Map<String,Long> boundaries=new HashMap<>();
        String floor="";
        void line(String line,long now){
            String reported=DungeonLocation.reportedFloor(line);
            if(!reported.isEmpty()){floor=reported;return;}
            String phase=PHASES.get(line);
            if(phase==null){
                if(line.equals(START))phase="Blood rush";
                else if(line.equalsIgnoreCase("The BLOOD DOOR has been opened!") ||
                    WATCHER_OPEN.contains(line))phase="Blood clear";
                else if(line.equals("[BOSS] The Watcher: You have proven yourself. You may pass."))phase="Portal";
                else if(isBossEntry(line))phase="Boss";
                else if(DungeonLocation.reportedTime(line)>0)phase="Finish";
            }
            if(phase!=null)boundaries.putIfAbsent(phase,now);
        }
        Map<String,Long> segments(){
            Map<String,Long> result=new LinkedHashMap<>();
            var names=phases(floor);
            for(int i=0;i<names.size();i++){
                String phase=names.get(i);
                String next=i+1<names.size()?names.get(i+1):floor.equals("E")?"Portal":"Finish";
                Long start=boundaries.get(phase),end=boundaries.get(next);
                if(start!=null && end!=null && end>start)result.put(label(floor,phase),end-start);
            }
            return result;
        }
    }
    private static List<String> phases(String floor){
        return switch(floor){case "E" -> List.of("Blood rush","Blood clear"); case "M7" -> MASTER_SEVEN;
            case "F7" -> SEVEN;case "F6","M6" -> SIX;default -> EARLY;};
    }
    private static String label(String floor,String phase){
        if(!phase.equals("Boss"))return phase;
        if(floor.endsWith("7"))return "Maxor";
        if(floor.endsWith("6"))return "Terracottas";
        return "Boss";
    }

    private static Run run;
    private static Object level;
    static void reset(){run=null;level=null;}
    static void onChat(String message){
        var mc=Minecraft.getInstance();
        if(level!=mc.level)reset();
        if(mc.player==null || mc.level==null)return;
        if(message.indexOf('\n')>=0){for(String line:message.split("\\R"))onChat(line);return;}
        String line=message.strip();
        if(line.equals(START)){run=new Run();level=mc.level;}
        if(run==null)return;
        run.line(line,System.nanoTime()/1_000_000L);
        if(run.floor.isEmpty() || !run.boundaries.containsKey("Finish"))return;
        var segments=run.segments();
        UUID player = mc.player.getUUID();
        var file=file(player);
        String floor=run.floor;
        reset();
        if(!segments.isEmpty())IO.execute(()->{
            try {
                boolean improved = save(file, floor, segments);
                if (KcaEdition.GENERAL) mc.execute(() -> {
                    if (mc.player == null || !player.equals(mc.player.getUUID())) return;
                    if (improved) com.nucleus.client.Achievements.unlockFun("kca_d_split_gold");
                    if (segments.size() == phases(floor).size()) com.nucleus.client.Achievements.unlockFun("kca_d_split_card");
                    if (segments.getOrDefault("Blood rush", Long.MAX_VALUE) <= 25_000)
                        com.nucleus.client.Achievements.unlockFun("kca_d_split_rush");
                });
            }
            catch(IOException | RuntimeException e){NucleusMod.LOGGER.warn("Could not save local dungeon splits",e);}
        });
    }
    private static Path file(UUID player){
        return FabricLoader.getInstance().getConfigDir().resolve("kca/dungeon-splits").resolve(player+".json");
    }
    private static JsonObject read(Path path)throws IOException{
        if(Files.notExists(path))return new JsonObject();
        try(var reader=Files.newBufferedReader(path)){return JsonParser.parseReader(reader).getAsJsonObject();}
    }
    static boolean save(Path file,String floor,Map<String,Long> splits)throws IOException{
        var records=read(file);
        var best=records.has(floor)?records.getAsJsonObject(floor):new JsonObject();
        boolean changed = false, improved = false;
        for (var entry : splits.entrySet()) {
            String name = entry.getKey();
            long ms = entry.getValue();
            if (best.has(name)) {
                if (ms >= best.get(name).getAsLong()) continue;
                improved = true;
            }
            best.addProperty(name, ms);
            changed = true;
        }
        if (!changed) return false;
        records.add(floor,best);
        Files.createDirectories(file.getParent());
        var temp=Files.createTempFile(file.getParent(),"splits-",".tmp");
        try{
            Files.writeString(temp,records.toString());
            try {Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(java.nio.file.AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
        return improved;
    }
    public static CompletableFuture<String> report(UUID player,String floor,boolean compact){
        return CompletableFuture.supplyAsync(()->{
            try {
                var records=read(file(player));
                if(!records.has(floor))return null;
                return formatReport(floor, records.getAsJsonObject(floor), compact);
            }catch(IOException | RuntimeException e){
                NucleusMod.LOGGER.warn("Could not read local dungeon splits",e);
                return "Could not read your KCA split records.";
            }
        },IO);
    }
    static String formatReport(String floor, JsonObject times, boolean compact) {
        var phases = phases(floor);
        StringBuilder rows = new StringBuilder(floor + " / KCA best splits");
        long sum = 0;
        int found = 0;
        for (String phase : phases) {
            String name = label(floor, phase);
            long ms = times.has(name) ? times.get(name).getAsLong() : 0;
            if (ms > 0) { sum += ms; found++; }
            if (!compact) rows.append("\n").append(name).append("  ").append(ms > 0 ? clock(ms) : "--");
        }
        String result = found == phases.size() ? clock(sum) : found + "/" + phases.size() + " segments recorded";
        if (compact) return floor + " theoretical best (KCA): " + result;
        return rows.append("\nSum of best segments  ").append(result).toString();
    }

    private static String clock(long ms){return String.format(Locale.ROOT,"%d:%02d.%03d",ms/60000,ms/1000%60,ms%1000);}
    private LocalDungeonSplits(){}
}
