package dev.purplepad;

import com.google.gson.*;
import com.nucleus.NucleusMod;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;

/** Exact commands expand first, followed by one pass over argument tokens. */
public final class Shortcuts {
    private static final Path CONFIG=FabricLoader.getInstance().getConfigDir();
    private static final Path FILE=CONFIG.resolve("kca/shortcuts.json");
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    enum Group {
        COMMAND("Commands","commands"), ARGUMENT("Command arguments","commandArgs"), KEY("Key bindings","keyBindings");
        final String title,field;
        Group(String title,String field){this.title=title;this.field=field;}
        boolean enabled(){
            var cfg=NucleusMod.CONFIG;
            return switch(this){case COMMAND->cfg.shortcutCommands;case ARGUMENT->cfg.shortcutArguments;case KEY->cfg.shortcutKeys;};
        }
        void toggle(){
            var cfg=NucleusMod.CONFIG;
            switch(this){case COMMAND->cfg.shortcutCommands=!cfg.shortcutCommands;case ARGUMENT->cfg.shortcutArguments=!cfg.shortcutArguments;case KEY->cfg.shortcutKeys=!cfg.shortcutKeys;}
            clearKeys();cfg.save();
        }
    }
    record Rules(Map<String,String> commands,Map<String,String> commandArgs,Map<String,String> keyBindings){
        Map<String,String> group(Group g){return switch(g){case COMMAND->commands;case ARGUMENT->commandArgs;case KEY->keyBindings;};}
        Rules copy(){return new Rules(new LinkedHashMap<>(commands),new LinkedHashMap<>(commandArgs),new LinkedHashMap<>(keyBindings));}
    }
    private static Rules rules=new Rules(Map.of(),Map.of(),Map.of());
    private static boolean ready;
    private static final Set<String> held=new LinkedHashSet<>();
    private static long lastKeyNanos;
    private Shortcuts(){}
    static Map<String,String> entries(Group g){return rules.group(g);}
    public static boolean active(){return ready && NucleusMod.CONFIG.kcaEnabled && NucleusMod.CONFIG.shortcutsEnabled;}
    private static Rules read(Reader reader){
        JsonObject object=JsonParser.parseReader(reader).getAsJsonObject();
        return new Rules(readGroup(object,Group.COMMAND),readGroup(object,Group.ARGUMENT),readGroup(object,Group.KEY));
    }
    private static Rules readImport(Reader reader){
        Rules imported=read(reader);
        if(imported.commandArgs().remove("/voiderst","/warp void"))
            imported.commandArgs().putIfAbsent("/void","/warp void");
        return imported;
    }
    private static Map<String,String> readGroup(JsonObject object,Group group){
        Map<String,String> result=new LinkedHashMap<>();
        if(!object.has(group.field))return result;
        for(var item:object.getAsJsonObject(group.field).entrySet()){
            String alias=item.getKey().strip(),target=item.getValue().getAsString().strip();
            if(!valid(alias,target,group)||result.putIfAbsent(alias,target)!=null)
                throw new IllegalArgumentException("Invalid or duplicate shortcut "+alias);
        }
        return result;
    }
    static boolean valid(String alias,String target,Group group){
        if(group==Group.KEY)return !alias.isBlank() && !target.isBlank() && target.length()<=256 && target.chars().noneMatch(Character::isISOControl) && Arrays.stream(alias.split(" \\+ ",-1)).allMatch(Shortcuts::validKey);
        return alias.startsWith("/") && alias.length()>1 && target.startsWith("/") && target.length()>1
            && (group!=Group.ARGUMENT||alias.indexOf(' ')<0) && alias.length()<=256 && target.length()<=256
            && alias.chars().noneMatch(Character::isISOControl) && target.chars().noneMatch(Character::isISOControl);
    }
    private static boolean validKey(String name){
        try{return com.mojang.blaze3d.platform.InputConstants.getKey(name)!=com.mojang.blaze3d.platform.InputConstants.UNKNOWN;}
        catch(IllegalArgumentException e){return false;}
    }
    public static void clearKeys(){held.clear();}
    public static void key(String name,boolean down){
        if(!down){held.remove(name);return;}
        if(!active() || !Group.KEY.enabled() || rules.keyBindings.isEmpty())return;
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(mc.player==null||mc.screen!=null||mc.getOverlay()!=null){clearKeys();return;}
        if(!held.add(name))return;
        String action=rules.keyBindings.get(name);
        if(action==null && held.size()>1)action=rules.keyBindings.get(String.join(" + ",held));
        long now=System.nanoTime();
        if(action==null || now-lastKeyNanos<200_000_000L)return;
        lastKeyNanos=now;
        if(action.startsWith("/"))mc.player.connection.sendCommand(typed(action.substring(1)));
        else mc.player.connection.sendChat(action);
    }
    static int importMissing()throws IOException{
        Path source=CONFIG.resolve("skyblocker/shortcuts.json");
        if(!Files.exists(source))throw new IOException("No Skyblocker shortcut file in this profile.");
        try(Reader reader=Files.newBufferedReader(source)){
            Rules incoming=readImport(reader),next=rules.copy();int added=0;
            for(Group g:Group.values())for(var e:incoming.group(g).entrySet())
                if(next.group(g).putIfAbsent(e.getKey(),e.getValue())==null)added++;
            if(added>0)commit(next);
            return added;
        }
    }
    static void load(){
        try{
            if(Files.exists(FILE)){
                try(Reader reader=Files.newBufferedReader(FILE)){rules=read(reader);ready=true;}
            }else{
                Path source=CONFIG.resolve("skyblocker/shortcuts.json");
                try(Reader reader=Files.exists(source)?Files.newBufferedReader(source):
                    new InputStreamReader(Objects.requireNonNull(Shortcuts.class.getResourceAsStream("/assets/kca/shortcuts.json")),StandardCharsets.UTF_8)){
                    commit(readImport(reader));
                }
            }
        }catch(IOException|RuntimeException e){NucleusMod.LOGGER.warn("Could not load KCA shortcuts",e);}
    }
    private static void commit(Rules next)throws IOException{
        Files.createDirectories(FILE.getParent());
        Path tmp=Files.createTempFile(FILE.getParent(),"shortcuts-",".tmp");
        try{
            JsonObject json=JSON.toJsonTree(next).getAsJsonObject();
            if(next.keyBindings.isEmpty())json.remove(Group.KEY.field);
            Files.writeString(tmp,JSON.toJson(json)+System.lineSeparator());
            try{Files.move(tmp,FILE,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING);}
            rules=next;clearKeys();ready=true;
        }finally{Files.deleteIfExists(tmp);}
    }
    static void update(Group group,String oldKey,String alias,String target)throws IOException{
        var next=rules.copy();
        if(oldKey!=null)next.group(group).remove(oldKey);
        if(alias!=null){
            var dest=next.group(group);
            if(!valid(alias,target,group))throw new IllegalArgumentException("Use /commands for the alias and its target.");
            if(dest.putIfAbsent(alias,target)!=null)throw new IllegalArgumentException("That shortcut already exists.");
        }
        commit(next);
    }
    static String expand(String input){
        String expanded=Group.COMMAND.enabled()?rules.commands().getOrDefault(input,input):input;
        if(!Group.ARGUMENT.enabled())return expanded;
        if(expanded.indexOf(' ')<0)return rules.commandArgs().getOrDefault(expanded,expanded);
        int start=0;StringBuilder out=null;
        while(start<expanded.length()){
            int end=expanded.indexOf(' ',start);if(end<0)end=expanded.length();
            if(expanded.charAt(start)=='/'){
                String replacement=rules.commandArgs().get(expanded.substring(start,end));
                if(replacement!=null){
                    if(out==null)out=new StringBuilder(expanded.length()+24).append(expanded,0,start);
                    out.append(replacement);
                }else if(out!=null)out.append(expanded,start,end);
            }else if(out!=null)out.append(expanded,start,end);
            if(out!=null&&end<expanded.length())out.append(' ');
            start=end+1;
        }
        return out==null?expanded:out.toString();
    }
    public static String typed(String command){
        if(!active())return command;
        String full="/"+command,result=expand(full);
        return result==full?command:result.substring(1);
    }
}

