package dev.purplepad;

import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.*;
import java.util.*;
import java.util.regex.Pattern;
import java.nio.file.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;

public final class RunStats {
    enum Lowest { OFF, LOCAL, PARTY }
    static String lowestTemplate=ClearReport.DEFAULT_LOWEST;
    static final String DEFAULT_KICK="{name} ranked lowest for {runs} runs (65% secrets, 35% rooms).";
    static String kickTemplate=DEFAULT_KICK;
    static String kickText(String template,String name){
        return (template.isBlank()?DEFAULT_KICK:template).replace("{name}",name).replace("{runs}","4");
    }
    static boolean suggestKick,suggestMage;
    private static long mortAt,bloodMs=-1;
    private static final Streak clearStreak=new Streak(),mageStreak=new Streak();
    private static final class Streak{
        String name="";int count;
        boolean update(String next){
            if(next.isEmpty()){name="";count=0;return false;}
            count=next.equalsIgnoreCase(name)?count+1:1;
            name=next;return count==4;
        }
    }
    static List<String> lastLowest=List.of();
    static void clearStreak(){clearStreak.update("");mageStreak.update("");}
    static void kickOption(Minecraft mc,String name,String reason){
        if(!PartyChat.playerName(name)||name.equalsIgnoreCase(mc.getUser().getName()))return;
        var action=Component.literal("[Kick "+name+"]").withStyle(style->style.withUnderlined(true)
            .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/party kick "+name)));
        mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§d[KCA] §"+com.nucleus.NucleusMod.CONFIG.lowestColor+reason+" ").append(action));
    }
    static List<String> sampleLowest(String template){
        return ClearReport.lowest(sampleTeam(),template);
    }
    private static List<ClearReport> sampleTeam(){
        return List.of(new ClearReport("Alex","Tank",3,2),new ClearReport("Sam","Archer",5,8),new ClearReport("Robin","Mage",0,0));
    }
    static Lowest lowest=Lowest.LOCAL;
    static boolean shareRows, muteBladeRecap;
    public static boolean hideBladeRecap(){return com.nucleus.NucleusMod.CONFIG.kcaEnabled&&muteBladeRecap;}
    private record ReportLine(String text,boolean party,long delay,String kickTarget) {}
    private static final Deque<ReportLine> outgoingReport=new ArrayDeque<>();
    private static long lastReportDelivery;
    private static long nextReportSend;
    private static boolean tracking(){return hud||chat||suggestKick||suggestMage||lowest!=Lowest.OFF;}
    static boolean hud=true, chat=true, simple;
    private static boolean kcaBackdrop;
    static HudStyle style(){return simple?HudStyle.SIMPLE:kcaBackdrop?HudStyle.KCA:HudStyle.FANCY;}
    static void setStyle(HudStyle style){simple=style==HudStyle.SIMPLE;kcaBackdrop=style==HudStyle.KCA;saveOptions();}
    static String messageTemplate="";
    static float scale=1f;
    static List<String> displayLines=List.of();
    static int displayWidth, displayStamp=-1;
    static float positionX=0,positionY=0;
    private static boolean inDungeon;
    private static net.minecraft.client.multiplayer.ClientLevel observedLevel;
    private static int locationTicks, pendingStart, missingLocation;

    static String locationLabel(){return inDungeon?"In the Catacombs":"Outside the Catacombs";}

    private static boolean dungeonRoster(Minecraft mc) {
        return mc.getConnection()!=null && mc.getConnection().getListedOnlinePlayers().stream()
            .anyMatch(i -> i.getTabListDisplayName()!=null &&
                TAB.matcher(com.nucleus.client.HollowsDetector.stripFormatting(i.getTabListDisplayName().getString()).trim()).matches());
    }

    private static boolean detectDungeon(Minecraft mc) {
        var board=mc.level.getScoreboard();
        var sidebar=board.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
        if(sidebar==null)return false;
        for(var row:board.listPlayerScores(sidebar)){
            var team=board.getPlayersTeam(row.owner());
            String line=net.minecraft.world.scores.PlayerTeam.formatNameForTeam(team,Component.literal(row.owner())).getString();
            if(row.display()!=null)line+=" "+row.display().getString();
            if(DungeonLocation.hasFloor(line))return true;
        }
        return false;
    }

    private static float position(Properties props,String key){
        try{
            float value=Float.parseFloat(props.getProperty(key,"0"));
            return Float.isFinite(value)?Math.clamp(value,0f,1f):0f;
        }catch(NumberFormatException e){return 0f;}
    }
    static final Path CONFIG=FabricLoader.getInstance().getConfigDir().resolve("kca-run-stats.properties");
    static final String START="[NPC] Mort: Here, I found this map when I first entered the dungeon.";
    static final Pattern TAB=Pattern.compile("^\\[\\d+\\]\\s+(?:(?:\\[[^]]+\\]|[^\\w\\s\\[\\]]+)\\s+)*(\\w{1,16})\\s+.*\\((Healer|Mage|Berserk|Archer|Tank|DEAD)(?: [^)]*)?\\)$",Pattern.CASE_INSENSITIVE);
    private static final Map<String,Player> party=new LinkedHashMap<>();
    private static final Map<String,SecretSamples> commandSamples=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private static byte[] lastMap;
    private static List<String> liveRoster=List.of();
    private static final RoomClears clears=new RoomClears();
    private static boolean active, finishing, inBoss, bossSample;
    private static final SecretCountReply secretReply=new SecretCountReply();
    private static long preRunUntil;
    private static net.minecraft.client.multiplayer.ClientLevel preRunLevel;
    private static String completedFloor="";
    private static long completionTime=-1;
    private static int age,deadline,readUntil,session,query,roomSize,originX,originZ;
    private static final String API_KEY=System.getenv("KCA_HYPIXEL_API_KEY");
    private static final class Player {
        final Set<Integer> solo=new HashSet<>(),shared=new HashSet<>();
        int deaths;
        String role="";
        boolean seenOnMap;
        ClearReport.Counts finalCounts;
        long secrets(){
            if(finalCounts!=null)return finalCounts.secrets();
            return command.delta(finishing).or(()->api.delta(finishing))
                .or(command::bossCheckpoint).or(api::bossCheckpoint).orElse(-1L);
        }
        int soloCount(){return finalCounts==null?solo.size():finalCounts.solo();}
        int sharedCount(){return finalCounts==null?shared.size():finalCounts.shared();}
        final SecretSamples command, api = new SecretSamples();
        Player(String name){command=commandSamples.computeIfAbsent(name,k->new SecretSamples());}
        String line(String name) { return line(name,false); }
        String line(String name,boolean compact) {
            long measured=secrets();
            String secrets=measured<0?"?":Long.toString(measured);
            var c=com.nucleus.NucleusMod.CONFIG;
            return SecretMessage.format(messageTemplate,name,soloCount(),sharedCount(),secrets,deaths,compact,c.secretColor,c.roomColor);
        }
    }
    static String sampleLine(String template,boolean second,boolean compact){
        var c=com.nucleus.NucleusMod.CONFIG;
        String name=second?"Mage":Minecraft.getInstance().getUser().getName();
        return SecretMessage.format(template,name,second?2:3,second?0:2,second?"6":"9",second?1:0,compact,c.secretColor,c.roomColor);
    }
    static int previewReport(boolean withKick){
        var config=com.nucleus.NucleusMod.CONFIG;
        for(var player:sampleTeam()){
            queueReport(SecretMessage.format(messageTemplate,player.name(),(int)player.rooms(),0,
                Long.toString(player.secrets()),0,false,config.secretColor,config.roomColor),false,0);
        }
        if(withKick)queueReport("§"+com.nucleus.NucleusMod.CONFIG.lowestColor+kickText(kickTemplate,"Alex")+" [Kick Alex]",false,250_000_000L);
        if(withKick || lowest!=Lowest.OFF)queueLowest(sampleLowest(lowestTemplate),false,withKick?0:250_000_000L);
        sendNextReportLine(Minecraft.getInstance());
        return 1;
    }
    static List<String> reportSample(){return List.of(sampleLine(messageTemplate,false,false),sampleLine(messageTemplate,true,false));}
    public static boolean acceptClearRow(Component row){
        if(!active||!com.nucleus.NucleusMod.CONFIG.kcaEnabled||!tracking())return false;
        var counts=ClearReport.fromNoamm(com.nucleus.client.HollowsDetector.stripFormatting(row.getString()));
        if(counts==null)return false;
        party.computeIfAbsent(counts.name(),Player::new).finalCounts=counts;
        if(finishing && party.size()>=5 && party.values().stream().allMatch(p->p.finalCounts!=null))deadline=Math.min(deadline,age+1);
        return finishing && chat;
    }
    static void reload() {
        var props=new Properties();
        if(Files.exists(CONFIG)) try(var in=Files.newInputStream(CONFIG)){props.load(in);}catch(IOException ignored){}
        kickTemplate=props.getProperty("kickTemplate",DEFAULT_KICK);
        lowestTemplate=props.getProperty("lowestTemplate",ClearReport.DEFAULT_LOWEST);
        suggestKick=Boolean.parseBoolean(props.getProperty("suggestKick","false"));
        suggestMage=Boolean.parseBoolean(props.getProperty("suggestMage","false"));
        if(!suggestKick)clearStreak.update("");if(!suggestMage)mageStreak.update("");
        try{lowest=Lowest.valueOf(props.getProperty("lowest","LOCAL"));}catch(IllegalArgumentException ignored){lowest=Lowest.OFF;}
        hud=Boolean.parseBoolean(props.getProperty("hud","true"));
        muteBladeRecap=Boolean.parseBoolean(props.getProperty("muteBladeRecap","false"));
        shareRows=Boolean.parseBoolean(props.getProperty("shareRows","false"));
        chat=Boolean.parseBoolean(props.getProperty("chat","true"));
        simple=Boolean.parseBoolean(props.getProperty("simple","false"));
        kcaBackdrop=Boolean.parseBoolean(props.getProperty("kcaBackdrop",props.getProperty("madoBackdrop","false")));
        String template=props.getProperty("messageTemplate","");
        messageTemplate=template.substring(0,Math.min(SecretMessage.MAX_LENGTH,template.length()));
        positionX=position(props,"x");positionY=position(props,"y");
        try{scale=Float.parseFloat(props.getProperty("scale","1"));}catch(NumberFormatException e){scale=1;}
        scale=Float.isFinite(scale)?Math.clamp(scale,0.5f,3f):1f;
        displayStamp=-1;
    }
    static void init(){
        reload();
        ClientPlayConnectionEvents.JOIN.register((h,s,c)->worldChanged(false));
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->worldChanged(false));
        ClientTickEvents.END_CLIENT_TICK.register(RunStats::tick);
        // Fabric may hide a reply before the normal GAME event can deliver it.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->overlay || !receiveSecrets(message.getString()));
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Identifier.fromNamespaceAndPath("kca","run_stats"),(g,d)->{
            var mc=Minecraft.getInstance();
            if(!PurplePadClient.hudEnabled()||!active||!hud||!inDungeon||mc.player==null||mc.gui.hud.isHidden()||mc.gui.screen() instanceof com.nucleus.client.MoveHudScreen||mc.gui.screen() instanceof GuiSettingsScreen||mc.gui.screen() instanceof RunHudEditor)return;
            if(displayStamp!=age/5){
            displayStamp=age/5;
            List<String> lines=new ArrayList<>();
            if(simple){
                var self=party.get(mc.player.getScoreboardName());
                if(self!=null)lines.add(self.line(mc.player.getScoreboardName(),true));
            }else party.forEach((name,p)->lines.add(p.line(name)));
            displayLines=lines;
            displayWidth=lines.stream().mapToInt(mc.font::width).max().orElse(0);
            }
            if(!displayLines.isEmpty()) RunHudEditor.draw(g,mc.font,displayLines,displayWidth,simple,positionX,positionY,scale);
        });
    }
    static void options(boolean display,boolean print){
        hud=display;chat=print;saveOptions();
    }
    static void saveOptions(){
        displayStamp=-1;
        var props=new Properties();props.setProperty("scale",""+scale);props.setProperty("hud",""+hud);props.setProperty("chat",""+chat);
        props.setProperty("suggestMage",Boolean.toString(suggestMage));
        props.setProperty("kickTemplate",kickTemplate);
        props.setProperty("lowestTemplate",lowestTemplate);props.setProperty("suggestKick",Boolean.toString(suggestKick));
        props.setProperty("messageTemplate",messageTemplate);props.setProperty("lowest",lowest.name());props.setProperty("shareRows",""+shareRows);props.setProperty("muteBladeRecap",""+muteBladeRecap);
        props.setProperty("kcaBackdrop",""+kcaBackdrop);
        props.setProperty("simple",""+simple);props.setProperty("x",""+positionX);props.setProperty("y",""+positionY);
        try(var out=Files.newOutputStream(CONFIG)){props.store(out,"KCA Dungeons");}
        catch(IOException e){Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal("KCA could not save dungeon settings."));}
    }
    private static void worldChanged(boolean sameConnection){
        var mc=Minecraft.getInstance();
        if(com.nucleus.NucleusMod.CONFIG.kcaEnabled){
            if(finishing)finishReport(mc,sameConnection);
            if(!sameConnection && !outgoingReport.isEmpty()){
                // A disconnect can land between the rows and the delayed summary. Keep
                // pending text for the next client tick in-world, but discard party actions.
                var pending=new ArrayDeque<ReportLine>();
                for(var line:outgoingReport)if(line.kickTarget==null)
                    pending.addLast(new ReportLine(line.text,false,line.delay,null));
                outgoingReport.clear();
                outgoingReport.addAll(pending);
            }
        }else outgoingReport.clear();
        if(!sameConnection)nextReportSend=0;
        if(active && !finishing)clearStreak();
        if(!sameConnection){clearStreak();lastLowest=List.of();}
        reset();observedLevel=null;inDungeon=false;locationTicks=0;
    }
    static void reset(){completedFloor="";completionTime=-1;displayStamp=-1;session++;active=false;finishing=false;inBoss=false;bossSample=false;secretReply.reset();preRunLevel=null;preRunUntil=0;party.clear();commandSamples.clear();lastMap=null;liveRoster=List.of();clears.reset();age=query=readUntil=roomSize=pendingStart=missingLocation=0;}
    private static void startRun(Minecraft mc){
        var early=preRunWindow(mc)?new TreeMap<>(commandSamples):null;
        reset();
        if(early!=null)commandSamples.putAll(early);
        observedLevel=mc.level;inDungeon=true;locationTicks=30;active=true;
        party.computeIfAbsent(mc.player.getScoreboardName(),Player::new);refreshParty(mc);
    }
    static void message(String line){
        if(!com.nucleus.NucleusMod.CONFIG.kcaEnabled||!tracking())return;
        var mc=Minecraft.getInstance();
        if(receiveSecrets(line))return;
        if(line.indexOf('\n')>=0){
            for(String row:line.split("\\R"))if(!row.isBlank())message(row.strip());
            return;
        }
        if(!active && line.trim().equals("Starting in 4 seconds.") && mc.level!=null && mc.getConnection()!=null
                && !preRunWindow(mc) && (detectDungeon(mc)||dungeonRoster(mc))){
            commandSamples.clear();secretReply.reset();
            preRunLevel=mc.level;preRunUntil=System.nanoTime()+10_000_000_000L;
            mc.getConnection().sendCommand("secretcount");
            return;
        }
        if(START.equals(line.trim())&&!active&&mc.player!=null&&mc.level!=null){
            mortAt=System.nanoTime();bloodMs=-1;
            if(detectDungeon(mc)||dungeonRoster(mc))startRun(mc);
            else {observedLevel=mc.level;pendingStart=60;locationTicks=1;}
            return;
        }
        String clean=line.trim();
        if((active||pendingStart>0) && bloodMs<0 && mortAt!=0 && clean.equalsIgnoreCase("The BLOOD DOOR has been opened!"))bloodMs=(System.nanoTime()-mortAt)/1_000_000L;
        if(!active)return;
        if(!finishing && !inBoss && LocalDungeonSplits.isBossEntry(clean)){
            inBoss=true;
            sample(mc,true);
        }
        String floor=DungeonLocation.reportedFloor(clean);
        if(!floor.isEmpty())completedFloor=floor;
        if(completionTime<0)completionTime=DungeonLocation.reportedTime(clean);
        if(!floor.isEmpty()&&!floor.equals("E"))com.nucleus.client.Achievements.unlockFun("kca_d_floor_"+floor);
        if(finishing)return;
        if(!floor.isEmpty()||completionTime>0||clean.equals("> EXTRA STATS <")){
            var self=mc.player==null?null:party.get(mc.player.getScoreboardName());
            com.nucleus.client.Achievements.recordDungeonProgress(1,0,self==null?0:self.solo.size(),self==null?0:self.shared.size());
            com.nucleus.client.Achievements.unlockFun("kca_d_run");
            refreshParty(mc);finishing=true;deadline=age+110;sample(mc,true);
        }else if(clean.startsWith("☠ ")){
            String name=clean.substring(2).split(" ",2)[0];
            if(name.equals("You")&&mc.player!=null)name=mc.player.getScoreboardName();
            if(party.containsKey(name))party.get(name).deaths++;
        }
    }
    private static List<String> refreshParty(Minecraft mc){
        List<String> live=new ArrayList<>();
        if(mc.getConnection()==null)return live;
        var infos=new ArrayList<>(mc.getConnection().getListedOnlinePlayers());
        infos.sort(Comparator.comparing(i->i.getProfile().name()));
        for(var i:infos){
            var display=i.getTabListDisplayName();if(display==null)continue;
            var m=TAB.matcher(com.nucleus.client.HollowsDetector.stripFormatting(display.getString()).trim());
            if(!m.matches())continue;
            String name=m.group(1);
            var player=party.computeIfAbsent(name,Player::new);
            if(!m.group(2).equalsIgnoreCase("DEAD"))player.role=m.group(2);
            if(!m.group(2).equalsIgnoreCase("DEAD")&&!name.equals(mc.player.getScoreboardName()))live.add(name);
        }
        return live;
    }
    private static void sample(Minecraft mc,boolean last){
        if(mc.getConnection()==null||mc.player==null)return;
        secretReply.reset();boolean checkpoint=last && !finishing;bossSample=checkpoint;readUntil=age+95;int serial=++query;
        mc.getConnection().sendCommand("secretcount");
        if(API_KEY==null||API_KEY.isBlank())return;
        int generation=session;
        // Keep API and command samples separate; their lifetime totals may update at different times.
        for(var entry:party.entrySet()){
            var info=mc.getConnection().getPlayerInfo(entry.getKey());
            if(info==null)continue;
            var request=HttpRequest.newBuilder(URI.create("https://api.hypixel.net/v2/player?uuid="+info.getProfile().id()))
                .header("API-Key",API_KEY).timeout(Duration.ofSeconds(4)).build();
            var player=entry.getValue();
            Http.client.sendAsync(request,HttpResponse.BodyHandlers.ofString()).thenAccept(response->{
                if(response.statusCode()!=200)return;
                var achievements=JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("player").getAsJsonObject("achievements");
                if(achievements==null||!achievements.has("skyblock_treasure_hunter"))return;
                long value=achievements.get("skyblock_treasure_hunter").getAsLong();
                mc.execute(()->{if(session==generation&&query==serial){
                    if(checkpoint)player.api.recordBoss(value);else player.api.record(value,serial,last);
                }});
            }).exceptionally(e->null);
        }
    }
    private static void finishReport(Minecraft mc,boolean sameWorld){
        if(completionTime>0 && !completedFloor.isEmpty() && !completedFloor.equals("E"))
            com.nucleus.client.Achievements.recordDungeonTime(completedFloor,completionTime);
        var self=party.get(mc.getUser().getName());
        if(self!=null){
            long secrets=self.secrets();
            if(secrets>0){
                com.nucleus.client.Achievements.recordDungeonProgress(0,secrets,0,0);
                com.nucleus.client.Achievements.unlockFun("kca_d_secret");
            }
            if(secrets>=10)com.nucleus.client.Achievements.unlockFun("kca_d_ten_secrets");
            if(self.solo.size()>=5)com.nucleus.client.Achievements.unlockFun("kca_d_five_solo");
            if(self.shared.size()>=3)com.nucleus.client.Achievements.unlockFun("kca_d_three_shared");
        }
        {
            var rows=new ArrayList<ClearReport>();
            party.forEach((name,p)->rows.add(new ClearReport(name,p.role,p.seenOnMap||p.finalCounts!=null?p.soloCount()+p.sharedCount():-1,p.secrets())));
            var lines=ClearReport.lowest(rows,lowestTemplate);
            lastLowest=List.copyOf(lines);
            String lowestPlayer=suggestKick && sameWorld?ClearReport.lowestContribution(rows):"";
            boolean clearKick=clearStreak.update(lowestPlayer);
            var mages=rows.stream().filter(r->r.role().equalsIgnoreCase("Mage")).toList();
            String mage=suggestMage && sameWorld && bloodMs>25_000 && mages.size()==1 &&
                rows.stream().noneMatch(r->r.role().isBlank())?mages.getFirst().name():"";
            boolean mageKick=mageStreak.update(mage);
            if(chat)party.forEach((name,p)->queueReport(p.line(name),shareRows&&sameWorld,0));
            long delay=250_000_000L;
            if(clearKick){
                queueKick(lowestPlayer,kickText(kickTemplate,lowestPlayer),delay);
                delay=0;
            }
            if(mageKick){
                queueKick(mage,"Blood opened over 25s in four runs with "+mage+" as Mage.",delay);
                delay=0;
            }
            if(lowest!=Lowest.OFF)queueLowest(lines,lowest==Lowest.PARTY && sameWorld,delay);
        }
        sendNextReportLine(mc);
    }
    private static void queueKick(String name,String reason,long delay){
        if(outgoingReport.isEmpty())lastReportDelivery=System.nanoTime();
        outgoingReport.addLast(new ReportLine(reason,false,delay,name));
    }
    private static void queueLowest(List<String> lines,boolean share,long delay){
        if(share){
            String combined=String.join(" | ",lines);
            if(combined.length()<=235)lines=List.of(combined);
        }
        for(String line:lines){
            queueReport("§"+com.nucleus.NucleusMod.CONFIG.lowestColor+line,share,delay);
            delay=0;
        }
    }
    private static void queueReport(String text,boolean party,long delay){
        if(outgoingReport.isEmpty())lastReportDelivery=System.nanoTime();
        outgoingReport.addLast(new ReportLine(text,party,delay,null));
    }
    private static void sendNextReportLine(Minecraft mc){
        while(!outgoingReport.isEmpty()){
            long now=System.nanoTime();
            var line=outgoingReport.getFirst();
            boolean share=line.party && mc.getConnection()!=null;
            if(now-lastReportDelivery<line.delay || share && now<nextReportSend)return;
            outgoingReport.removeFirst();
            if(line.kickTarget!=null)kickOption(mc,line.kickTarget,line.text);
            else if(share){
                String plain=com.nucleus.client.HollowsDetector.stripFormatting(line.text).replaceAll("[\\p{Cntrl}]"," ").strip();
                if(!plain.isEmpty())mc.getConnection().sendCommand("pchat [KCA] "+plain.substring(0,Math.min(235,plain.length())));
                nextReportSend=now+650_000_000L;
            }else local(mc,line.text);
            lastReportDelivery=now;
        }
    }
    private static void local(Minecraft mc,String line){
        mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§d[KCA] §r"+line));
    }
    private static class Http {static final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();}
    private static boolean preRunWindow(Minecraft mc){
        return preRunLevel!=null && preRunLevel==mc.level && System.nanoTime()<preRunUntil;
    }
    private static boolean receiveSecrets(String text){
        if(!com.nucleus.NucleusMod.CONFIG.kcaEnabled || !tracking())return false;
        if(active ? query==0 || age>readUntil : !preRunWindow(Minecraft.getInstance()))return false;
        return secretReply.read(text,(name,total)->{
            var counts=commandSamples.computeIfAbsent(name,k->new SecretSamples());
            if(active && bossSample)counts.recordBoss(total);
            else counts.record(total,active?query:1,active && finishing);
        });
    }
    private static void tick(Minecraft mc){
        if(!com.nucleus.NucleusMod.CONFIG.kcaEnabled){if(active||pendingStart>0||!outgoingReport.isEmpty())worldChanged(false);return;}
        if(observedLevel!=mc.level){worldChanged(true);observedLevel=mc.level;}
        if(mc.player==null||mc.level==null)return;
        sendNextReportLine(mc);
        if(!tracking()){if(active||pendingStart>0)reset();return;}
        if(--locationTicks<=0){
            locationTicks=20;
            boolean detected=detectDungeon(mc);
            // A roster also identifies the run when the floor line is temporarily missing.
            if(!detected && (active||pendingStart>0))detected=dungeonRoster(mc);
            inDungeon=detected;
            if(active && !finishing){
                missingLocation=detected?0:missingLocation+20;
                if(missingLocation>=120){clearStreak();reset();}
            }
            if(pendingStart>0 && detected && tracking())startRun(mc);
        }
        if(pendingStart>0)pendingStart--;
        if(!active)return;
        age++;
        if(finishing){
            if(age>=deadline){
                finishReport(mc,true);
                reset();
            }
            return;
        }
        if(!inBoss && (age==1 || age%600==0 && hud))sample(mc,false);
        if(age%5!=0)return;
        if(age%20==5)liveRoster=refreshParty(mc);
        var live=liveRoster;
        var id=mc.player.getInventory().getItem(8).get(DataComponents.MAP_ID);
        if(id==null)return;
        var map=mc.level.getMapData(id);if(map==null)return;
        if(Arrays.equals(lastMap,map.colors))return;
        if(lastMap==null)lastMap=map.colors.clone();else System.arraycopy(map.colors,0,lastMap,0,map.colors.length);
        if(roomSize==0&&!calibrate(map.colors))return;
        int stride=roomSize+4;
        Map<String,Integer> positions=new HashMap<>();
        int icon=0;
        for(var marker:map.getDecorations()){
            if(marker.type().equals(MapDecorationTypes.FRAME)){icon++;continue;}
            if(icon>=live.size())break;
            int x=Math.floorDiv((marker.x()+128)/2-originX+2,stride), z=Math.floorDiv((marker.y()+128)/2-originZ+2,stride);
            if(x>=0&&x<6&&z>=0&&z<6)positions.put(live.get(icon),x+6*z);
            icon++;
        }
        for(var player:mc.level.players()){
            String name=player.getScoreboardName();if(!party.containsKey(name))continue;
            int x=(int)Math.floor((player.getX()+201)/32),z=(int)Math.floor((player.getZ()+201)/32);
            if(x>=0&&x<6&&z>=0&&z<6)positions.put(name,x+6*z);
        }
        for(String name:positions.keySet())party.get(name).seenOnMap=true;
        clears.update(map.colors,originX,originZ,roomSize,positions,(room,names)->{
            if(names.size()==1 && names.contains(mc.player.getScoreboardName()))com.nucleus.client.Achievements.unlockFun("kca_d_room");
            for(String name:names){
                var player=party.get(name);
                (names.size()==1?player.solo:player.shared).add(room);
            }
        });
    }
    static boolean calibrate(byte[] colors){
        for(int z=0;z<128;z++)for(int x=0;x<128;){
            if(pixel(colors,x,z)!=30){x++;continue;}
            int start=x;while(x<128&&pixel(colors,x,z)==30)x++;
            int length=x-start;if(length!=16&&length!=18)continue;
            roomSize=length;int step=length+4;
            originX=start%step;originZ=z%step;
            // Small floors have the entrance grid inset by a full stride on one or both axes.
            var mc=Minecraft.getInstance();
            if(mc.level!=null){
                var obj=mc.level.getScoreboard().getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
                if(obj!=null)for(var row:mc.level.getScoreboard().listPlayerScores(obj)){
                    var team=mc.level.getScoreboard().getPlayersTeam(row.owner());
                    String line=net.minecraft.world.scores.PlayerTeam.formatNameForTeam(team,Component.literal(row.owner())).getString().replaceAll("§.","");
                    if(line.contains("(E)")){originX=originZ=22;break;}
                    if(line.matches(".*\\([FM]1\\).*")){originX=22;originZ=11;break;}
                    if(line.matches(".*\\([FM][23]\\).*")){originX=originZ=11;break;}
                }
            }
            return true;
        }
        return false;
    }
    private static int pixel(byte[] colors,int x,int z){return x>=0&&z>=0&&x<128&&z<128?colors[x+128*z]&255:0;}
}
