package dev.purplepad;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Uses the live HUD renderers and coordinate conventions in the shared layout editor. */
public final class DungeonHudPreview {
    public final boolean timer;
    public final float width,height,scale;
    public final int x,y;
    private final int screenW,screenH,textWidth;
    private final Font font;
    private final List<String> lines;
    private final TimerConfig config;
    public DungeonHudPreview(boolean timer,Font font,int screenW,int screenH){
        this.timer=timer;this.font=font;this.screenW=screenW;this.screenH=screenH;
        config=PurplePadClient.timerConfig();
        lines=RunHudEditor.sample();
        textWidth=lines.stream().mapToInt(font::width).max().orElse(0);
        if(timer){
            width=TimerHudRenderer.baseWidth(font,config);height=TimerHudRenderer.baseHeight(font,config);scale=config.scale;
            var b=TimerHudRenderer.bounds(font,config,screenW,screenH);x=Math.round(b.x());y=Math.round(b.y());
        }else{
            width=textWidth+(RunStats.simple?2:12);
            height=lines.size()*(font.lineHeight+1)+(RunStats.simple?0:8);scale=RunStats.scale;
            var b=box(scale);x=b.x();y=b.y();
        }
    }
    private CounterPlacement box(float scale){
        return CounterPlacement.at(screenW,screenH,(int)Math.ceil(width*scale),(int)Math.ceil(height*scale),RunStats.positionX,RunStats.positionY);
    }
    public void draw(GuiGraphicsExtractor g,int x,int y,float scale){
        if(timer)TimerHudRenderer.draw(g,font,config,"2.80s",x,y,scale);
        else{
            var b=box(scale);
            RunHudEditor.draw(g,font,lines,textWidth,RunStats.simple,CounterPlacement.fraction(x,b.travelX()),CounterPlacement.fraction(y,b.travelY()),scale);
        }
    }
    public boolean enabled(){return timer?PurplePadClient.timerConfig().enabled:RunStats.hud;}
    public void setEnabled(boolean value){
        if(!timer){RunStats.options(value,RunStats.chat);return;}
        var next=PurplePadClient.timerConfig();next.enabled=value;
        try{PurplePadClient.applySettings(next);}catch(java.io.IOException e){
            Minecraft.getInstance().gui.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal("Could not save timer settings."));
        }
    }
    public void save(int x,int y,float scale){
        if(timer){
            var c=PurplePadClient.timerConfig();c.scale=scale;c.x=x;c.y=y;
            try{PurplePadClient.applySettings(c);}catch(java.io.IOException e){
                Minecraft.getInstance().gui.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal("Could not save timer placement."));
            }
        }else{
            var b=box(scale);RunStats.positionX=CounterPlacement.fraction(x,b.travelX());RunStats.positionY=CounterPlacement.fraction(y,b.travelY());
            RunStats.scale=scale;RunStats.saveOptions();
        }
    }
}
