package dev.purplepad;

import com.nucleus.NucleusMod;
import com.nucleus.client.NucleusUi;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class PartyScreen extends Screen {
    static final int MINT=0xFF9DDBCB;
    private final Screen parent;
    private int tab,page,rows;
    private String query="",triggers,markers,error="";
    private PartyCatalog.Entry detail;
    private static final String[] TABS={"Commands","Routing","Shortcuts","Tester"};
    private Shortcuts.Group group=Shortcuts.Group.COMMAND;
    private boolean editing,recording;
    private final LinkedHashSet<String> recorded=new LinkedHashSet<>();
    static PartyScreen shortcutsPage(Screen parent){var screen=new PartyScreen(parent);screen.tab=2;return screen;}
    static PartyScreen testerPage(Screen parent){var screen=new PartyScreen(parent);screen.tab=3;return screen;}
    private String groupHint(){
        return switch(group){
            case COMMAND -> "Match the whole command. /pc switches to party chat.";
            case ARGUMENT -> "Replace a word and keep the rest. /m Alex hi becomes /msg Alex hi.";
            case KEY -> "Press a key or combination in game to run its saved command.";
        };
    }
    private String oldKey,alias="",target="",sample="!coords",result="";
    private int left,w;
    PartyScreen(Screen parent){
        super(Component.literal("Chat commands"));this.parent=parent;
        triggers=NucleusMod.CONFIG.partyTriggers;markers=NucleusMod.CONFIG.partyChannelMarkers;
    }
    private NucleusUi.NButton button(String label,int x,int y,int width,Runnable action){
        return addRenderableWidget(KcaTheme.tab(label,x,y,width,false,MINT,action));
    }
    private EditBox input(String label,int x,int y,int width,String value,int max){
        var box=addRenderableWidget(new EditBox(font,x,y,width,18,Component.literal(label)));
        box.setMaxLength(max);box.setValue(value);return box;
    }
    @Override protected void init(){
        w=Math.min(width-16,366);left=(width-w)/2;
        String[] tabs=TABS;int tw=(w-4*(tabs.length-1))/tabs.length;
        for(int i=0;i<tabs.length;i++){int next=i;addRenderableWidget(KcaTheme.tab(tabs[i],left+i*(tw+4),46,tw,tab==i,MINT,()->{
            if(tab==1&&!saveRouting())return;tab=next;page=0;detail=null;editing=false;recording=false;error="";rebuildWidgets();
        }));}
        if(tab==1)routing();
        else if(tab==2)shortcuts();
        else if(tab==3)tester();
        else if(detail==null)listing();
        else button("Party command - "+(NucleusMod.CONFIG.partyDisabled.contains(detail.key())?"OFF":"ON"),left,69,w,()->{
            var cfg=NucleusMod.CONFIG;if(!cfg.partyDisabled.remove(detail.key()))cfg.partyDisabled.add(detail.key());cfg.save();rebuildWidgets();
        });
        button("Back",width/2-43,height-24,86,()->onClose());
    }
    private List<PartyCatalog.Entry> matches(){
        String q=query.toLowerCase(Locale.ROOT).trim().replaceFirst("^!","");
        return PartyCatalog.entries.stream().filter(e->(e.names()+" "+e.source()+" "+e.description()).toLowerCase(Locale.ROOT).contains(q)).toList();
    }
    private void listing(){
        var cfg=NucleusMod.CONFIG;
        button("Party replies - "+(cfg.partyChatEnabled?"ON":"OFF"),left,70,125,()->{
            cfg.partyChatEnabled=!cfg.partyChatEnabled;cfg.save();PartyChat.reset();rebuildWidgets();
        });
        int offset=131;
        var search=input("Find command or alias",left+offset,70,w-offset,query,48);
        search.setHint(Component.literal("Find command or alias"));
        int rowTop=110;
        rows=Math.max(1,(height-rowTop-50)/20);
        var rowButtons=new ArrayList<NucleusUi.NButton>();
        for(int i=0;i<rows;i++){int index=i;rowButtons.add(button("",left,rowTop+20*i,w,()->{
            var found=matches();int k=page*rows+index;if(k>=found.size())return;
            detail=found.get(k);rebuildWidgets();
        }));}
        var prev=button("<",left,height-46,32,()->{page--;rebuildWidgets();});
        var next=button(">",left+w-32,height-46,32,()->{page++;rebuildWidgets();});
        Runnable refresh=()->{
            var found=matches();int pages=Math.max(1,(found.size()+rows-1)/rows);page=Math.clamp(page,0,pages-1);
            for(int i=0;i<rows;i++){
                int k=page*rows+i;var b=rowButtons.get(i);b.visible=k<found.size();if(!b.visible)continue;
                var e=found.get(k);
                b.setMessage(Component.literal("!"+e.key()+" - "+(cfg.partyDisabled.contains(e.key())?"OFF":"ON")+"    "+e.source()));
                b.setTooltip(Tooltip.create(Component.literal(e.description()+"\n"+e.names())));
            }
            prev.active=page>0;next.active=page+1<pages;pageLabel=found.isEmpty()?"No matches":(page+1)+" / "+pages;
        };
        search.setResponder(text->{query=text;page=0;refresh.run();});refresh.run();
    }
    private void tester(){
        if(!NucleusMod.CONFIG.testCommands){
            button("General settings",left,95,w,()->minecraft.setScreen(new KcaMenu(KcaMenu.Page.GLOBAL,this)));
            return;
        }
        var box=input("Party command, chat line, or /shortcut",left,84,w,sample,512);
        box.setResponder(value->{sample=value;result="";});
        button("Test locally",left,107,w,()->result=PartyChat.test(sample));
    }
    private void beginEdit(String key){
        oldKey=key;alias=key==null?"":key;target=key==null?"":Shortcuts.entries(group).get(key);recording=false;
        editing=true;error="";rebuildWidgets();
    }
    private void saveShortcut(boolean remove){
        try{
            Shortcuts.update(group,oldKey,remove?null:alias.strip(),target.strip());
            editing=false;error="";rebuildWidgets();
        }catch(java.io.IOException|IllegalArgumentException ex){error=ex.getMessage();}
    }
    private void shortcuts(){
        int half=(w-4)/2;
        if(editing){
            if(group==Shortcuts.Group.KEY)button(recording?"Press keys, then release":alias.isEmpty()?"Choose keys":keyLabel(alias),left,81,w,()->{recorded.clear();recording=true;rebuildWidgets();});
            else {var key=input("Alias",left,81,w,alias,256);key.setResponder(v->alias=v);}
            var dst=input("Expands to",left,115,w,target,256);dst.setResponder(v->target=v);
            button("Save",left,140,half,()->saveShortcut(false));
            if(oldKey!=null)button("Remove",left+half+4,140,half,()->saveShortcut(true));
            return;
        }
        var cfg=NucleusMod.CONFIG;
        button("KCA shortcuts - "+(cfg.shortcutsEnabled?"ON":"OFF"),left,69,half,()->{cfg.shortcutsEnabled=!cfg.shortcutsEnabled;cfg.save();rebuildWidgets();});
        button("Import Skyblocker",left+half+4,69,half,()->{try{int count=Shortcuts.importMissing();error="Imported "+count+" new shortcuts.";}catch(Exception ex){error="Import failed. Check the Skyblocker file.";}rebuildWidgets();});
        int third=(w-8)/3;
        for(var category:Shortcuts.Group.values()){
            addRenderableWidget(KcaTheme.tab(category.title,left+category.ordinal()*(third+4),91,third,category==group,MINT,()->{group=category;page=0;error="";rebuildWidgets();}));
        }
        button(group.title+" - "+(group.enabled()?"ON":"OFF"),left,131,half,()->{group.toggle();rebuildWidgets();});
        button("New shortcut",left+half+4,131,half,()->beginEdit(null));
        var all=new ArrayList<>(Shortcuts.entries(group).entrySet());
        int per=Math.max(1,(height-203)/20),pages=Math.max(1,(all.size()+per-1)/per);
        page=Math.clamp(page,0,pages-1);
        for(int j=0;j<per && page*per+j<all.size();j++){
            var e=all.get(page*per+j);String label=(group==Shortcuts.Group.KEY?keyLabel(e.getKey()):e.getKey())+"  →  "+e.getValue();
            button(font.plainSubstrByWidth(label,w-16),left,153+j*20,w,()->beginEdit(e.getKey())).setTooltip(Tooltip.create(Component.literal(label)));
        }
        button("<",left,height-45,32,()->{page--;rebuildWidgets();}).active=page>0;
        button(">",left+w-32,height-45,32,()->{page++;rebuildWidgets();}).active=page+1<pages;
        pageLabel=(page+1)+" / "+pages;
    }
    private String keyLabel(String binding){
        return String.join(" + ",Arrays.stream(binding.split(" \\+ ")).map(k->com.mojang.blaze3d.platform.InputConstants.getKey(k).getDisplayName().getString()).toList());
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent e){
        if(!recording)return super.keyPressed(e);
        if(e.key()==256){recording=false;rebuildWidgets();return true;}
        recorded.add(com.mojang.blaze3d.platform.InputConstants.getKey(e).getName());return true;
    }
    @Override public boolean keyReleased(net.minecraft.client.input.KeyEvent e){
        if(!recording||recorded.isEmpty())return super.keyReleased(e);
        alias=String.join(" + ",recorded);recording=false;rebuildWidgets();return true;
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e,boolean dbl){
        if(!recording)return super.mouseClicked(e,dbl);
        recorded.add(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(e.button()).getName());
        alias=String.join(" + ",recorded);recording=false;rebuildWidgets();return true;
    }
    private String pageLabel="";
    private void routing(){
        var cfg=NucleusMod.CONFIG;int half=(w-6)/2;
        var trigger=input("Command prefixes",left,82,half,triggers,48);
        var channel=input("Party channel labels",left+half+6,82,half,markers,100);
        trigger.setTooltip(Tooltip.create(Component.literal("What players type before a command. For example, ! makes !coords work. Separate extra prefixes with commas.")));
        channel.setTooltip(Tooltip.create(Component.literal("Text at the start of an incoming party line, such as Party > or .pc. Separate labels with commas.")));
        trigger.setResponder(v->triggers=v);channel.setResponder(v->markers=v);
        button("Reply - /"+cfg.partyReplyRoute,left,106,half,()->{cfg.partyReplyRoute=cfg.partyReplyRoute.equals("pchat")?"pc":"pchat";cfg.save();rebuildWidgets();}).setTooltip(Tooltip.create(Component.literal("Use /pchat to send replies directly to the party even when /pc is a personal shortcut.")));
        if(KcaEdition.KUUDRA)button("!profit - "+cfg.partyProfitSource,left+half+6,106,half,()->{
            cfg.partyProfitSource=switch(cfg.partyProfitSource){case "Auto"->"IQ";case "IQ"->"SBO";default->"Auto";};cfg.save();rebuildWidgets();
        }).setTooltip(Tooltip.create(Component.literal("Auto uses IQ for Kuudra profit when installed, then SBO for Diana.")));
        button("All Invite auto-off (60s) - "+(cfg.partyAllInviteOff?"ON":"OFF"),left,129,w,()->{cfg.partyAllInviteOff=!cfg.partyAllInviteOff;cfg.save();rebuildWidgets();});
    }
    private boolean saveRouting(){
        if(triggers.isBlank()||markers.isBlank()||Arrays.stream(triggers.split(",",-1)).map(String::trim).anyMatch(p->!p.matches("[^\\p{L}\\p{N}\\s,:]{1,5}"))
            || Arrays.stream(markers.split(",",-1)).map(String::trim).anyMatch(s->s.isEmpty()||s.contains(":"))){
            error="Enter comma-separated prefixes and party labels.";return false;
        }
        var cfg=NucleusMod.CONFIG;cfg.partyTriggers=triggers;cfg.partyChannelMarkers=markers;cfg.save();return true;
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float t){KcaTheme.backdrop(g,width,height,KcaTheme.Branch.CHAT);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float t){
        KcaTheme.heading(g,font,"KCA",width/2,5,2);
        g.centeredText(font,"Chat commands / "+TABS[tab],width/2,29,MINT);
        if(tab==3){
            String hint=NucleusMod.CONFIG.testCommands?"Party command, chat line, or /shortcut":"Enable Test commands in General settings.";
            g.text(font,hint,left,70,MINT,false);
            if(NucleusMod.CONFIG.testCommands){
                String note=result.isEmpty()?"Try !coords for your reply, !warp for the party action, or /pc for a shortcut. Results appear here only.":result;
                g.textWithWordWrap(font,Component.literal(note),left,135,w,KcaTheme.PETAL);
            }
        }else if(tab==2&&editing){
            g.text(font,group==Shortcuts.Group.KEY?"Key binding":"What you type",left,69,MINT,false);g.text(font,"Expands to",left,103,MINT,false);
            if(!error.isEmpty())g.textWithWordWrap(font,Component.literal(error),left,163,w,0xFFFF9292);
        }else if(tab==2){
            g.textWithWordWrap(font,Component.literal(error.isEmpty()?groupHint():error),left,112,w,KcaTheme.PETAL);
            if(group==Shortcuts.Group.KEY && Shortcuts.entries(group).isEmpty())g.centeredText(font,"No key bindings. Choose New shortcut to add one.",width/2,157,MINT);
            g.centeredText(font,pageLabel,width/2,height-40,MINT);
        }else if(tab==1){
            g.text(font,"Command prefixes",left,70,MINT,false);g.text(font,"Party channel labels",left+(w-6)/2+6,70,MINT,false);
            String prefix=Arrays.stream(triggers.split(",")).map(String::trim).filter(v->!v.isEmpty()).findFirst().orElse("!");
            String label=Arrays.stream(markers.split(",")).map(String::trim).filter(v->!v.isEmpty()).findFirst().orElse("Party >");
            String note=error.isEmpty()?label+" Alex: "+prefix+"coords\nAlex types "+prefix+"coords in party chat. KCA recognizes "+label+" and replies with your X, Y and Z using /"+NucleusMod.CONFIG.partyReplyRoute+".":error;
            g.textWithWordWrap(font,Component.literal(note),left,152,w,error.isEmpty()?KcaTheme.PETAL:0xFFFF9292);
        }else if(detail!=null){
            g.text(font,"!"+detail.key()+" "+detail.usage(),left,95,MINT,true);
            g.text(font,detail.source(),left,109,KcaTheme.PETAL,false);
            g.textWithWordWrap(font,Component.literal(detail.description()+"\n\n"+String.join(", ",Arrays.stream(detail.names().split(" ")).map(v->"!"+v).toList())),left,124,w,0xFFDDDFE4);
        }else {
            if(tab==0)g.text(font,NucleusMod.CONFIG.partyChatEnabled?"Select a command for help and its ON/OFF switch.":"Party replies are off. Shortcuts have their own switch.",left,94,KcaTheme.PETAL,false);
            g.centeredText(font,pageLabel,width/2,height-40,MINT);
        }
        super.extractRenderState(g,x,y,t);
    }
    @Override public void onClose(){if(tab==1&&!saveRouting())return;if(editing){editing=false;recording=false;error="";rebuildWidgets();}else if(detail!=null){detail=null;rebuildWidgets();}else minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
