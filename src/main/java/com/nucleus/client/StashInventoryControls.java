package com.nucleus.client;

import com.nucleus.NucleusMod;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/** Stash controls in the foreground of the player's inventory. */
final class StashInventoryControls {
    private static final int ROW=14, PINK=0xFFFFC2DC;
    private final Minecraft client;
    private final InventoryScreen screen;
    private final Button edit, counts, area;
    private final java.util.List<Button> buttons;
    private boolean editing, dragging, visible;
    private int x,y,w,h;
    private double grabX,grabY;

    StashInventoryControls(Minecraft client, InventoryScreen screen){
        this.client=client;
        this.screen=screen;
        edit=button("[Edit stash]","Edit the stash display. Drag its text to move it and scroll over it to resize.",()->{
            editing=!editing;
            finishDrag();
            updateLabels();
            layout();
        });
        counts=button("","Choose items, materials, or both.",()->{
            StashHud.cycleMode();
            updateLabels();
            layout();
        });
        area=button("","Choose all of SkyBlock or only the Crystal Hollows.",()->{
            var cfg=NucleusMod.CONFIG;
            cfg.stashHollowsOnly=!cfg.stashHollowsOnly;
            cfg.save();
            updateLabels();
            layout();
        });
        buttons=java.util.List.of(edit,counts,area);
        updateLabels();
        layout();
        ScreenEvents.beforeExtract(screen).register((s,g,mx,my,dt)->layout());
        ScreenEvents.afterExtract(screen).register((s,g,mx,my,dt)->{
            if(visible)SpeedrunHud.drawHudLine(g,StashHud.hudText(),x,y,NucleusMod.CONFIG.stashScale,PINK);
        });
        ScreenMouseEvents.allowMouseClick(screen).register((s,event)->{
            if(event.button()!=0 || !visible)return true;
            for(var child:buttons) {
                if(child.visible && child.isMouseOver(event.x(),event.y())){
                    child.mouseClicked(event,false);
                    return false;
                }
            }
            if(!editing || !overText(event.x(),event.y()))return true;
            grabX=event.x()-x;
            grabY=event.y()-y;
            dragging=true;
            return false;
        });
        ScreenMouseEvents.allowMouseDrag(screen).register((s,event,dx,dy)->{
            if(!dragging || event.button()!=0)return true;
            var cfg=NucleusMod.CONFIG;
            cfg.stashX=clampX((int)Math.round(event.x()-grabX),w);
            cfg.stashY=clampY((int)Math.round(event.y()-grabY),h);
            layout();
            return false;
        });
        ScreenMouseEvents.allowMouseRelease(screen).register((s,event)->{
            if(!dragging || event.button()!=0)return true;
            finishDrag();
            return false;
        });
        ScreenMouseEvents.allowMouseScroll(screen).register((s,mx,my,dx,dy)->{
            if(!visible || !editing || dy==0 || !overText(mx,my))return true;
            var cfg=NucleusMod.CONFIG;
            cfg.stashScale=Math.clamp(Math.round(cfg.stashScale*10+Math.signum(dy))/10f,0.5f,3f);
            cfg.save();
            layout();
            return false;
        });
        ScreenEvents.remove(screen).register(s->finishDrag());
    }

    private Button button(String label,String tip,Runnable action){
        var button=new Button(0,0,1,ROW,Component.literal(label),b->action.run(),java.util.function.Supplier::get){
            @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float dt){
                g.text(client.font,getMessage(),getX()+2,getY()+3,isHoveredOrFocused()?0xFFFFFFFF:PINK,true);
            }
        };
        button.setTooltip(Tooltip.create(Component.literal(tip)));
        Screens.getWidgets(screen).add(button);
        return button;
    }

    private void updateLabels(){
        label(edit,editing?"[Done]":"[Edit stash]");
        label(counts,"["+StashHud.mode().label+"]");
        label(area,NucleusMod.CONFIG.stashHollowsOnly?"[Hollows only]":"[All SkyBlock]");
    }

    private void label(Button button,String value){
        button.setMessage(Component.literal(value));
        button.setWidth(client.font.width(value)+5);
    }

    private int clampX(int value,int width){return Math.clamp(value,3,Math.max(3,screen.width-width-3));}
    private int clampY(int value,int height){return Math.clamp(value,3,Math.max(3,screen.height-height-3));}
    private boolean overText(double mx,double my){return mx>=x-2 && mx<x+w+2 && my>=y-2 && my<y+h;}
    private void finishDrag(){
        if(dragging){dragging=false;NucleusMod.CONFIG.save();}
    }

    private void layout(){
        var cfg=NucleusMod.CONFIG;
        visible=cfg.kcaEnabled && cfg.showGameplayHud && cfg.showStash && SkyBlockDetector.isOnSkyBlock()
            && (editing || StashHud.inArea()) && !JackpotAnimation.cinematicActive();
        edit.visible=visible;
        counts.visible=area.visible=visible && editing;
        if(!visible){finishDrag();return;}
        w=(int)Math.ceil(client.font.width(StashHud.hudText())*cfg.stashScale);
        h=(int)Math.ceil(12*cfg.stashScale);
        x=clampX(cfg.stashX,w);
        y=clampY(cfg.stashY,h);
        int rowWidth=edit.getWidth()+(editing?counts.getWidth()+area.getWidth():0);
        int left=clampX(x,rowWidth);
        int top=y+h+2+ROW<=screen.height?y+h+2:Math.max(0,y-ROW-3);
        edit.setPosition(left,top);
        counts.setPosition(left+edit.getWidth(),top);
        area.setPosition(counts.getX()+counts.getWidth(),top);
    }
}
