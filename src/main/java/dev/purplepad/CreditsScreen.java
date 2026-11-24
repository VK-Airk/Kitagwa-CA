package dev.purplepad;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class CreditsScreen extends Screen {
    private final Screen parent;
    private final FanArt artwork;
    CreditsScreen(Screen parent,FanArt artwork){
        super(Component.literal("Credits"));this.parent=parent;this.artwork=artwork;
    }
    @Override protected void init(){
        addRenderableWidget(new TimerSettingsScreen.PixelButton(width/2-40,height-25,80,19,"Back",KcaTheme.MUTED,b->onClose()));
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float t){KcaTheme.backdrop(g,width,height,KcaTheme.Branch.GENERAL);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float t){
        KcaTheme.heading(g,font,"KCA",width/2,5,2);
        g.centeredText(font,"Credits",width/2,29,KcaTheme.PETAL);
        artwork.draw(g,10,42,width-20,Math.max(1,height-108));
        g.centeredText(font,artwork.credit+"  ·  "+artwork.width+" × "+artwork.height,width/2,height-60,KcaTheme.PETAL);
        g.centeredText(font,"Mado (Madobrick)",width/2,height-47,KcaTheme.PETAL);
        g.centeredText(font,"Original wheel animation and final drop reveal",width/2,height-37,KcaTheme.MUTED);
        super.extractRenderState(g,x,y,t);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
