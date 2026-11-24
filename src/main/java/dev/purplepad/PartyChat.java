package dev.purplepad;

import com.nucleus.NucleusMod;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class PartyChat {
    private static final Pattern NAME=Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern SENDER=Pattern.compile("^(?:(?:\\[[^\\]\\r\\n]{1,32}\\]|[\\p{So}\\p{M}]+)\\s*)*([A-Za-z0-9_]{1,16})\\s*:\\s*(.{1,256})$");
    private static final Pattern REMOVED=Pattern.compile("^(?:\\[[^\\]\\r\\n]{1,32}\\]\\s+)*([A-Za-z0-9_]{1,16}) has been removed from the party[.!]$");
    private static final Pattern DEPARTED=Pattern.compile("^(?:\\[[^\\]]+\\]\\s+)*([A-Za-z0-9_]{1,16}) (?:has left the party|has been removed from the party|left the party)[.!]?$",Pattern.CASE_INSENSITIVE);
    private static final Pattern JOINED=Pattern.compile("^(?:\\[[^\\]]+\\]\\s+)*([A-Za-z0-9_]{1,16}) joined the party[.!]?$",Pattern.CASE_INSENSITIVE);
    private static final Map<String,Long> awaitingLeave=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private static final Map<String,Long> inviteAfterLeave=new LinkedHashMap<>();
    private static void clearReinvites(){awaitingLeave.clear();inviteAfterLeave.clear();}
    private static String lastKicked;
    private static final Pattern ALL_INVITE=Pattern.compile("^(?:\\[[^\\]]+\\] )*([A-Za-z0-9_]{1,16}) (enabled|disabled) All Invite[.!]?$",Pattern.CASE_INSENSITIVE);
    static final String[] TIERS={"NORMAL","HOT","BURNING","FIERY","INFERNAL"};
    private static final ArrayDeque<String> replies=new ArrayDeque<>();
    private static volatile long pingToken;
    private static long pingStart;
    public static boolean acceptPong(net.minecraft.client.multiplayer.ClientPacketListener connection,long token){
        if(token==0 || token!=pingToken)return false;
        long arrival=System.nanoTime();
        var mc=Minecraft.getInstance();
        mc.execute(()->{
            if(mc.getConnection()!=connection || pingToken!=token)return;
            pingToken=0;
            if(!NucleusMod.CONFIG.partyDisabled.contains("ping"))
                reply("Ping: "+Math.max(0,Math.round((arrival-pingStart)/1_000_000.0))+" ms");
        });
        return true;
    }
    private static long nextReply,lastAction,expectConfirm,autoOffAt;
    private record Lookup(String key,String prefix,String sender,PartyRoster roster,long deadline){}
    private static Lookup lookup;
    private static String lastRequest="";
    private static long lastRequestAt;
    private static boolean handling;
    private static String markerSetting="",prefixSetting="";
    private static String[] markerList={},prefixList={};
    private static final Pattern WORDS=Pattern.compile("\\s+");
    private static final DateTimeFormatter CLOCK=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static StringBuilder preview;
    private static long budgetAt;
    private static double credits=4;
    private static String[] parts(String setting){
        return Arrays.stream(setting.split(",")).map(String::trim).filter(s->!s.isEmpty())
            .sorted(Comparator.comparingInt(String::length).reversed()).toArray(String[]::new);
    }
    private PartyChat(){}
    record Request(String sender,String alias,String[] args){}
    static boolean playerName(String name){return NAME.matcher(name).matches();}
    static Request parse(String line,String markers,String prefixes){
        if(line==null || line.length()>512 || line.indexOf(':')<0)return null;
        String plain=line.indexOf('§')<0?line:net.minecraft.ChatFormatting.stripFormatting(line);
        if(plain==null)return null;
        if(!markers.equals(markerSetting)){markerList=parts(markers);markerSetting=markers;}
        if(!prefixes.equals(prefixSetting)){prefixList=parts(prefixes);prefixSetting=prefixes;}
        for(String marker:markerList){
            int start=marker.length();
            if(!plain.regionMatches(true,0,marker,0,start))continue;
            if(start<plain.length()&&Character.isLetterOrDigit(marker.charAt(start-1))&&!Character.isWhitespace(plain.charAt(start)))continue;
            while(start<plain.length()&&Character.isWhitespace(plain.charAt(start)))start++;
            int colon=plain.indexOf(':',start);
            if(colon<0)return null;
            int body=colon+1;
            while(body<plain.length()&&Character.isWhitespace(plain.charAt(body)))body++;
            for(String prefix:prefixList){
                if(!plain.startsWith(prefix,body))continue;
                Matcher m=SENDER.matcher(plain).region(start,plain.length());
                if(!m.matches())continue;
                String[] words=WORDS.split(plain.substring(body+prefix.length()).strip());
                if(words.length==0 || words[0].isEmpty())return null;
                String key=words[0].toLowerCase(Locale.ROOT);
                if(key.equals("all") && words.length==2 && words[1].equalsIgnoreCase("inv"))return new Request(m.group(1),"allinv",new String[0]);
                if((key.equals("f")||key.equals("m")||key.equals("t")) && words.length==2 && words[1].matches(key.equals("f")?"[0-7]":key.equals("t")?"[1-5]":"[1-7]"))
                    return new Request(m.group(1),key+words[1],new String[0]);
                return new Request(m.group(1),key,Arrays.copyOfRange(words,1,words.length));
            }
        }
        return null;
    }
    static void init(){
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h,c)->reset());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c->tick());
    }
    public static void reset(){pingToken=0;clearReinvites();lastKicked=null;lookup=null;replies.clear();expectConfirm=autoOffAt=nextReply=lastAction=lastRequestAt=0;lastRequest="";credits=4;budgetAt=0;}
    public static void receive(String text){
        var cfg=NucleusMod.CONFIG;
        var mc=Minecraft.getInstance();
        if(!cfg.kcaEnabled || !cfg.partyChatEnabled || mc.player==null || mc.getConnection()==null)return;
        if(lookup!=null)readPartyList(text,System.currentTimeMillis());
        if(!awaitingLeave.isEmpty() || !inviteAfterLeave.isEmpty() || lastKicked!=null
            || text.contains("has been removed from the party") || expectConfirm!=0 || autoOffAt!=0){
            String plain=net.minecraft.ChatFormatting.stripFormatting(text);
            if(plain!=null){
                plain=plain.strip();
                long observedAt=System.currentTimeMillis();
                String self=mc.player.getGameProfile().name();
                if(plain.startsWith("----") && plain.indexOf('\n')>=0){
                    for(String row:plain.split("\\R"))observe(row.strip(),self,observedAt);
                }else observe(plain,self,observedAt);
            }
        }
        var request=parse(text,cfg.partyChannelMarkers,cfg.partyTriggers);
        if(request==null)return;
        var entry=PartyCatalog.aliases.get(request.alias);
        if(entry==null || cfg.partyDisabled.contains(entry.key()))return;
        long now=System.currentTimeMillis();
        credits=Math.min(4,credits+Math.max(0,now-budgetAt)/400.0);budgetAt=now;
        if(credits<1 || replies.size()>=12)return;
        String signature=request.sender+" "+request.alias+" "+String.join(" ",request.args);
        if(signature.equals(lastRequest)&&now-lastRequestAt<1000)return;
        credits--;lastRequest=signature;lastRequestAt=now;
        handling=true;
        try{execute(entry,request,mc,now);}
        catch(ReflectiveOperationException|RuntimeException|LinkageError e){
            notice("!"+request.alias+" — "+entry.source()+" could not provide a result. Check the source version and tracker.");
            NucleusMod.LOGGER.warn("Party command {} provider failed",request.alias,e);
        }finally{handling=false;}
    }
    private static void execute(PartyCatalog.Entry entry,Request r,Minecraft mc,long now)throws ReflectiveOperationException{
        String key=entry.key();
        if(!entry.source().equals("KCA")){PartySources.run(entry,r.alias,r.sender,r.args);return;}
        String target=r.args.length==0?r.sender:r.args[0];
        switch(key){
            case "help" -> help(r.args);
            case "coords" -> {var p=mc.player.blockPosition();reply("x: "+p.getX()+", y: "+p.getY()+", z: "+p.getZ());}
            case "lowest" -> {var rows=preview!=null?RunStats.sampleLowest(RunStats.lowestTemplate):RunStats.lastLowest;if(rows.isEmpty())reply("No completed dungeon report yet.");else rows.forEach(PartyChat::reply);}
            case "pb" -> reply(com.nucleus.client.KcaCommands.personalBest());
            case "tools" -> reply("Divan tool sets remaining: " + NucleusMod.SPEEDRUN.remainingToolSets);
            case "cnruns" -> reply("Crystal Nucleus " + com.nucleus.client.SpeedrunManager.counter());
            case "tpb" -> {
                if (r.args.length != 1 || !r.args[0].matches("(?i)e|[fm][1-7]")) {
                    reply("Use !tpb <floor>, e.g. !tpb F7 or !tpb M7.");
                } else if (preview != null) {
                    reply("Example: " + r.args[0].toUpperCase(Locale.ROOT) + " theoretical best (KCA): 4:31.250");
                } else {
                    var connection = mc.getConnection();
                    com.nucleus.client.DungeonRecords.best(r.args[0], result -> {
                        var cfg = NucleusMod.CONFIG;
                        if (connection == mc.getConnection() && cfg.kcaEnabled && cfg.partyChatEnabled
                            && !cfg.partyDisabled.contains("tpb")) reply(result.getString());
                    }, true);
                }
            }
            case "ping" -> {
                if(preview!=null)reply("Measures the round trip to the connected Minecraft server.");
                else if(pingToken==0){
                    pingStart=System.nanoTime();
                    pingToken=pingStart | Long.MIN_VALUE;
                    mc.getConnection().send(new net.minecraft.network.protocol.ping.ServerboundPingRequestPacket(pingToken));
                }
            }
            case "fps" -> reply("FPS: "+mc.getFps());
            case "time" -> reply(LocalDateTime.now().format(CLOCK));
            case "holding" -> {
                var hand=mc.player.getMainHandItem();
                reply(mc.player.getScoreboardName()+(hand.isEmpty()?" has an empty main hand.":" is holding "+hand.getHoverName().getString()+"."));
            }
            case "cf" -> reply(java.util.concurrent.ThreadLocalRandom.current().nextBoolean()?"Heads":"Tails");
            default -> {
                if(preview==null && !key.equals("reinv") && now-lastAction<650){reply("Party action is cooling down. Try !"+r.alias+" again in a moment.");return;}
                String command=switch(key){
                    case "allinv" -> "party settings allinvite";
                    case "warp" -> "party warp";
                    case "reinv" -> {
                        if(r.args.length!=0){reply("Type !reinv before leaving. For someone else, use !p <player>.");yield null;}
                        if(preview!=null){preview.append("After ").append(r.sender).append(" leaves, invite them back after five seconds.");yield null;}
                        if(r.sender.equalsIgnoreCase(mc.player.getScoreboardName())){
                            notice("Another party member with party replies enabled can reinvite you after you leave.");
                            yield null;
                        }
                        if(awaitingLeave.size()>=100 && !awaitingLeave.containsKey(r.sender))yield null;
                        awaitingLeave.put(r.sender,now+60_000);
                        reply(r.sender+", leave within 60 seconds and I’ll invite you back.");
                        yield null;
                    }
                    case "kpt" -> {
                        if(r.args.length!=0){reply("Use !kpt by itself, or !inv <full name> to invite a named player.");yield null;}
                        if(lastKicked==null){
                            if(preview!=null)notice("Would invite the last confirmed kick. No kick is recorded for this party yet.");
                            else reply("No kick is recorded for this party yet.");
                            yield null;
                        }
                        yield "party invite "+lastKicked;
                    }
                    case "kickoffline" -> "party kickoffline";
                    case "kick","pt" -> {
                        if(r.alias.equals("ptme")){
                            if(r.args.length==0)yield "party transfer "+r.sender;
                            reply("Use !ptme by itself, or !pt <name> to transfer to someone else.");
                            yield null;
                        }
                        if(key.equals("pt")&&r.args.length==0){
                            reply("Use !"+r.alias+" <name> or !ptme for yourself.");
                            yield null;
                        }
                        if(r.args.length!=1||!playerName(target)){reply("Usage: !"+r.alias+" "+entry.usage());yield null;}
                        if(preview!=null){preview.append("Would check /party list and ").append(key.equals("kick")?"kick ":"transfer leadership to ").append("the party member whose name starts with ").append(target).append(". A full name wins a tie.");yield null;}
                        if(lookup!=null){reply("Still checking the party list. Try again in a moment.");yield null;}
                        lookup=new Lookup(key,target,r.sender,new PartyRoster(),now+5000);
                        yield "party list";
                    }
                    case "invite" -> r.args.length==1&&playerName(target)?"party "+key+" "+target:null;
                    case "promote","demote","boop" -> r.args.length<=1&&playerName(target)?
                        (key.equals("boop")?"boop ":"party "+key+" ")+target:null;
                    default -> {
                        if(key.equals("f0"))yield "joininstance CATACOMBS_ENTRANCE";
                        if(key.matches("[fm][1-7]"))yield "joininstance "+(key.startsWith("m")?"MASTER_":"")+"CATACOMBS_FLOOR_"+List.of("ONE","TWO","THREE","FOUR","FIVE","SIX","SEVEN").get(key.charAt(1)-'1');
                        if(KcaEdition.KUUDRA && key.matches("t[1-5]"))yield "joininstance KUUDRA_"+TIERS[key.charAt(1)-'1'];
                        yield null;
                    }
                };
                if(command==null && Set.of("invite","promote","demote","boop").contains(key))
                    reply("Usage: !"+r.alias+" "+entry.usage());
                if(command!=null){
                    if(preview!=null){server(command);return;}
                    lastAction=now;
                    if(key.equals("allinv")){expectConfirm=cfgAuto()?now+5000:0;autoOffAt=0;}
                    server(command);
                }
            }
        }
    }
    private static void readPartyList(String text,long now){
        var request=lookup;
        if(now>=request.deadline){expireLookup();return;}
        String clean=net.minecraft.ChatFormatting.stripFormatting(text);
        if(clean==null)return;
        clean=clean.strip();
        if(!(clean.startsWith("Party Members (")||clean.startsWith("Party Leader:")||
            clean.startsWith("Party Moderators:")||clean.startsWith("Party Members:")||clean.startsWith("----")))return;
        for(String row:clean.split("\\R"))request.roster.accept(row.strip());
        if(!request.roster.complete())return;
        lookup=null;
        var cfg=NucleusMod.CONFIG;
        if(!cfg.kcaEnabled||!cfg.partyChatEnabled||cfg.partyDisabled.contains(request.key))return;
        if(!request.roster.contains(request.sender))return;
        var names=request.roster.matches(request.prefix);
        if(names.size()==1){
            lastAction=now;
            server("party "+(request.key.equals("pt")?"transfer":"kick")+" "+names.getFirst());
        }else if(names.isEmpty())reply("No party member starts with "+request.prefix+".");
        else reply("Multiple matches for "+request.prefix+": "+String.join(", ",names.stream().limit(5).toList())+". Type more of the name.");
    }
    private static void expireLookup(){
        lookup=null;notice("Party list timed out. Try the command again.");
    }
    private static boolean cfgAuto(){return NucleusMod.CONFIG.partyAllInviteOff;}
    private static void observe(String plain,String self,long now){
        if(plain.indexOf(':')>=0)return;
        if(!awaitingLeave.isEmpty() || !inviteAfterLeave.isEmpty()){
            Matcher departed=DEPARTED.matcher(plain),joined=JOINED.matcher(plain);
            if(departed.matches()){
                String name=departed.group(1);
                Long expiry=awaitingLeave.remove(name);
                if(expiry!=null && now<=expiry)inviteAfterLeave.put(name.toLowerCase(Locale.ROOT),now+5000);
            }else if(joined.matches()){
                inviteAfterLeave.remove(joined.group(1).toLowerCase(Locale.ROOT));
            }
        }
        if(plain.endsWith("has been removed from the party.") || plain.endsWith("has been removed from the party!")){
            Matcher removed=REMOVED.matcher(plain);
            if(removed.matches())lastKicked=removed.group(1);
            return;
        }
        if(lastKicked!=null && plain.endsWith(" joined the party.")){
            String joined=plain.replaceFirst("^(?:\\[[^\\]]+\\]\\s+)*","");
            if(joined.equalsIgnoreCase(lastKicked+" joined the party."))lastKicked=null;
        }
        Matcher m=ALL_INVITE.matcher(plain);
        if(m.matches()){
            boolean ours=m.group(1).equalsIgnoreCase(self)&&m.group(2).equalsIgnoreCase("enabled")&&expectConfirm>=now;
            autoOffAt=ours&&cfgAuto()?now+60_000:0;expectConfirm=0;
        }else if(plain.contains("transferred the party to")){
            expectConfirm=autoOffAt=0;
        }else if(plain.startsWith("You left the party") || plain.startsWith("You have left the party")
            || plain.contains("has been disbanded") || plain.contains("has disbanded the party")
            || plain.startsWith("The party was disbanded") || plain.startsWith("You are not currently in a party")
            || (plain.startsWith("You have joined ") && plain.endsWith(" party!"))
            || plain.startsWith("You have been kicked from the party")){
            clearReinvites();lastKicked=null;lookup=null;
            expectConfirm=autoOffAt=0;
        }
    }
    private static void help(String[] args){
        if(args.length>0){
            var entry=PartyCatalog.aliases.get(args[0].replaceFirst("^!","").toLowerCase(Locale.ROOT));
            if(entry!=null){
                String names="!"+entry.names().replace(" ",", !");
                reply(names+(entry.usage().isEmpty()?"":" "+entry.usage())+" — "+entry.description()+" ("+entry.source()+")");
                return;
            }
        }
        int page=1;
        if(args.length>0){try{page=Integer.parseInt(args[0]);}catch(NumberFormatException e){return;}}
        var enabled=PartyCatalog.entries.stream().filter(e->!NucleusMod.CONFIG.partyDisabled.contains(e.key())).toList();
        int pages=Math.max(1,(enabled.size()+8)/9); page=Math.clamp(page,1,pages);
        reply("Commands "+page+"/"+pages+": "+String.join(", ",enabled.subList((page-1)*9,Math.min(page*9,enabled.size())).stream().map(e->"!"+e.key()).toList())+" | !help <page>");
    }
    static String test(String input){
        var cfg=NucleusMod.CONFIG;
        if(!cfg.testCommands)return "Enable Test commands in General settings.";
        var mc=Minecraft.getInstance();
        if(mc.player==null)return "Enter a world to test party commands.";
        String line=input.strip();
        if(line.startsWith("/")){
            if(!Shortcuts.active())return "KCA shortcuts are off.";
            String expanded=Shortcuts.expand(line);
            return expanded.equals(line)?"No shortcut matches this input.":"Shortcut: "+expanded;
        }
        String marker=cfg.partyChannelMarkers.split(",",2)[0].trim();
        for(String p:parts(cfg.partyTriggers))if(line.startsWith(p)){line=marker+" Tester: "+line;break;}
        Request r=parse(line,cfg.partyChannelMarkers,cfg.partyTriggers);
        if(r==null)return "No match. Check the party label and command prefix in Routing.";
        var e=PartyCatalog.aliases.get(r.alias);
        if(e==null)return "Unknown command: "+r.alias;
        if(cfg.partyDisabled.contains(e.key()))return "!"+r.alias+" is switched off in Commands.";
        String source=PartySources.provider(e);
        if(!PartySources.available(source))return "Source mod "+source+" is not on.";
        boolean tracker=source.equals("IQ") || (source.equals("SBO") && (PartyCatalog.dianaTrackers.contains(e.key()) || Set.of("profit","tps","version").contains(e.key())));
        if(!source.equals("KCA")&&!tracker)return source+" handles !"+r.alias+" in live chat. "+e.description();
        preview=new StringBuilder();
        try{
            execute(e,r,mc,System.currentTimeMillis());
            return preview.isEmpty()?"No result for these arguments. Usage: !"+e.key()+" "+e.usage():preview.toString();
        }catch(ReflectiveOperationException|RuntimeException|LinkageError ex){
            return source+" could not supply this result.";
        }finally{preview=null;}
    }
    static void notice(String line){if(preview!=null){preview.append(line);return;}var mc=Minecraft.getInstance();if(mc.player!=null)mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[KCA] "+line));}
    static void server(String command){if(preview!=null){preview.append("Action: /").append(command);return;}var c=Minecraft.getInstance().getConnection();if(c!=null)c.sendCommand(command);}
    static void reply(String text){
        if(preview!=null){preview.append("/").append(NucleusMod.CONFIG.partyReplyRoute).append(" [KCA] ").append(text);return;}
        String clean=net.minecraft.ChatFormatting.stripFormatting(text);
        if(clean==null)return;
        clean=clean.replaceAll("[\\p{Cntrl}]"," ").strip();
        while(!clean.isEmpty()&&replies.size()<12){
            int end=Math.min(clean.length(),232);
            if(end<clean.length()){
                int space=clean.lastIndexOf(' ',end);if(space>0)end=space;
                if(Character.isHighSurrogate(clean.charAt(end-1)))end--;
            }
            replies.addLast(clean.substring(0,end));clean=clean.substring(end).stripLeading();
        }
    }
    public static boolean captureReply(String text){if(!handling || !Minecraft.getInstance().isSameThread())return false;reply(text);return true;}
    public static boolean captureCommand(String command){
        if(!handling || !Minecraft.getInstance().isSameThread())return false;
        String raw=command.startsWith("/")?command.substring(1):command;
        for(String prefix:List.of("pc ","pchat ","party chat ","p chat "))if(raw.startsWith(prefix))return captureReply(raw.substring(prefix.length()));
        return false;
    }
    private static void tick(){
        if(pingToken==0&&awaitingLeave.isEmpty()&&inviteAfterLeave.isEmpty()&&lookup==null&&replies.isEmpty()&&autoOffAt==0&&expectConfirm==0)return;
        var cfg=NucleusMod.CONFIG;
        if(!cfg.kcaEnabled||!cfg.partyChatEnabled||Minecraft.getInstance().player==null){reset();return;}
        long now=System.currentTimeMillis();
        if(pingToken!=0 && System.nanoTime()-pingStart>=5_000_000_000L){pingToken=0;if(!cfg.partyDisabled.contains("ping"))reply("Ping: server did not respond in 5 seconds.");}
        if(cfg.partyDisabled.contains("reinv"))clearReinvites();
        if(!awaitingLeave.isEmpty())awaitingLeave.values().removeIf(expiry->now>=expiry);
        if(!inviteAfterLeave.isEmpty() && now-lastAction>=650){
            var iter=inviteAfterLeave.entrySet().iterator();
            var invite=iter.next();
            if(now>=invite.getValue()){
                String name=invite.getKey();iter.remove();lastAction=now;
                server("party invite "+name);
            }
        }
        if(lookup!=null&&now>=lookup.deadline)expireLookup();
        if(expectConfirm<now)expectConfirm=0;
        if(!cfgAuto())autoOffAt=0;
        if(autoOffAt>0&&now>=autoOffAt){autoOffAt=0;server("party settings allinvite");}
        if(now>=nextReply&&!replies.isEmpty()){
            server((cfg.partyReplyRoute.equals("pc")?"pc":"pchat")+" [KCA] "+replies.removeFirst());
            nextReply=now+650;
        }
    }
}
