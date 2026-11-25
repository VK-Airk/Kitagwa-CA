package dev.purplepad;

import com.nucleus.NucleusMod;
import com.nucleus.NucleusConfig.DamageColor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class DamageSettingsScreen extends Screen {
    private final Screen parent;
    private static final Component SAMPLE=Component.literal("✧1,257,400✧").withStyle(ChatFormatting.GOLD);
    private Component preview;
    private int top;
    DamageSettingsScreen(Screen parent){super(Component.literal("Damage numbers"));this.parent=parent;}

    @Override protected void init(){
        var cfg=NucleusMod.CONFIG;
        int w=Math.min(width-16,320),x=(width-w)/2,half=(w-4)/2;
        top=Math.max(2,(height-170)/2);
        boolean styling=cfg.showDamageSplashes && cfg.compactDamageNumbers;
        control(x,30,w,"Damage splashes - "+(cfg.showDamageSplashes?"On":"Off"),true,
            "Show or hide floating hit numbers in SkyBlock.",()->cfg.showDamageSplashes=!cfg.showDamageSplashes);
        control(x,52,half,"Format - "+(cfg.compactDamageNumbers?"Compact":"Original"),cfg.showDamageSplashes,
            "Compact rounds each hit and adds a unit for thousands and above.",()->cfg.compactDamageNumbers=!cfg.compactDamageNumbers);
        control(x+half+4,52,half,"Significant figures - "+DamageNumbers.figures(cfg),styling,
            "Choose 1 to 4 significant figures. The default is 2.",()->cfg.damageSignificantFigures=DamageNumbers.figures(cfg)%4+1);
        control(x,74,half,"Hit symbols - "+(cfg.showDamageSymbols?"On":"Off"),styling,
            "Keep the server's critical stars and damage type symbols.",()->cfg.showDamageSymbols=!cfg.showDamageSymbols);
        control(x+half+4,74,half,"Units - "+(cfg.lowercaseDamageUnits?"k / m / b":"K / M / B"),styling,
            "Choose uppercase or lowercase unit letters.",()->cfg.lowercaseDamageUnits=!cfg.lowercaseDamageUnits);
        control(x,96,w,"Color - "+DamageNumbers.color(cfg),styling,"Original uses the hit number's first digit color.",()->{
            var colors=DamageColor.values();
            cfg.damageColor=colors[(DamageNumbers.color(cfg).ordinal()+1)%colors.length];
        });
        preview=DamageNumbers.format(SAMPLE,cfg);
        addRenderableWidget(new TimerSettingsScreen.PixelButton(width/2-48,top+147,96,18,"Back",KcaTheme.PETAL,b->onClose()));
    }
    private void control(int x,int y,int width,String label,boolean active,String tip,Runnable change){
        var button=addRenderableWidget(new TimerSettingsScreen.PixelButton(x,top+y,width,18,label,KcaTheme.ROSE,b->{
            change.run();NucleusMod.CONFIG.save();rebuildWidgets();
        }));
        button.active=active;
        button.setTooltip(Tooltip.create(Component.literal(tip)));
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float t){KcaTheme.backdrop(g,width,height,KcaTheme.Branch.GENERAL);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float t){
        g.centeredText(font,"Damage numbers",width/2,top+10,KcaTheme.ROSE);
        g.centeredText(font,"Preview",width/2,top+119,KcaTheme.PETAL);
        g.centeredText(font,preview==null?Component.literal("Hidden"):preview,width/2,top+132,0xFFFFFFFF);
        super.extractRenderState(g,x,y,t);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
