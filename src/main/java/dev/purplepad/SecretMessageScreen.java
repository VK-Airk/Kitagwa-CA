package dev.purplepad;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class SecretMessageScreen extends Screen {
    private final Screen parent;
    private int tab;
    private String kickDraft=RunStats.kickTemplate;
    private String lowestDraft=RunStats.lowestTemplate;
    private String draft = RunStats.messageTemplate.isBlank()?SecretMessage.DEFAULT_TEMPLATE:RunStats.messageTemplate;
    private EditBox field;
    private int top, left, inner;
    SecretMessageScreen(Screen parent){super(Component.literal("Secrets message"));this.parent=parent;}
    @Override protected void init(){
        inner=Math.min(width-28,336); left=(width-inner)/2; top=Math.max(0,(height-198)/2);
        int half=(inner-4)/2;
        boolean fewest=tab==1, kick=tab==2;
        int tabWidth=(inner-8)/3;
        String[] labels={"Player rows","Fewest","Kick prompt"};
        for(int i=0;i<labels.length;i++){
            int index=i;
            addRenderableWidget(KcaTheme.tab(labels[i],left+i*(tabWidth+4),top+28,tabWidth,tab==i,KcaTheme.ROSE,()->{tab=index;rebuildWidgets();}));
        }
        field=addRenderableWidget(new EditBox(font,left,top+51,inner,20,getTitle()));
        field.setMaxLength(SecretMessage.MAX_LENGTH); field.setValue(kick?kickDraft:fewest?lowestDraft:draft);
        field.setHint(Component.literal("Default report"));field.setResponder(s->{if(kick)kickDraft=s;else if(fewest)lowestDraft=s;else draft=s;});
        setInitialFocus(field);
        if(kick){addRenderableWidget(new TimerSettingsScreen.PixelButton(left,top+139,half,18,
            "65/35 kick prompt - "+(RunStats.suggestKick?"ON":"OFF"),KcaTheme.ROSE,b->{
                RunStats.suggestKick=!RunStats.suggestKick;RunStats.clearStreak();RunStats.saveOptions();rebuildWidgets();
            })).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Offer a kick after four consecutive lowest scores. Secrets count for 65% and rooms for 35% of team contribution. Mages are excluded.")));
            addRenderableWidget(new TimerSettingsScreen.PixelButton(left+half+4,top+139,half,18,
                "Mage BR prompt - "+(RunStats.suggestMage?"ON":"OFF"),KcaTheme.ROSE,b->{
                    RunStats.suggestMage=!RunStats.suggestMage;RunStats.clearStreak();RunStats.saveOptions();rebuildWidgets();
                })).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Offer a kick after four completed runs where blood took over 25 seconds with the same sole Mage.")));
        }
        int w=(inner-8)/3;
        addRenderableWidget(new TimerSettingsScreen.PixelButton(left,top+161,w,20,"Default",KcaTheme.MUTED,b->field.setValue(kick?RunStats.DEFAULT_KICK:fewest?ClearReport.DEFAULT_LOWEST:SecretMessage.DEFAULT_TEMPLATE)));
        addRenderableWidget(new TimerSettingsScreen.PixelButton(left+w+4,top+161,w,20,"Cancel",KcaTheme.MUTED,b->onClose()));
        addRenderableWidget(new TimerSettingsScreen.PixelButton(left+2*(w+4),top+161,w,20,"Save",KcaTheme.ROSE,b->{
            RunStats.kickTemplate=kickDraft.isBlank()?RunStats.DEFAULT_KICK:kickDraft.strip();RunStats.messageTemplate=draft.strip();RunStats.lowestTemplate=lowestDraft.isBlank()?ClearReport.DEFAULT_LOWEST:lowestDraft.strip();RunStats.saveOptions();onClose();
        }));
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float t){KcaTheme.backdrop(g,width,height,KcaTheme.Branch.DUNGEONS);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float t){
        KcaTheme.heading(g,font,"KCA",width/2,top,2);

        boolean fewest=tab==1,kick=tab==2;
        g.text(font,kick?"{name}  {runs}":fewest?"{metric}  {names}  {count}":"{name}  {secrets}  {rooms}  {solo}  {shared}  {deaths}",left,top+74,KcaTheme.PETAL,false);
        KcaTheme.panel(g,left,top+90,inner,45);
        int lineY=top+97;
        for(var line:font.split(Component.literal(kick||fewest?"§"+com.nucleus.NucleusMod.CONFIG.lowestColor+(kick?RunStats.kickText(kickDraft,"Alex")+" [Kick Alex]":String.join("\n",RunStats.sampleLowest(lowestDraft))):RunStats.sampleLine(draft,false,RunStats.simple)),inner-14)){
            if(lineY+font.lineHeight>top+141)break;
            g.text(font,line,left+7,lineY,KcaTheme.PETAL,false);lineY+=font.lineHeight+1;
        }
        super.extractRenderState(g,x,y,t);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
