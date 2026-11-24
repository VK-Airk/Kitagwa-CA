package dev.purplepad;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

final class KuudraScreen extends Screen {
    static final Identifier ART=FanArt.KUUDRA.texture;
    static final int ACCENT=0xFFF47799;
    private final Screen parent;
    KuudraScreen(Screen parent){super(Component.literal("Kuudra"));this.parent=parent;}
    @Override protected void init(){
        int size=Math.max(1,Math.min(width-24,height-80));
        var credits=addRenderableWidget(new net.minecraft.client.gui.components.Button((width-size)/2,44,size,size,Component.literal("Artwork credits"),b->minecraft.gui.setScreen(new CreditsScreen(this,FanArt.KUUDRA)),label->label.get()){
            @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float tick){
                FanArt.KUUDRA.draw(g,getX(),getY(),getWidth(),getHeight());
            }
        });
        credits.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("View artwork credits")));
        addRenderableWidget(new TimerSettingsScreen.PixelButton(width/2-40,height-25,80,19,"Back",ACCENT,b->onClose()));
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float t){KcaTheme.backdrop(g,width,height,KcaTheme.Branch.KUUDRA);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float t){
        KcaTheme.heading(g,font,"KCA",width/2,6,2);
        g.centeredText(font,getTitle(),width/2,30,ACCENT);
        int size=Math.min(width-24,height-80),left=(width-size)/2;
        KcaTheme.panel(g,left-2,42,size+4,size+4);
        super.extractRenderState(g,x,y,t);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
