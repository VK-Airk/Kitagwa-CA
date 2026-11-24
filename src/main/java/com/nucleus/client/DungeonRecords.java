package com.nucleus.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nucleus.NucleusMod;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class DungeonRecords {
    private static final String FISH = "fishmod-dungeons";

    public static int scan(Consumer<Component> output) {
        return NucleusRunScan.lookup(message -> {
            String text = message.getString();
            output.accept(text.startsWith("Dungeon PBs / ") ? styledReport(text) : message);
        },p->profileTimes(p.name(),p.member()),text->text,DungeonRecords::fishLookup);
    }

    private static CompletableFuture<String> fishLookup(String playerName) {
        var result = new CompletableFuture<String>();
        try {
            var api = Class.forName("fishmod.utils.HypixelApi");
            var callback = Class.forName("fishmod.utils.HypixelApi$DungeonDataCallback");
            var listener = Proxy.newProxyInstance(callback.getClassLoader(),new Class<?>[]{callback},(proxy,method,args) -> {
                if (method.getDeclaringClass()==Object.class) return switch(method.getName()) {
                    case "equals" -> proxy==args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> "KCA dungeon PB callback";
                };
                try { result.complete(fishTimes(args[0])); }
                catch (ReflectiveOperationException | RuntimeException e) { result.completeExceptionally(e); }
                return null;
            });
            api.getMethod("getByNameSilent",String.class,callback).invoke(null,playerName,listener);
        } catch (ReflectiveOperationException | LinkageError e) { result.completeExceptionally(e); }
        return result;
    }

    private static String fishTimes(Object data) throws ReflectiveOperationException {
        var type=data.getClass();
        if(type.getField("failed").getBoolean(data))throw new NucleusRunScan.ScanError("FishMod could not fetch this profile.");
        var rows=new ArrayList<String>();
        for(String group : new String[]{"cataPbs","masterPbs"}) {
            boolean master=group.equals("masterPbs");
            var times=(String[])type.getField(group).get(data);
            if(times==null)continue;
            for(int i=master?1:0;i<Math.min(8,times.length);i++) {
                if(times[i]!=null && !times[i].isBlank())rows.add(floor(master,i)+"  "+times[i]);
            }
        }
        return rows.isEmpty()?"FishMod found no ranked dungeon PBs on your active profile.":
            "Dungeon PBs / FishMod (best S+, then S)\n"+String.join("\n",rows);
    }

    static String profileTimes(String profile,JsonObject member) {
        var types=child(child(member,"dungeons"),"dungeon_types");
        var rows=new ArrayList<String>();
        for(boolean master:new boolean[]{false,true}) {
            var dungeon=child(types,master?"master_catacombs":"catacombs");
            for(int i=master?1:0;i<=7;i++) {
                var scores=new ArrayList<String>();
                for(String rank:new String[]{"fastest_time_s_plus","fastest_time_s","fastest_time"}) {
                    var values=child(dungeon,rank);
                    var time=values==null?null:values.get(Integer.toString(i));
                    if(time==null || !time.isJsonPrimitive() || !time.getAsJsonPrimitive().isNumber())continue;
                    long ms=time.getAsLong();
                    if(ms>0)scores.add((rank.endsWith("plus")?"S+ ":rank.endsWith("_s")?"S ":"Any ")+clock(ms));
                }
                if(!scores.isEmpty())rows.add(floor(master,i)+"  "+String.join("  |  ",scores));
            }
        }
        return rows.isEmpty()?"No recorded dungeon PBs on "+profile+".":"Dungeon PBs / "+profile+"\n"+String.join("\n",rows);
    }
    private static JsonObject child(JsonObject obj,String key) {
        return obj!=null && obj.has(key) && obj.get(key).isJsonObject()?obj.getAsJsonObject(key):null;
    }
    private static String floor(boolean master,int i) { return i==0?"Entrance":(master?"M":"F")+i; }
    static String clock(long ms) { return String.format(Locale.ROOT,"%d:%02d.%03d",ms/60000,ms/1000%60,ms%1000); }
    private static int say(Consumer<Component> out,String text) {out.accept(Component.literal(text));return 1;}

    private static Component styledReport(String text) {
        String[] lines = text.split("\n");
        var result = Component.literal(lines[0]).withColor(0xFFB2D1);
        String lastGroup = "";
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int divider = line.indexOf("  ");
            String label = divider < 0 ? line : line.substring(0, divider);
            if (lines[0].startsWith("Dungeon PBs / ")) {
                String group = label.startsWith("M") ? "Master Mode" : "Catacombs";
                if (!group.equals(lastGroup)) {
                    result.append(Component.literal("\n" + group).withColor(0xC5A2EE));
                    lastGroup = group;
                }
            }
            result.append(Component.literal("\n  " + label).withColor(0xCAC4CA));
            if (divider >= 0) result.append(Component.literal(line.substring(divider)).withColor(0xFFE3A5));
        }
        return result;
    }

    public static int best(String floor,Consumer<Component> output) {
        return best(floor, output, false);
    }

    public static int best(String floor,Consumer<Component> output,boolean compact) {
        String id=floor.toLowerCase(Locale.ROOT);
        if(!id.matches("e|[fm][1-7]"))return say(output,"Choose E, F1-F7 or M1-M7.");
        var mc=Minecraft.getInstance();
        var player=mc.player;
        if(player==null)return say(output,"Join SkyBlock before checking local splits.");
        dev.purplepad.LocalDungeonSplits.report(player.getUUID(),id.toUpperCase(Locale.ROOT),compact).thenAcceptAsync(text->{
            if(mc.player!=player)return;
            if(text!=null)output.accept(compact ? Component.literal(text) : styledReport(text));
            else if(FabricLoader.getInstance().isModLoaded(FISH))fishBest(id,output,compact);
            else say(output,"No KCA splits for "+id.toUpperCase(Locale.ROOT)+" yet. Finish this floor with KCA running.");
        },mc);
        return 1;
    }

    public static int fishBest(String floor,Consumer<Component> output) {
        return fishBest(floor, output, false);
    }

    private static int fishBest(String floor,Consumer<Component> output,boolean compact) {
        String id=floor.toLowerCase(Locale.ROOT);
        if(!id.matches("e|[fm][1-7]"))return say(output,"Choose E, F1-F7 or M1-M7.");
        var loader=FabricLoader.getInstance();
        var definitions=loader.getModContainer(FISH).flatMap(mod->mod.findPath("data/run_splits.json"));
        if(definitions.isEmpty())return say(output,"FishMod history is unavailable. Use /kca dungeons best <floor> for KCA records.");
        Path history=loader.getConfigDir().resolve("fishmod-runs.json");
        var mc=Minecraft.getInstance();
        var player=mc.player;
        CompletableFuture.supplyAsync(()->{
            if(!Files.isRegularFile(history))return "No FishMod split history yet. Enable FishMod run splits and finish a dungeon.";
            try(var defs=Files.newBufferedReader(definitions.get());var records=Files.newBufferedReader(history)) {
                return bestOf(id,JsonParser.parseReader(defs).getAsJsonObject(),JsonParser.parseReader(records).getAsJsonObject(),compact);
            } catch(java.io.IOException | RuntimeException e) {
                NucleusMod.LOGGER.debug("Could not read FishMod splits",e);
                return "Could not read FishMod's split history. Try again after the run.";
            }
        }).thenAcceptAsync(text->{if(mc.player==player)output.accept(compact ? Component.literal(text) : styledReport(text));},mc);
        return 1;
    }

    static String bestOf(String floor,JsonObject definitions,JsonObject history,boolean compact) {
        var entries=child(history,floor);
        if(entries==null)entries=child(history,floor.toUpperCase(Locale.ROOT));
        String label=floor.toUpperCase(Locale.ROOT);
        if(entries==null || !definitions.has(floor))return "No FishMod splits recorded for "+label+".";
        var rows=new ArrayList<String>();
        long total=0;
        int found = 0, expected = 0;
        for(var element:definitions.getAsJsonArray(floor)) {
            var def=element.getAsJsonObject();
            if(def.get("avgSec").getAsDouble()<0)continue; // FishMod marks cumulative checkpoints this way.
            expected++;
            String name=def.get("label").getAsString();
            double fastest=Double.POSITIVE_INFINITY;
            if(entries.has(name) && entries.get(name).isJsonArray())for(var sample:entries.getAsJsonArray(name)){
                if(!sample.isJsonPrimitive() || !sample.getAsJsonPrimitive().isNumber())continue;
                double seconds=sample.getAsDouble();
                if(seconds>0 && seconds<=3600)fastest=Math.min(fastest,seconds);
            }
            if(!Double.isFinite(fastest)){if (!compact) rows.add(name+"  --");continue;}
            found++;
            long ms=Math.round(fastest*1000);
            if (!compact) rows.add(name+"  "+clock(ms));
            total+=ms;
        }
        boolean complete = found == expected && total > 0;
        if (compact) return label + " theoretical best (FishMod): " +
            (complete ? clock(total) : found + "/" + expected + " segments recorded");
        String summary=complete?"Sum of best segments  "+clock(total):"Sum available after every segment has a time.";
        return label+" / Best splits (FishMod local history)\n"+String.join("\n",rows)+"\n"+summary;
    }
    private DungeonRecords() {}
}
