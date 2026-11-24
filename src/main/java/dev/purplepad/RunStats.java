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
        mc.gui.getChat().addClientSystemMessage(Component.literal("[KCA] "+reason+" ").append(action));
    }
    static List<String> sampleLowest(String template){
        return ClearReport.lowest(List.of(new ClearReport("Alex","Tank",3,2),new ClearReport("Sam","Archer",5,8),
            new ClearReport("Robin","Mage",0,0)),template);
    }
    static Lowest lowest=Lowest.LOCAL;
    static boolean shareRows, muteBladeRecap;
    public static boolean hideBladeRecap(){return com.nucleus.NucleusMod.CONFIG.kcaEnabled&&muteBladeRecap;}
    private static final Deque<String> outgoingReport=new ArrayDeque<>();
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
    static final Pattern COUNT=Pattern.compile("^(\\w{1,16}): ([\\d,]+)$");
    private static final Map<String,Player> party=new LinkedHashMap<>();
    private static final Map<String,SecretSamples> commandSamples=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private static byte[] lastMap;
    private static List<String> liveRoster=List.of();
    private static final RoomClears clears=new RoomClears();
    private static boolean active, finishing, header;
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
        long secrets(){return finalCounts!=null?finalCounts.secrets():command.delta(finishing).or(()->api.delta(finishing)).orElse(-1L); }
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
    static List<String> testReport(){
        var rows=new ArrayList<>(reportSample());
        if(lowest!=Lowest.OFF)rows.addAll(ClearReport.lowest(List.of(
            new ClearReport(Minecraft.getInstance().getUser().getName(),"Archer",5,9),new ClearReport("Mage","Mage",2,6))));
        return rows;
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
        ClientReceiveMessageEvents.ALLOW_GAME.register((message,overlay)->overlay||!active||!header||!isCommandReply(com.nucleus.client.HollowsDetector.stripFormatting(message.getString()).trim()));
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Identifier.fromNamespaceAndPath("kca","run_stats"),(g,d)->{
            var mc=Minecraft.getInstance();
            if(!PurplePadClient.hudEnabled()||!active||!hud||!inDungeon||mc.player==null||mc.options.hideGui||mc.screen instanceof com.nucleus.client.MoveHudScreen||mc.screen instanceof GuiSettingsScreen||mc.screen instanceof RunHudEditor)return;
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
        props.setProperty("lowestTemplate",lowestTemplate);props.setProperty("suggestKick",Boolean.toString(suggestKick));
        props.setProperty("messageTemplate",messageTemplate);props.setProperty("lowest",lowest.name());props.setProperty("shareRows",""+shareRows);props.setProperty("muteBladeRecap",""+muteBladeRecap);
        props.setProperty("kcaBackdrop",""+kcaBackdrop);
        props.setProperty("simple",""+simple);props.setProperty("x",""+positionX);props.setProperty("y",""+positionY);
        try(var out=Files.newOutputStream(CONFIG)){props.store(out,"KCA Dungeons");}
        catch(IOException e){Minecraft.getInstance().gui.getChat().addClientSystemMessage(Component.literal("KCA could not save dungeon settings."));}
    }
    private static void worldChanged(boolean sameConnection){
        var mc=Minecraft.getInstance();
        if(com.nucleus.NucleusMod.CONFIG.kcaEnabled){
            if(finishing)finishReport(mc,sameConnection);
            if(!sameConnection)while(!outgoingReport.isEmpty())local(mc,outgoingReport.removeFirst());
        }
        if(!sameConnection){outgoingReport.clear();nextReportSend=0;}
        if(active && !finishing)clearStreak();
        if(!sameConnection){clearStreak();lastLowest=List.of();}
        reset();observedLevel=null;inDungeon=false;locationTicks=0;
    }
    static void reset(){completedFloor="";completionTime=-1;displayStamp=-1;session++;active=false;finishing=false;header=false;party.clear();commandSamples.clear();lastMap=null;liveRoster=List.of();clears.reset();age=query=readUntil=roomSize=pendingStart=missingLocation=0;}
    private static void startRun(Minecraft mc){
        reset();observedLevel=mc.level;inDungeon=true;locationTicks=30;active=true;
        party.computeIfAbsent(mc.player.getScoreboardName(),Player::new);refreshParty(mc);
    }
    static void message(String line){
        if(!com.nucleus.NucleusMod.CONFIG.kcaEnabled||!tracking())return;
        var mc=Minecraft.getInstance();
        if(line.indexOf('\n')>=0){
            for(String row:line.split("\\R"))if(!row.isBlank())message(row.strip());
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
        if(clean.equals("Secret Counts:") && query>0 && age<=readUntil)header=true;
        if(isCommandReply(clean)){
            var count=COUNT.matcher(clean);
            if(count.matches()){
                commandSamples.computeIfAbsent(count.group(1),k->new SecretSamples()).record(Long.parseLong(count.group(2).replace(",","")),query,finishing);
            }
            return;
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
        header=false;readUntil=age+95;int serial=++query;
        mc.getConnection().sendCommand("secretcount");
        if(API_KEY==null||API_KEY.isBlank())return;
        int generation=session;
        // Optional official Hypixel API access for players absent from /secretcount.
        for(var entry:party.entrySet()){
            var info=mc.getConnection().getPlayerInfo(entry.getKey());
            if(info==null||entry.getKey().equals(mc.player.getScoreboardName()))continue;
            var request=HttpRequest.newBuilder(URI.create("https://api.hypixel.net/v2/player?uuid="+info.getProfile().id()))
                .header("API-Key",API_KEY).timeout(Duration.ofSeconds(4)).build();
            var player=entry.getValue();
            Http.client.sendAsync(request,HttpResponse.BodyHandlers.ofString()).thenAccept(response->{
                if(response.statusCode()!=200)return;
                var achievements=JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("player").getAsJsonObject("achievements");
                if(achievements==null||!achievements.has("skyblock_treasure_hunter"))return;
                long value=achievements.get("skyblock_treasure_hunter").getAsLong();
                mc.execute(()->{if(session==generation&&query==serial)player.api.record(value,serial,last);});
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
            if(clearStreak.update(lowestPlayer))kickOption(mc,lowestPlayer,lowestPlayer+" ranked lowest for four runs (65% secrets, 35% rooms).");
            var mages=rows.stream().filter(r->r.role().equalsIgnoreCase("Mage")).toList();
            String mage=suggestMage && sameWorld && bloodMs>25_000 && mages.size()==1 &&
                rows.stream().noneMatch(r->r.role().isBlank())?mages.getFirst().name():"";
            if(mageStreak.update(mage))kickOption(mc,mage,"Blood opened over 25s in four runs with "+mage+" as Mage.");
            if(lowest==Lowest.PARTY && sameWorld){
                String summary=String.join(" | ",lines);
                if(summary.length()<=235)output(mc,summary,true);
                else lines.forEach(line->output(mc,line,true));
            }else if(lowest!=Lowest.OFF)lines.forEach(line->local(mc,line));
        }
        if(chat)party.forEach((name,p)->output(mc,p.line(name),shareRows&&sameWorld));
    }
    private static void output(Minecraft mc,String line,boolean share){
        if(!share||mc.getConnection()==null){local(mc,line);return;}
        String plain=com.nucleus.client.HollowsDetector.stripFormatting(line).replaceAll("[\\p{Cntrl}]"," ").strip();
        if(!plain.isEmpty())outgoingReport.addLast(plain.substring(0,Math.min(235,plain.length())));
    }
    private static void sendNextReportLine(Minecraft mc){
        if(outgoingReport.isEmpty()||mc.getConnection()==null)return;
        long now=System.nanoTime();
        if(now<nextReportSend)return;
        mc.getConnection().sendCommand("pchat [KCA] "+outgoingReport.removeFirst());
        nextReportSend=now+650_000_000L;
    }
    private static void local(Minecraft mc,String line){
        mc.gui.getChat().addClientSystemMessage(Component.literal("§d[KCA] §r"+line));
    }
    private static class Http {static final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();}
    private static boolean isCommandReply(String message){
        if(!active||query==0||age>readUntil||!header)return false;
        if(message.startsWith("Secret Counts:")&&message.indexOf('\n')>=0)
            return message.lines().map(String::strip).filter(s->!s.isEmpty()).allMatch(s->s.equals("Secret Counts:")||COUNT.matcher(s).matches());
        return message.equals("Secret Counts:") || COUNT.matcher(message).matches();
    }
    private static void tick(Minecraft mc){
        if(!com.nucleus.NucleusMod.CONFIG.kcaEnabled){if(active||pendingStart>0||!outgoingReport.isEmpty())worldChanged(false);return;}
        if(observedLevel!=mc.level){worldChanged(true);observedLevel=mc.level;}
        if(mc.player==null||mc.level==null)return;
        sendNextReportLine(mc);
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
        if(!tracking()){reset();return;}
        age++;
        if(finishing){
            if(age>=deadline){
                finishReport(mc,true);
                reset();
            }
            return;
        }
        if(age==1||age%600==0&&hud)sample(mc,false);
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
