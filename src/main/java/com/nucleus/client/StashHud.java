package com.nucleus.client;

import com.nucleus.NucleusMod;
import com.nucleus.NucleusConfig.StashCounts;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class StashHud {
    private static final Pattern TOTAL = Pattern.compile("^You have ([\\d,]+) (item|material)s? stashed away!.*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGACY = Pattern.compile("^You have [\\d,]+ materials? totall?ing ([\\d,]+) items? stashed away!.*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern PICKED = Pattern.compile("^You picked up (all|[\\d,]+) items? from your (item|material) stash!.*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern ADDED = Pattern.compile("^One or more items didn't fit in your inventory and were added to your (item|material) stash!.*$",Pattern.CASE_INSENSITIVE);
    private static final long UNKNOWN=-1;
    private static long items=UNKNOWN, materials=UNKNOWN;
    private static boolean reported, itemsPending, materialsPending;
    private static StashCounts cachedMode;
    private static String text;
    private StashHud() {}

    static void reset() {items=materials=UNKNOWN;itemsPending=materialsPending=reported=false;text=null;}
    static StashCounts mode() {
        var mode=NucleusMod.CONFIG.stashCounts;
        return mode==null?StashCounts.BOTH:mode;
    }
    static void cycleMode() {
        var cfg=NucleusMod.CONFIG;
        cfg.stashCounts=switch(mode()) {case BOTH -> StashCounts.ITEMS; case ITEMS -> StashCounts.MATERIALS; case MATERIALS -> StashCounts.BOTH;};
        cfg.save();
    }
    private static String format(long itemCount,long materialCount,boolean pendingItems,boolean pendingMaterials) {
        return "Stash: "+switch(mode()) {
            case ITEMS -> display(itemCount,pendingItems)+" items";
            case MATERIALS -> display(materialCount,pendingMaterials)+" materials";
            case BOTH -> display(itemCount,pendingItems)+" items / "+display(materialCount,pendingMaterials)+" materials";
        };
    }
    static String sample(){return format(12,30,false,false);}
    static String hudText(){
        if(text==null || cachedMode!=mode()){cachedMode=mode();text=format(items,materials,itemsPending,materialsPending);}
        return text;
    }
    static String label() {return reported?hudText():"Stash: waiting for a SkyBlock stash message";}
    static boolean inArea(){return SkyBlockDetector.isOnSkyBlock() && (!NucleusMod.CONFIG.stashHollowsOnly || HollowsDetector.isInCrystalHollows());}

    static void chat(String raw) {
        if (!raw.toLowerCase(java.util.Locale.ROOT).contains("stash") && !raw.contains("Switching to profile")) return;
        String message=HollowsDetector.stripFormatting(raw).strip();
        if (ChatLines.isPlayerChat(message)) return;
        if (message.startsWith("Switching to profile")) {reset();return;}
        if (message.equals("Your stash isn't holding any items or materials!")) {set("item",0);set("material",0);}
        else {
            var total=TOTAL.matcher(message);
            var picked=PICKED.matcher(message);
            var added=ADDED.matcher(message);
            var legacy=LEGACY.matcher(message);
            if (total.matches()) set(total.group(2),number(total.group(1)));
            else if (picked.matches()) {
                String kind=picked.group(2);
                long old=kind.equalsIgnoreCase("item")?items:materials;
                long left=picked.group(1).equalsIgnoreCase("all")?0:
                    old==UNKNOWN?UNKNOWN:Math.max(0,old-number(picked.group(1)));
                if (picked.group(1).equalsIgnoreCase("all") || !(kind.equalsIgnoreCase("item") ? itemsPending : materialsPending))
                    set(kind,left);
            } else if (added.matches()) {
                if (added.group(1).equalsIgnoreCase("item")) itemsPending=true;
                else materialsPending=true;
            }
            else if (legacy.matches()) set("material",number(legacy.group(1)));
            else return;
        }
        reported=true;
        text=null;
    }
    private static long number(String value){return Long.parseLong(value.replace(",",""));}
    private static void set(String kind,long count){if(kind.equalsIgnoreCase("item")){items=count;itemsPending=false;}else{materials=count;materialsPending=false;}}
    private static String display(long value,boolean pending){return value==UNKNOWN?"?":Long.toString(value)+(pending?" (last known)":"");}
    static void draw(GuiGraphicsExtractor g){
        var cfg=NucleusMod.CONFIG;
        if(!cfg.showStash || !reported || !inArea() || JackpotAnimation.cinematicActive()
            || Minecraft.getInstance().gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)return;
        String text=hudText();
        int w=Minecraft.getInstance().font.width(text);
        int x=Math.clamp(cfg.stashX,3,Math.max(3,g.guiWidth()-3-(int)Math.ceil(w*cfg.stashScale)));
        int y=Math.clamp(cfg.stashY,3,Math.max(3,g.guiHeight()-3-(int)Math.ceil(12*cfg.stashScale)));
        SpeedrunHud.drawHudLine(g,text,x,y,cfg.stashScale,0xFFFFC2DC,w);
    }
}
