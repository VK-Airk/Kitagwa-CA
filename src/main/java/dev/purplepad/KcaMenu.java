package dev.purplepad;

import com.nucleus.NucleusMod;
import com.nucleus.client.KcaNucleusSettings;
import com.nucleus.client.MoveHudScreen;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class KcaMenu extends Screen {
    enum Page { ROOT, DUNGEONS, GLOBAL }
    private static final int[] COLORS={0xFFB7AACD,0xFFDCB7B0,0xFF86D8EC,0xFFFFC877,0xFF94E1BD,0xFFF499B8,0xFFF2EBDB};
    private static final String[] NAMES={"Lavender","Dusty peach","Aqua","Amber","Mint","Rose","Ivory"};
    private static final String TEXT_CODES="bd6ae5cf";
    private static final String[] TEXT_NAMES={"Aqua","Pink","Gold","Green","Yellow","Purple","Red","White"};
    private final Page page;
    private final Screen parent;
    private boolean secrets, records, appearance, rarity;
    private int recordFloor=7;
    private RarityEditor rarityEditor;
    private EditBox search;
    private String query="";
    private boolean focusSearch;
    private int resultPage;
    private record Target(String name,String keywords,Runnable open){}
    private final List<Target> targets=new ArrayList<>();
    private final List<Button> branchWidgets=new ArrayList<>(), results=new ArrayList<>();
    private static final Identifier LOGO=Identifier.fromNamespaceAndPath("kca","textures/logo.png");
    private boolean customLogo;
    private FanArt artwork;
    private FanArt artwork(){
        if(artwork==null)artwork=parent instanceof KcaMenu menu ? menu.artwork() : FanArt.next();
        return artwork;
    }
    private static final int NODE_W=60,NODE_H=46,LEAF_W=86;
    private int rootY(){return height/2-(height<240?9:Math.clamp((290-height)/2,0,25));}
    private int searchY(){return Math.max(4,height/2-180);}
    private int nodeX(int branch){
        int inner=Math.min(142,Math.max(80,width/5));
        int outer=Math.min(266,Math.min(width/2-NODE_W/2-6,width*3/8));
        return width/2+switch(branch){case 0->-inner;case 4->inner;case 2->-outer;case 3->outer;default->0;};
    }
    private int nodeY(int branch){
        int cy=rootY();
        return switch(branch){
            case 0,4 -> Math.max(searchY()+NODE_H/2+23,cy-Math.min(108,height/4));
            case 2,3 -> cy+Math.min(24,height/14);
            default -> Math.min(cy+78,height-(height<240?59:87));
        };
    }
    private int leafX(int side){return width/2+side*(height<240?Math.min(90,width/2-LEAF_W/2-5):Math.min(82,width/5));}
    private int leafY(){return height<240?nodeY(1)+5:Math.min(height-47,nodeY(1)+39);}
    private boolean gamblingAbove(){return nodeX(0)-NODE_W/2<LEAF_W+16;}
    private int gamblingX(){return gamblingAbove()?4:Math.max(4,nodeX(0)-NODE_W/2-LEAF_W-24);}
    private int gamblingY(){return gamblingAbove()?searchY():nodeY(0)+6;}
    private int logoRadius(){return Math.max(52,font.width("Kitagawa Codex Addons")/2+9);}
    private int nodeColor(int i){
        return switch(i){case 0->NucleusMod.CONFIG.nucleusAccent;case 1->KcaTheme.ROSE;case 2->NucleusMod.CONFIG.dungeonAccent;case 3->PartyScreen.MINT;default->KuudraScreen.ACCENT;};
    }
    private final List<BranchStroke> branches=new ArrayList<>();
    KcaMenu(Page page){this(page,null);}
    KcaMenu(Page page,Screen parent){super(Component.literal("Kitagawa Codex Addons"));this.page=page;this.parent=parent;}
    private static Screen nucleus(KcaNucleusSettings.Section section,Screen parent){return new KcaNucleusSettings(section,parent);}
    public static void openNucleus(){
        var mc=Minecraft.getInstance();
        mc.schedule(()->mc.gui.setScreen(nucleus(KcaNucleusSettings.allSections().getFirst(),new KcaMenu(Page.ROOT))));
    }
    private static Screen kuudraScreen(Screen parent){
        if(KcaEdition.KUUDRA)return new KuudraScreen(parent);
        return parent;
    }
    static Screen chatScreen(Screen parent,boolean shortcuts){
        if(KcaEdition.CHAT)return shortcuts?PartyScreen.shortcutsPage(parent):new PartyScreen(parent);
        return parent;
    }
    static int openKuudra(){
        if(!KcaEdition.KUUDRA)return 0;
        var mc=Minecraft.getInstance();mc.schedule(()->mc.gui.setScreen(kuudraScreen(new KcaMenu(Page.ROOT))));return 1;
    }
    static int openWaypointSettings(){
        var mc=Minecraft.getInstance();
        mc.schedule(()->mc.gui.setScreen(new KcaNucleusSettings(
            KcaNucleusSettings.sections(KcaNucleusSettings.Tab.WAYPOINTS).getFirst(),
            new KcaMenu(Page.ROOT)).focusSetting("Waypoint mode")));
        return 1;
    }
    static int open(Page page,String query){
        var mc=Minecraft.getInstance();mc.schedule(()->{
            var screen=new KcaMenu(page,page==Page.ROOT?null:new KcaMenu(Page.ROOT));
            if(query!=null){screen.query=query.trim();screen.focusSearch=true;}
            mc.gui.setScreen(screen);
        });return 1;
    }
    private void show(Screen screen){minecraft.gui.setScreen(screen);}
    public static void generalTabs(java.util.function.Consumer<com.nucleus.client.NucleusUi.NButton> add,int cx,int available,int selected,java.util.function.IntConsumer change){
        int w=(Math.min(368,available)-12)/4,left=cx-(4*w+12)/2;
        String[] labels={"Settings","Appearance","Achievements","Item rarity"};
        for(int i=0;i<labels.length;i++){int page=i;add.accept(KcaTheme.tab(labels[i],left+i*(w+4),49,w,selected==i,KcaTheme.ROSE,()->change.accept(page)));}
    }
    public static void generalTabFrom(Screen parent,int tab){
        var screen=parent instanceof KcaMenu menu && menu.page==Page.GLOBAL?menu:new KcaMenu(Page.GLOBAL,parent);
        screen.appearance=tab==1;screen.rarity=tab==3;Minecraft.getInstance().gui.setScreen(screen);
    }
    private void general(boolean style){var screen=new KcaMenu(Page.GLOBAL,this);screen.appearance=style;show(screen);}
    private void dungeon(boolean counter){var screen=new KcaMenu(Page.DUNGEONS,this);screen.secrets=counter;show(screen);}
    private int accent(){return page==Page.DUNGEONS?NucleusMod.CONFIG.dungeonAccent:KcaTheme.ROSE;}
    Button button(String text,int x,int y,int w,int color,Runnable action){
        int ink=page==Page.DUNGEONS?KcaTheme.DUNGEON_INK:page==Page.GLOBAL?0xFF100D12:KcaTheme.INK;
        return addRenderableWidget(new TimerSettingsScreen.PixelButton(x,y,w,19,text,color,ink,b->action.run()));
    }
    @Override protected void init(){
        branchWidgets.clear();results.clear();targets.clear();
        int cx=width/2,cy=rootY();
        if(page==Page.ROOT){
            customLogo=minecraft.getResourceManager().getResource(LOGO).isPresent();
            if(KcaEdition.NUCLEUS)for(var s:KcaNucleusSettings.allSections()){
                targets.add(new Target("Nucleus / "+s.title(),s.tab().title+" "+s.desc()+" "+keywords(s.title()),()->show(nucleus(s,this))));
            }
            if(KcaEdition.DUNGEONS)targets.add(new Target("Dungeons / Purple Pad Timer","storm py simple move resize shadow timer color",()->dungeon(false)));
            if(KcaEdition.DUNGEONS)targets.add(new Target("Dungeons / Secrets and rooms","counter report hud chat print scale move colors teammates",()->dungeon(true)));
            if(KcaEdition.GENERAL)targets.add(new Target("General / Settings","master enable quiet chat test commands colors themes accent gameplay hud menu effects animation backgrounds",()->show(new KcaMenu(Page.GLOBAL,this))));
            if(KcaEdition.GENERAL)targets.add(new Target("General / Damage numbers","damage splash compact precision significant figures critical color symbols",()->show(new DamageSettingsScreen(this))));
            if(KcaEdition.GENERAL)targets.add(new Target("General / Item rarity","skyblocker item rarity backgrounds opacity shape palette inventory hotbar custom item color",()->generalTabFrom(this,3)));
            if(KcaEdition.GENERAL)targets.add(new Target("General / Achievements","achievements progress dungeon rooms secrets storm nucleus records milestones",()->show(nucleus(KcaNucleusSettings.achievements(),new KcaMenu(Page.GLOBAL,this)))));
            if(KcaEdition.HAS_HUD)targets.add(new Target("GUI settings","simple kca purple pad fancy gui appearance hud style nucleus counter timer",()->show(new GuiSettingsScreen(this,KcaEdition.DUNGEONS?GuiSettingsScreen.Element.TIMER:GuiSettingsScreen.Element.NUCLEUS))));
            if(KcaEdition.NUCLEUS)for(var section:KcaNucleusSettings.allSections()){
                for(String label:SettingLabels.NUCLEUS.getOrDefault(section.title(),List.of()))
                    targets.add(new Target(label,"Nucleus "+section.title(),()->show(new KcaNucleusSettings(section,this).focusSetting(label))));
            }
            if(KcaEdition.GENERAL)for(String label:List.of("KCA master switch","Quiet chat","Test commands","Game HUD"))
                targets.add(new Target(label,"General settings",()->general(false)));
            if(KcaEdition.GENERAL)for(String label:List.of("Menu FX","Dungeons accent","Nucleus accent"))
                targets.add(new Target(label,"General appearance",()->general(true)));
            if(KcaEdition.DUNGEONS)for(String label:List.of("Secrets color","Rooms color","Chat print","Move / resize counter","Lowest non-Mage rooms and secrets","Hide Blade split recap"))
                targets.add(new Target(label,"Dungeons secrets and rooms",()->dungeon(true)));
            if(KcaEdition.DUNGEONS)for(String label:List.of("Timer color","Shadow","Move / resize timer"))
                targets.add(new Target(label,"Dungeons Purple Pad Timer",()->dungeon(false)));
            if(KcaEdition.DUNGEONS)targets.add(new Target("Secrets message","dungeons chat report format template counter",()->show(new SecretMessageScreen(this))));
            if(KcaEdition.CHAT)targets.add(new Target("Shortcuts","command arguments key bindings aliases",()->show(chatScreen(this,true))));
            if(KcaEdition.CHAT)targets.add(new Target("Chat commands","party chat prefixes routing help coords chims allinvite shortcuts aliases tester testing",()->show(chatScreen(this,false))));
            if(KcaEdition.NUCLEUS)targets.add(new Target("Nucleus spreadsheet","google sheet",()->KcaNucleusSettings.openSheet(this)));
            if(KcaEdition.KUUDRA)targets.add(new Target("Kuudra","kitagawa marin",()->show(kuudraScreen(this))));
            if(KcaEdition.NUCLEUS)for(boolean best:List.of(true,false))targets.add(new Target(best?"Personal best":"Run history","Nucleus speedruns PB fastest time records splits",()->{
                var timer=nucleus(KcaNucleusSettings.sections(KcaNucleusSettings.Tab.SPEEDRUNS).getFirst(),this);
                show(new com.nucleus.client.RunHistoryScreen(timer,best));
            }));
            targets.add(new Target("Credits","marin kitagawa fan art",this::openCredits));
            if(KcaEdition.GENERAL && KcaEdition.HAS_HUD)targets.add(new Target("Move all HUD elements","global move gui position resize overlays",()->MoveHudScreen.open(this)));
            int searchWidth=gamblingAbove()?Math.min(224,width-2*(LEAF_W+10)):224;
            search=addRenderableWidget(new EditBox(font,cx-searchWidth/2,searchY(),searchWidth,18,Component.literal("Search all settings")));
            search.setHint(Component.literal("Search all settings"));search.setMaxLength(64);search.setValue(query);
            buildBranches();
            for(int i=0;i<5;i++)if(KcaEdition.includesNode(i))branchWidgets.add(addRenderableWidget(new BranchNode(nodeX(i)-NODE_W/2,nodeY(i)-NODE_H/2,i)));
            if(KcaEdition.NUCLEUS)branchWidgets.add(button("CN Gambling",gamblingX(),gamblingY(),LEAF_W,nodeColor(0),
                ()->show(nucleus(KcaNucleusSettings.sections(KcaNucleusSettings.Tab.MISC).getFirst(),this))));
            if(KcaEdition.GENERAL)branchWidgets.add(button("Achievements",leafX(-1)-LEAF_W/2,leafY(),LEAF_W,KcaTheme.ROSE,()->show(nucleus(KcaNucleusSettings.achievements(),this))));
            if(KcaEdition.GENERAL && KcaEdition.HAS_HUD)branchWidgets.add(button("Move all HUD",leafX(1)-LEAF_W/2,leafY(),LEAF_W,KcaTheme.ROSE,()->MoveHudScreen.open(this)));
            branchWidgets.add(button("Done",cx-32,height-23,64,KcaTheme.MUTED,this::onClose));
            int artTop=height<240?searchY():searchY()+22,artHeight=height<240?22:Math.min(128,Math.max(22,cy-artTop-45));
            int artWidth=Math.max(40,Math.round(artHeight*.85f));
            FanArt selected=artwork();
            branchWidgets.add(addRenderableWidget(new Button(height<240?width-artWidth-4:cx-artWidth/2,artTop,artWidth,artHeight+15,
                Component.literal("Credits"),b->openCredits(),label->label.get()){
                @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float tick){
                    int x=getX(),y=getY();
                    KcaTheme.button(g,x,y,getWidth(),getHeight(),isHoveredOrFocused(),true,KcaTheme.ROSE,KcaTheme.INK);
                    selected.draw(g,x+2,y+2,artWidth-4,artHeight-4);
                    g.centeredText(font,getMessage(),x+artWidth/2,y+getHeight()-13,KcaTheme.PETAL);
                }
            }));
            search.setResponder(v->{query=v;resultPage=0;searchResults();});
            searchResults();if(focusSearch){setFocused(search);focusSearch=false;}return;
        }
        int left=Math.max(4,(width-368)/2),full=width-left*2,nav=Math.min(110,full/3);
        int x=left+nav+10,w=full-nav-16,y=52;
        if(page==Page.DUNGEONS){
            int tabW=(full-16)/3;
            addRenderableWidget(KcaTheme.tab("Purple Pad Timer",left+4,49,tabW,!secrets && !records,accent(),()->{records=false;secrets=false;rebuildWidgets();}));
            addRenderableWidget(KcaTheme.tab("Secrets and rooms",left+8+tabW,49,tabW,secrets && !records,accent(),()->{records=false;secrets=true;rebuildWidgets();}));
            x=left+8;w=full-16;y=72;
            addRenderableWidget(KcaTheme.tab("Records",left+12+tabW*2,49,tabW,records,accent(),()->{records=true;rebuildWidgets();}));
            if(records){
                button("Scan account PBs",x,y,w,accent(),()->{
                    minecraft.gui.setScreen(null);
                    com.nucleus.client.DungeonRecords.scan(minecraft.gui.hud.getChat()::addClientSystemMessage);
                });
                String floor=recordFloor==0?"E":recordFloor<=7?"F"+recordFloor:"M"+(recordFloor-7);
                button("Split floor - "+floor,x,y+22,w,accent(),()->{recordFloor=(recordFloor+1)%15;rebuildWidgets();});
                button("Show best splits",x,y+44,w,accent(),()->{
                    minecraft.gui.setScreen(null);
                    com.nucleus.client.DungeonRecords.best(floor,minecraft.gui.hud.getChat()::addClientSystemMessage);
                }).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Fastest segments from runs KCA observed on this floor. Shows FishMod history if KCA has no records yet.")));
            }else if(secrets){
                button(state("Display",RunStats.hud),x,y,w,accent(),()->{RunStats.options(!RunStats.hud,RunStats.chat);rebuildWidgets();});
                int half=(w-4)/2;
                button("Chat print - "+(!RunStats.chat?"OFF":RunStats.shareRows?"PARTY":"LOCAL"),x,y+22,half,KcaTheme.DUNGEON_BLUE,()->{
                    if(!RunStats.chat){RunStats.chat=true;RunStats.shareRows=false;}
                    else if(!RunStats.shareRows)RunStats.shareRows=true;
                    else {RunStats.chat=false;RunStats.shareRows=false;}
                    RunStats.saveOptions();rebuildWidgets();
                }).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Print one row per player. LOCAL shows you the report. PARTY sends each row to party chat.")));
                button("Fewest - "+RunStats.lowest.name(),x+half+4,y+22,half,accent(),()->{
                    RunStats.lowest=switch(RunStats.lowest){case OFF->RunStats.Lowest.LOCAL;case LOCAL->RunStats.Lowest.PARTY;case PARTY->RunStats.Lowest.OFF;};
                    RunStats.saveOptions();rebuildWidgets();
                }).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Print the fewest rooms and secrets separately. Mages are excluded. LOCAL shows you the result. PARTY shares it with your party.")));
                button("Move / resize",x,y+44,w,accent(),()->show(new RunHudEditor(this)));
                textColor(x,y+66,half,0);textColor(x+half+4,y+66,half,1);
                textColor(x,y+88,w,2);
                button("Secrets message",x,y+110,half,accent(),()->show(new SecretMessageScreen(this)));
                button(state("Hide Blade split recap",RunStats.muteBladeRecap),x+half+4,y+110,half,accent(),()->{RunStats.muteBladeRecap=!RunStats.muteBladeRecap;RunStats.saveOptions();rebuildWidgets();}).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Hide Blade Addons’ final Splits chat summary. Its timer still runs.")));
            }else{
                var c=PurplePadClient.timerConfig();
                button(state("Timer",c.enabled),x,y,w,accent(),()->editTimer(t->t.enabled=!t.enabled));
                button(state("Shadow",c.shadow),x,y+22,w,KcaTheme.DUNGEON_BLUE,()->editTimer(t->t.shadow=!t.shadow));
                button("Move / resize timer",x,y+44,w,accent(),()->PurplePadClient.openTimer(this));
                palette("Timer color",NucleusMod.CONFIG.timerColor,x,y+66,w,v->NucleusMod.CONFIG.timerColor=v);
            }
        }else{
            var cfg=NucleusMod.CONFIG;
            generalTabs(this::addRenderableWidget,cx,full,rarity?3:appearance?1:0,i->{
                if(i==2) show(nucleus(KcaNucleusSettings.achievements(),this));
                else {appearance=i==1;rarity=i==3;rebuildWidgets();}
            });
            x=left+8;y=72;w=full-16;
            if(rarity){
                if(rarityEditor==null)rarityEditor=new RarityEditor(this);
                rarityEditor.controls(x,y,w);
            }
            else if(appearance){
                button("Menu FX - "+(!cfg.menuEffects?"Off":cfg.subtleMenuEffects?"Subtle":"Full"),x,y,w,KcaTheme.ROSE,this::cycleMenuEffects);
                if(KcaEdition.DUNGEONS)palette("Dungeons",cfg.dungeonAccent,x,y+22,w,v->cfg.dungeonAccent=v);
                if(KcaEdition.NUCLEUS)palette("Nucleus",cfg.nucleusAccent,x,y+44,w,v->cfg.nucleusAccent=v);
                button("Credits",x,y+66,(w-4)/2,KcaTheme.PETAL,this::openCredits);
                button(state("Beta badges",cfg.showBetaBadges),x+(w-4)/2+4,y+66,(w-4)/2,KcaTheme.ROSE,()->{
                    cfg.showBetaBadges=!cfg.showBetaBadges;save();
                }).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Show a pink BETA badge above listed testers' names.")));
            }else{
                button(state("KCA",cfg.kcaEnabled),x,y,w,accent(),()->{cfg.kcaEnabled=!cfg.kcaEnabled;if(!cfg.kcaEnabled)PurplePadClient.disableRuntime();save();});
                button(state("Quiet chat",cfg.quietChat),x,y+22,(w-4)/2,KcaTheme.PETAL,()->{cfg.quietChat=!cfg.quietChat;save();});
                button("Damage numbers",x+(w-4)/2+4,y+22,(w-4)/2,KcaTheme.PETAL,()->show(new DamageSettingsScreen(this)));
                button(state("Test commands",cfg.testCommands),x,y+44,w,KcaTheme.PETAL,()->{cfg.testCommands=!cfg.testCommands;save();});
                int half=(w-4)/2;
                button(state("Game HUD",cfg.showGameplayHud),x,y+66,half,KcaTheme.MAUVE,()->{cfg.showGameplayHud=!cfg.showGameplayHud;save();});
                if(KcaEdition.HAS_HUD)button("Move all HUD",x+half+4,y+66,half,KcaTheme.PETAL,()->MoveHudScreen.open(this));
            }
        }
        if(KcaEdition.HAS_HUD)button("GUI settings",cx-102,footerY(),100,KcaTheme.PETAL,()->show(new GuiSettingsScreen(this,secrets?GuiSettingsScreen.Element.COUNTER:(KcaEdition.DUNGEONS?GuiSettingsScreen.Element.TIMER:GuiSettingsScreen.Element.NUCLEUS))));
        button("Back",cx+2,footerY(),100,KcaTheme.MUTED,this::onClose);
    }
    <T extends net.minecraft.client.gui.components.AbstractWidget> T editorWidget(T widget){
        return addRenderableWidget(widget);
    }
    void refreshEditor(){rebuildWidgets();}
    @Override public void removed(){
        if(rarityEditor!=null)rarityEditor.flush();
        super.removed();
    }
    private int footerY(){
        if(page==Page.GLOBAL && rarity)return Math.min(height-21,232);
        return Math.min(height-22,72+(page==Page.DUNGEONS&&secrets?6:4)*22+12);
    }
    private static String keywords(String title){
        return switch(title){
            case "Jungle Temple" -> "offset coordinates guardian automatic manual set now hotkey waypoint through walls lines";
            case "Custom waypoints" -> "import export clipboard capture keybind remove";
            case "Tool Finder" -> "mines of divan metal detector treasure locator scavenged tools sets hourly";
            case "HUD & alerts" -> "crystal hollows map poi regions landmarks minimap wrong pet mole low tools bad lobby notification terror stacks lobby day bal pet spawn detector";
            case "NPC highlights" -> "yolkar npc box outlines mob markers";
            case "Customization" -> "seasonal particles animation wheel theme sound pack";
            case "CN Gambling" -> "misc gamble gambling jackpot vault spin rare drop wheel odds preview loot zoom reset settings defaults";
            case "Timing" -> "gambling quick roll quick spin hotkey speed opening game reveal";
            case "Sounds" -> "yolkar crystal apparatus tool key bal place chest";
            case "Achievements" -> "progress unlock challenges";
            case "Waypoints" -> "jungle temple custom";
            case "Speedruns" -> "warp nucleus lobby auto safe runs splits history crystal timer record personal best";
            default -> "";
        };
    }
    private void searchResults(){
        for(var b:results)removeWidget(b);results.clear();
        boolean searching=!query.isBlank();for(var b:branchWidgets)b.visible=!searching;
        search.setX(width/2-112);
        if(!searching)return;
        String[] words=query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        var found=targets.stream().filter(t->{String s=(t.name+" "+t.keywords).toLowerCase(Locale.ROOT);return Arrays.stream(words).allMatch(s::contains);}).sorted(Comparator.comparingInt(t -> t.name.equalsIgnoreCase(query.trim()) ? 0 : 1)).toList();
        int cx=width/2,y=search.getY()+24,rows=Math.clamp((height-y-27)/22,1,6);
        int start=resultPage*rows;
        for(int i=start;i<Math.min(found.size(),start+rows);i++){
            var t=found.get(i);results.add(button(t.name,cx-112,y+(i-start)*22,224,KcaTheme.PETAL,()->{award("kca_ui_search");t.open.run();}));
        }
        if(found.isEmpty()){var b=button("No matching settings",cx-112,y,224,KcaTheme.MUTED,()->{});b.active=false;results.add(b);}
        if(found.size()>rows){
            int foot=y+rows*22;
            var prev=button("<",cx-42,foot,40,KcaTheme.ROSE,()->{resultPage--;searchResults();});prev.active=start>0;results.add(prev);
            var next=button(">",cx+2,foot,40,KcaTheme.ROSE,()->{resultPage++;searchResults();});next.active=start+rows<found.size();results.add(next);
        }
    }
    private static String state(String label,boolean enabled){return label+" - "+(enabled?"ON":"OFF");}
    private void award(String id){
        if(minecraft.player!=null && NucleusMod.CONFIG.kcaEnabled) com.nucleus.client.Achievements.unlockFun(id);
    }
    private void openCredits(){award("kca_ui_credits");show(new CreditsScreen(this,artwork()));}
    private void cycleMenuEffects(){
        var cfg=NucleusMod.CONFIG;
        if(!cfg.menuEffects){cfg.menuEffects=true;cfg.subtleMenuEffects=false;}
        else if(cfg.subtleMenuEffects){cfg.menuEffects=false;cfg.subtleMenuEffects=false;}
        else cfg.subtleMenuEffects=true;
        award("kca_ui_effects");
        save();
    }
    private void save(){NucleusMod.CONFIG.save();RunStats.displayStamp=-1;rebuildWidgets();}
    private void editTimer(Consumer<TimerConfig> edit){
        var cfg=PurplePadClient.timerConfig();edit.accept(cfg);
        try{PurplePadClient.applySettings(cfg);}catch(java.io.IOException ex){minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal("Could not save timer settings."));}
        rebuildWidgets();
    }
    private void palette(String label,int value,int x,int y,int w,Consumer<Integer> set){
        int idx=0;while(idx<COLORS.length && COLORS[idx]!=value)idx++;
        int next=(idx+1)%COLORS.length;
        button(label+" - "+(idx<COLORS.length?NAMES[idx]:"Custom"),x,y,w,value,()->{NucleusMod.CONFIG.paletteVersion=1;set.accept(COLORS[next]);save();});
    }
    private void textColor(int x,int y,int w,int kind){
        var cfg=NucleusMod.CONFIG;char code=kind==0?cfg.secretColor:kind==1?cfg.roomColor:cfg.lowestColor;
        int i=Math.max(0,TEXT_CODES.indexOf(code));var formatting=net.minecraft.ChatFormatting.getByCode(TEXT_CODES.charAt(i));
        button((kind==0?"Secrets":kind==1?"Rooms":"Fewest / kick")+" color - "+TEXT_NAMES[i],x,y,w,0xFF000000|net.minecraft.network.chat.TextColor.fromLegacyFormat(formatting).getValue(),()->{
            char next=TEXT_CODES.charAt((i+1)%TEXT_CODES.length());
            if(kind==0)cfg.secretColor=next;else if(kind==1)cfg.roomColor=next;else cfg.lowestColor=next;save();
        });
    }
    private final class BranchNode extends Button {
        final int branch;
        final Identifier texture;
        float selection;
        long lastFrame;
        BranchNode(int x,int y,int branch){
            super(x,y,NODE_W,NODE_H,Component.literal(branch==0?"Nucleus":branch==1?"General":branch==2?"Dungeons":branch==3?"Chat commands":"Kuudra"),b->{
                if(branch==0)show(nucleus(KcaNucleusSettings.allSections().getFirst(),KcaMenu.this));
                else if(branch==1)show(new KcaMenu(Page.GLOBAL,KcaMenu.this));
                else if(branch==2)dungeon(false);
                else if(KcaEdition.CHAT && branch==3)show(chatScreen(KcaMenu.this,false));
                else if(KcaEdition.KUUDRA)show(kuudraScreen(KcaMenu.this));
            },DEFAULT_NARRATION);
            this.branch=branch;
            texture=KcaEdition.KUUDRA && branch==4?KuudraScreen.ART:branch==3?Identifier.fromNamespaceAndPath("kca","textures/party_chat.png"):branch==1?KcaTheme.SETTINGS_ICON:Identifier.fromNamespaceAndPath("kca",branch==0?"textures/alloy.png":"textures/hyperion.png");
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float tick){
            var cfg=NucleusMod.CONFIG;
            int accent=branch==0?cfg.nucleusAccent:branch==1?KcaTheme.ROSE:branch==4?KuudraScreen.ACCENT:branch==3?PartyScreen.MINT:cfg.dungeonAccent;
            long now=System.nanoTime();
            float target=isHoveredOrFocused()?1:0;
            float dt=lastFrame==0?0:Math.min(.1f,(now-lastFrame)*1e-9f);lastFrame=now;
            selection=cfg.menuEffects?selection+(target-selection)*Math.min(1f,dt*14f):target;
            int x=getX(),y=getY(),cx=x+NODE_W/2;
            if(selection>.08f)g.fill(x-2,y-2,x+NODE_W+2,y+NODE_H+2,((int)(48*selection)<<24)|(accent&0xFFFFFF));
            KcaTheme.button(g,x,y,NODE_W,NODE_H,selection>.5f,true,accent,branch==2?KcaTheme.DUNGEON_INK:KcaTheme.INK);
            int radius=11+(cfg.menuEffects?Math.round(2*selection):0);
            int iconY=y+17,halfWidth=branch==3?radius*3/4:radius;
            g.blit(texture,cx-halfWidth,iconY-radius,cx+halfWidth,iconY+radius,0,1,0,branch==0?.25f:1f);
            g.centeredText(font,branch==3?"Chat":getMessage().getString(),cx,y+32,accent);
        }
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float tick){KcaTheme.backdrop(g,width,height,page==Page.DUNGEONS?KcaTheme.Branch.DUNGEONS:KcaTheme.Branch.GENERAL);}
    // Compute the strokes on layout. A half-pixel overlap joins their rounded bends.
    private record BranchStroke(org.joml.Matrix3x2f transform,int color){
        void draw(GuiGraphicsExtractor g){
            var pose=g.pose();
            pose.pushMatrix();
            pose.mul(transform);
            g.fill(0,-1,1,1,color);
            pose.popMatrix();
        }
    }
    private void curve(int x0,int y0,int x1,int y1,int x2,int y2,int x3,int y3,int start,int end){
        double arcBound=Math.hypot(x1-x0,y1-y0)+Math.hypot(x2-x1,y2-y1)+Math.hypot(x3-x2,y3-y2);
        int count=Math.clamp((int)arcBound/5,4,32);
        double px=x0,py=y0;
        for(int i=1;i<=count;i++){
            double t=(double)i/count,u=1-t;
            double x=u*u*u*x0+3*u*u*t*x1+3*u*t*t*x2+t*t*t*x3;
            double y=u*u*u*y0+3*u*u*t*y1+3*u*t*t*y2+t*t*t*y3;
            float dx=(float)(x-px),dy=(float)(y-py),length=(float)Math.hypot(dx,dy);
            if(length>.01f){
                var matrix=new org.joml.Matrix3x2f().translation((float)px,(float)py)
                    .rotate((float)Math.atan2(dy,dx)).translate(-.45f,0).scale(length+.9f,.75f);
                branches.add(new BranchStroke(matrix,KcaTheme.tint(start,end,(i-1)*100/(count-1))));
            }
            px=x;py=y;
        }
    }
    private void buildBranches(){
        branches.clear();
        int cx=width/2,cy=rootY(),stem=KcaTheme.EDGE;
        for(int side:new int[]{-1,1}){
            int crown=side<0?0:4,outer=side<0?2:3;
            if(!KcaEdition.includesNode(crown) && !KcaEdition.includesNode(outer))continue;
            int startX=cx+side*(logoRadius()-1),fork=cx+side*(logoRadius()+Math.min(31,width/26));
            int y=cy-4,forkY=y-8;
            curve(startX,y,startX+side*7,y,fork-side*6,forkY+3,fork,forkY,stem,stem);
            int x=nodeX(crown),tip=nodeY(crown)+NODE_H/2-1;
            int rise=Math.max(0,forkY-tip);
            int reach=Math.clamp(side*(x-fork),0,28)/2;
            if(KcaEdition.includesNode(crown))curve(fork,forkY,fork+side*reach,forkY-Math.min(7,rise/3),x,tip+Math.min(18,rise/2),x,tip,stem,KcaTheme.tint(KcaTheme.INK,nodeColor(crown),70));
            x=nodeX(outer)-side*(NODE_W/2-1);tip=nodeY(outer);
            int bend=Math.max(3,Math.abs(x-fork)/3);
            if(KcaEdition.includesNode(outer))curve(fork,forkY,fork+side*bend,forkY-2,x-side*bend,tip,x,tip,stem,KcaTheme.tint(KcaTheme.INK,nodeColor(outer),70));
        }
        if(KcaEdition.NUCLEUS)drawGamblingBranch();
        if(!KcaEdition.GENERAL)return;
        drawGeneralBranches(cx,cy,stem);
    }
    private void drawGamblingBranch(){
        int childX=gamblingX(),childY=gamblingY(),nucX=nodeX(0),nucY=nodeY(0);
        int nucAccent=KcaTheme.tint(KcaTheme.INK,nodeColor(0),70);
        if(gamblingAbove()){
            int port=childX+LEAF_W/2;
            curve(port,childY+17,port,childY+24,nucX,nucY-NODE_H/2-6,nucX,nucY-NODE_H/2+1,nucAccent,nucAccent);
        }else{
            int port=childX+LEAF_W-1,edge=nucX-NODE_W/2+1;
            curve(port,childY+9,(port+edge)/2,childY+9,(port+edge)/2,nucY,edge,nucY,nucAccent,nucAccent);
        }
    }
    private void drawGeneralBranches(int cx,int cy,int stem){
        int general=nodeY(1),tip=general-NODE_H/2+1;
        curve(cx,cy+21,cx,cy+22,cx,tip-1,cx,tip,stem,stem);
        int bottom=general+NODE_H/2-1,mid=(bottom+leafY())/2;
        for(int side:new int[]{-1,1}){
            if(side>0 && !KcaEdition.HAS_HUD)continue;
            int x=leafX(side);
            int accent=KcaTheme.tint(KcaTheme.INK,KcaTheme.ROSE,65);
            if(height<240){
                int port=x-side*(LEAF_W/2-1);
                curve(cx+side*(NODE_W/2-1),general,cx+side*39,general,port-side*8,leafY()+9,port,leafY()+9,stem,accent);
            }else curve(cx+side*13,bottom,cx+side*13,mid+3,x,mid-3,x,leafY()+1,stem,accent);
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float tick){
        int cx=width/2,cy=rootY();
        if(page==Page.ROOT && query.isBlank()){
            for(BranchStroke stroke:branches)stroke.draw(g);
            int radius=logoRadius();
            KcaTheme.panel(g,cx-radius,cy-24,2*radius,46);
            if(customLogo)g.blit(LOGO,cx-48,cy-19,cx+48,cy+5,0,1,0,1);
            else KcaTheme.heading(g,font,"KCA",cx,cy-19,3);
            g.centeredText(font,"Kitagawa Codex Addons",cx,cy+10,KcaTheme.PETAL);
        }else if(page!=Page.ROOT){
            int y=52,left=Math.max(4,(width-368)/2),full=width-left*2,nav=Math.min(110,full/3);
            KcaTheme.heading(g,font,"KCA",cx,6,2);
            g.centeredText(font,page==Page.GLOBAL?"General / "+(rarity?"Item rarity":appearance?"Appearance":"Settings"):"Dungeons / "+(records?"Records":secrets?"Secrets and rooms":"Purple Pad Timer"),cx,30,accent());
            int h=footerY()-6-(y-4);
            if(page==Page.DUNGEONS){
                TimerSettingsScreen.card(g,left,46,full,h+2,KcaTheme.DUNGEON_EDGE,KcaTheme.DUNGEON_INK);
                g.centeredText(font,RunStats.locationLabel(),cx,footerY()+21,KcaTheme.DUNGEON_BLUE);
            }else{
                TimerSettingsScreen.card(g,left,46,full,h+2,KcaTheme.EDGE,0xFF100D12);
                if(rarity && rarityEditor!=null)rarityEditor.preview(g,left+8,full-16,footerY());
            }
        }
        super.extractRenderState(g,mx,my,tick);
    }
}
