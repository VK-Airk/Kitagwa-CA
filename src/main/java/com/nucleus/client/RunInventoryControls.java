package com.nucleus.client;

import com.nucleus.NucleusMod;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/** Inventory-only controls anchored to the speedrun HUD's saved position. */
final class RunInventoryControls {
    private static final int WIDTH=150, ROW=15;
    private static final Component START=Component.literal("[Start run]"), STOP=Component.literal("[Stop run]");
    private static final Component DOWNTIME=Component.literal("[Downtime]"), RESUME=Component.literal("[Resume]");
    private final Minecraft client;
    private final InventoryScreen inventory;
    private final Button toggle,downtime,skip,settings,edit,goal,editTools,scanTools,save,cancel;
    private final EditBox total;
    private final java.util.List<net.minecraft.client.gui.components.AbstractWidget> controls;
    private enum Count { TOTAL, GOAL, TOOLS }
    private Count target = Count.TOTAL;
    private boolean editing,visible;
    private int toolX, toolY;
    private int left,top;

    static void register(){
        ScreenEvents.AFTER_INIT.register((mc,screen,w,h)->{
            if(screen instanceof InventoryScreen inventory){
                new RunInventoryControls(mc,inventory);
                new StashInventoryControls(mc,inventory);
            }
        });
    }

    private RunInventoryControls(Minecraft mc,InventoryScreen screen){
        client=mc;
        inventory=screen;
        toggle=link("[Start run]",54,"Start timing, or discard the unfinished attempt.",()->{
            if(running())SpeedrunManager.resetRun(false);
            else{
                if(!NucleusMod.CONFIG.speedrunEnabled){
                    NucleusMod.CONFIG.speedrunEnabled=true;
                    NucleusMod.CONFIG.save();
                }
                SpeedrunManager.startRun(client);
            }
        });
        downtime=link("[Downtime]",65,"Pause or resume the timer and its downtime warning.",()->{
            SpeedrunManager.toggleDowntime();
            layout();
        });
        skip=link("[Skip]",33,"Advance past the current split and keep timing.",SpeedrunManager::skipSplit);
        settings=link("[Settings]",57,"Open speedrun settings, run goal and tracked total.",()->
                client.setScreen(new KcaNucleusSettings(KcaNucleusSettings.sections(KcaNucleusSettings.Tab.SPEEDRUNS).getFirst(),inventory)));
        total=new EditBox(mc.font,0,0,64,ROW,Component.literal("Run count"));
        total.setMaxLength(20);
        total.setTooltip(Tooltip.create(Component.literal("Enter a number to set it, or +50 to add 50.")));
        Screens.getWidgets(screen).add(total);
        edit=link("[Edit total]",69,"Change completed runs. Enter +5 to add five runs.",()->beginEdit(Count.TOTAL));
        goal=link("[Edit goal]",69,"Change the target. Enter +50 to raise the goal by 50.",()->beginEdit(Count.GOAL));
        editTools=link("[Edit sets]",65,"Set your complete Divan tool sets, or enter +5 to add five.",()->beginEdit(Count.TOOLS));
        scanTools=link("[Scan bags]",69,"Count complete sets across your active profile’s backpacks.",()->
            ToolSets.scan(mc.gui.getChat()::addClientSystemMessage));
        save=link("[Save]",36,"Apply this edit.",()->{
            long value=editedCount();
            if(value<0)return;
            if (target == Count.TOOLS) ToolSets.set(value);
            else {
                if (target == Count.GOAL) NucleusMod.SPEEDRUN.runGoal=value;
                else NucleusMod.SPEEDRUN.manualRunTotal=value;
                NucleusMod.SPEEDRUN.save();
            }
            finishEdit();
        });
        cancel=link("[Cancel]",46,"Discard the edit.",this::finishEdit);
        total.setResponder(value->save.active=editedCount()>=0);
        save.active=false;
        controls=java.util.List.of(toggle,downtime,skip,settings,edit,goal,editTools,scanTools,save,cancel,total);
        layout();
        ScreenEvents.beforeExtract(screen).register((s,g,mx,my,dt)->layout());
        ScreenEvents.afterExtract(screen).register((s,g,mx,my,dt)->{
            if(!visible)return;
            if (NucleusMod.CONFIG.showToolSets)
                SpeedrunHud.drawHudLine(g,NucleusMod.SPEEDRUN.toolSetLabel(),toolX,toolY,NucleusMod.CONFIG.toolHudScale,0xFFFFD6AE);
            if(SpeedrunManager.state()!=SpeedrunManager.State.IDLE)
                SpeedrunHud.drawTimer(g,left,top,SpeedrunHud.timerScale());
            else {
                g.text(mc.font,"Nucleus speedrun",left,top,0xFFFFD6AE,true);
                if(NucleusMod.CONFIG.showRunCounter)g.text(mc.font,SpeedrunManager.counter(),left,top+10,0xFFE1CDCD,true);
            }
        });
    }

    private static boolean running(){return SpeedrunManager.state()==SpeedrunManager.State.RUNNING;}

    private Button link(String label,int width,String hint,Runnable action){
        var button=new Button(0,0,width,ROW,Component.literal(label),b->action.run(),java.util.function.Supplier::get){
            @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float dt){
                int color=!active?0xFF8F878D:isHoveredOrFocused()?0xFFFFFFFF:0xFFFFA6C3;
                g.text(client.font,getMessage(),getX(),getY()+3,color,true);
            }
        };
        button.setTooltip(Tooltip.create(Component.literal(hint)));
        Screens.getWidgets(inventory).add(button);
        return button;
    }

    private long currentCount(){
        return switch(target) {
            case GOAL -> NucleusMod.SPEEDRUN.runGoal;
            case TOOLS -> NucleusMod.SPEEDRUN.remainingToolSets;
            case TOTAL -> NucleusMod.SPEEDRUN.displayedRuns();
        };
    }

    private long editedCount(){
        return com.nucleus.SpeedrunStore.editedRunCount(total.getValue(),currentCount(),target==Count.GOAL?1:0);
    }

    private void beginEdit(Count kind){
        target=kind;
        editing=true;
        total.setMessage(Component.literal(switch(target){case GOAL -> "Run goal"; case TOTAL -> "Lifetime runs"; case TOOLS -> "Tool sets";}));
        total.setValue(Long.toString(currentCount()));
        layout();
        inventory.setFocused(total);
        total.setHighlightPos(0);
    }

    private void finishEdit(){
        editing=false;
        layout();
        inventory.setFocused(target==Count.TOOLS?editTools:target==Count.GOAL?goal:edit);
    }

    private void layout(){
        var cfg=NucleusMod.CONFIG;
        var store=NucleusMod.SPEEDRUN;
        visible=cfg.kcaEnabled && cfg.showGameplayHud && HollowsDetector.isInCrystalHollows() && !JackpotAnimation.cinematicActive();
        if(!visible){
            if(toggle.visible) for(var control:controls) control.visible=false;
            return;
        }
        var state=SpeedrunManager.state();
        int lines=1+(running()?1:0)+(cfg.showRunCounter?1:0);
        if(state!=SpeedrunManager.State.IDLE && store.bestTotalMs>=0)lines++;
        int textHeight=Math.round(lines*10*(state==SpeedrunManager.State.IDLE?1f:SpeedrunHud.timerScale()))+4;
        left=Math.clamp(store.speedTimerX,3,Math.max(3,inventory.width-WIDTH-3));
        top=Math.clamp(store.speedTimerY,3,Math.max(3,inventory.height-textHeight-(running()?3:2)*ROW-4));
        int y=top+textHeight;
        toggle.setPosition(left,y);
        toggle.setMessage(running()?STOP:START);
        downtime.setPosition(left,y+2*ROW);
        downtime.setMessage(SpeedrunManager.manuallyPaused()?RESUME:DOWNTIME);
        skip.setPosition(left+55,y);
        settings.setPosition(left+91,y);
        edit.setPosition(left,y+ROW);
        goal.setPosition(left+72,y+ROW);
        int toolHeight = (int)Math.ceil(12*cfg.toolHudScale)+3;
        toolX=Math.clamp(cfg.toolHudX,3,Math.max(3,inventory.width-WIDTH-3));
        toolY=Math.clamp(cfg.toolHudY,3,Math.max(3,inventory.height-toolHeight-ROW-3));
        int toolsRow=toolY+toolHeight;
        editTools.setPosition(toolX,toolsRow);
        scanTools.setPosition(toolX+67,toolsRow);
        boolean toolEditor=editing && target==Count.TOOLS;
        int editX=toolEditor?toolX:left, editY=toolEditor?toolsRow:y+ROW;
        total.setPosition(editX,editY);
        save.setPosition(editX+66,editY);
        cancel.setPosition(editX+104,editY);
        toggle.visible=settings.visible=visible;
        downtime.visible=skip.visible=visible && running();
        skip.active=!SpeedrunManager.manuallyPaused();
        edit.visible=goal.visible=visible && (!editing || toolEditor);
        editTools.visible=scanTools.visible=visible && cfg.showToolSets && !toolEditor;
        total.visible=save.visible=cancel.visible=visible && editing && (!toolEditor || cfg.showToolSets);
        if(total.visible && (inventory.getFocused()==edit || inventory.getFocused()==goal || inventory.getFocused()==editTools))inventory.setFocused(total);
    }
}
