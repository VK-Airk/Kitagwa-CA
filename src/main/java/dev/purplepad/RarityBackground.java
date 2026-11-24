package dev.purplepad;

import com.nucleus.NucleusMod;
import com.nucleus.NucleusConfig.RarityShape;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.registries.BuiltInRegistries;
import com.nucleus.client.SkyBlockDetector;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public final class RarityBackground {
    enum Tier {
        COMMON(0xffffff), UNCOMMON(0x55ff55), RARE(4561919,0x5555ff), EPIC(10696174,0xaa00aa),
        LEGENDARY(16748544,0xffaa00), MYTHIC(0xff55ff), DIVINE(0x55ffff), SPECIAL(0xff5555),
        VERY_SPECIAL(0xff5555), ULTIMATE(13709864,0xaa0000), ADMIN(13709864,0xaa0000);
        final int modern,legacy;
        final String label;
        Tier(int rgb){this(rgb,rgb);}
        Tier(int modern,int legacy){
            this.modern=modern;this.legacy=legacy;
            label=name().replace('_',' ');
        }
    }
    static final Tier[] TIERS=Tier.values();
    private static final HashMap<String,Tier> STYLE=new HashMap<>();
    private static final IdentityHashMap<ItemLore,Tier> CACHE=new IdentityHashMap<>();
    private static final java.util.EnumMap<RarityShape,Identifier> SPRITES=new java.util.EnumMap<>(RarityShape.class);
    private static final IdentityHashMap<CustomData,String> ITEM_IDS=new IdentityHashMap<>();
    static {
        for(var shape:RarityShape.values()){
            String suffix=shape==RarityShape.Circle?"circular":shape.name().toLowerCase(Locale.ROOT);
            SPRITES.put(shape,Identifier.fromNamespaceAndPath("kca","item_background_"+suffix));
        }
        for(Tier t:TIERS)STYLE.put(t.name().toLowerCase(Locale.ROOT),t);
        STYLE.put("supreme",Tier.DIVINE);
    }
    public static boolean active(){
        var cfg=NucleusMod.CONFIG;
        return cfg!=null && cfg.kcaEnabled && cfg.rarityBackgrounds && SkyBlockDetector.isOnSkyBlock();
    }
    private static Tier tier(ItemStack stack){
        var style=stack.get(DataComponents.TOOLTIP_STYLE);
        if(style!=null && style.getNamespace().equals("hypixel_skyblock")){
            Tier tier=STYLE.get(style.getPath());
            if(tier!=null)return tier;
        }
        var lore=stack.get(DataComponents.LORE);
        if(lore==null)return null;
        if(CACHE.containsKey(lore))return CACHE.get(lore);
        Tier found=null;
        for(int i=lore.lines().size()-1;i>=0 && found==null;i--){
            String line=net.minecraft.ChatFormatting.stripFormatting(lore.lines().get(i).getString()).strip();
            line=line.replaceFirst("^✪?\\s*[^a-zA-Z0-9 ]*\\s*","").strip();
            for(Tier t:TIERS)if(line.equals(t.label)||line.startsWith(t.label+" ")){found=t;break;}
        }
        if(CACHE.size()>=512)CACHE.clear();
        CACHE.put(lore,found);
        return found;
    }
    static int color(Tier t){
        var cfg=NucleusMod.CONFIG;
        return cfg.rarityColors.getOrDefault(t.name(),cfg.rarityLegacy?t.legacy:t.modern);
    }
    static String canonicalKey(String id){
        String value=id.strip();
        return value.contains(":")?value.toLowerCase(Locale.ROOT):value.toUpperCase(Locale.ROOT);
    }
    static String itemKey(ItemStack stack){
        if(stack==null || stack.isEmpty())return "";
        var data=stack.get(DataComponents.CUSTOM_DATA);
        if(data!=null){
            String id=ITEM_IDS.get(data);
            if(id==null){
                id=canonicalKey(data.copyTag().getStringOr("id",""));
                if(ITEM_IDS.size()>=512)ITEM_IDS.clear();
                ITEM_IDS.put(data,id);
            }
            if(!id.isEmpty())return id;
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
    static void swatch(GuiGraphicsExtractor g,int x,int y,int size,RarityShape shape,int rgb){
        int alpha=NucleusMod.CONFIG.rarityOpacity*255/100;
        if(alpha>0)g.blitSprite(RenderPipelines.GUI_TEXTURED,SPRITES.get(shape),x,y,size,size,(alpha<<24)|(rgb&0xffffff));
    }
    public static void drawStorage(GuiGraphicsExtractor g,ItemStack stack,int x,int y){
        var cfg=NucleusMod.CONFIG;
        if(cfg!=null && cfg.rarityStorage)draw(g,stack,x,y,false);
    }
    public static void draw(GuiGraphicsExtractor g,ItemStack stack,int x,int y,boolean hotbar){
        if(stack.isEmpty() || !active())return;
        var cfg=NucleusMod.CONFIG;
        if(cfg.rarityOpacity==0 || (hotbar && !cfg.rarityHotbar))return;
        var item=cfg.rarityItems.isEmpty()?null:cfg.rarityItems.get(itemKey(stack));
        if(item!=null){
            swatch(g,x,y,16,item.shape()==null?cfg.backgroundShape():item.shape(),item.color());
            return;
        }
        Tier tier=tier(stack);
        if(tier!=null)swatch(g,x,y,16,cfg.backgroundShape(),color(tier));
    }
}
