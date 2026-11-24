package dev.purplepad;

import com.nucleus.NucleusConfig;
import com.nucleus.NucleusConfig.ItemBackground;
import com.nucleus.NucleusConfig.RarityShape;
import com.nucleus.NucleusMod;
import com.nucleus.client.IntSlider;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

final class RarityEditor {
    private static final Identifier DIAMOND=Identifier.fromNamespaceAndPath("minecraft","textures/item/diamond.png");
    private static final Pattern HEX=Pattern.compile("#?[0-9a-fA-F]{6}");
    private static final Pattern ITEM_ID=Pattern.compile("[A-Z0-9_][A-Z0-9_:.\\-]*|[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final RarityShape[] SHAPES=RarityShape.values();
    private final KcaMenu menu;
    private final NucleusConfig cfg=NucleusMod.CONFIG;
    private RarityBackground.Tier tier=RarityBackground.Tier.LEGENDARY;
    private boolean perItem, opacityDirty;
    private String key="", hex;
    private RarityShape itemShape;
    private int previewColor,previewTop;
    private boolean validColor=true;

    RarityEditor(KcaMenu menu){
        this.menu=menu;
        loadColor(RarityBackground.color(tier));
    }
    private void loadColor(int color){
        previewColor=color;
        hex=String.format(Locale.ROOT,"#%06X",color&0xffffff);
        validColor=true;
    }
    private void loadRule(String id){
        key=id;
        var entry=cfg.rarityItems.get(id);
        itemShape=entry==null?null:entry.shape();
        loadColor(entry==null?RarityBackground.color(tier):entry.color());
    }
    private ItemStack held(){
        var mc=Minecraft.getInstance();
        return mc.level==null || mc.player==null?null:mc.player.getMainHandItem();
    }
    private void persist(){
        cfg.save();
        opacityDirty=false;
    }
    void flush(){if(opacityDirty)persist();}
    private Button button(String label,int x,int y,int width,int tint,Runnable action){
        return menu.button(label,x,y,width,tint,action);
    }
    private static Tooltip tip(String text){return Tooltip.create(Component.literal(text));}
    private void switchMode(boolean items){
        if(perItem==items)return;
        perItem=items;
        if(items){
            String id=key.isEmpty()?RarityBackground.itemKey(held()):key;
            if(id.isEmpty() && !cfg.rarityItems.isEmpty())id=cfg.rarityItems.keySet().iterator().next();
            loadRule(id);
        }else loadColor(RarityBackground.color(tier));
        menu.refreshEditor();
    }
    private String shapeLabel(){
        return perItem && itemShape==null?"Shape - Use global":"Shape - "+(perItem?itemShape:cfg.backgroundShape());
    }
    void controls(int x,int y,int w){
        var font=Minecraft.getInstance().font;
        int step=Math.clamp((menu.height-122)/5,19,23),half=(w-4)/2,right=x+half+4;
        int sideWidth=(w*3/10)-2, masterWidth=w-2*sideWidth-8;
        button("Rarity backgrounds - "+(cfg.rarityBackgrounds?"ON":"OFF"),x,y,masterWidth,KcaTheme.ROSE,()->{
            cfg.rarityBackgrounds=!cfg.rarityBackgrounds;persist();menu.refreshEditor();
        }).setTooltip(tip("Shows item backgrounds in your inventory and open containers on SkyBlock."));
        button("Storage - "+(cfg.rarityStorage?"ON":"OFF"),x+masterWidth+4,y,sideWidth,KcaTheme.PETAL,()->{
            cfg.rarityStorage=!cfg.rarityStorage;persist();menu.refreshEditor();
        }).setTooltip(tip("Show KCA backgrounds on Ender Chest, backpack and Storage Overlay screens when rarity backgrounds are on."));
        button("Hotbar - "+(cfg.rarityHotbar?"ON":"OFF"),x+w-sideWidth,y,sideWidth,KcaTheme.PETAL,()->{
            cfg.rarityHotbar=!cfg.rarityHotbar;persist();menu.refreshEditor();
        }).setTooltip(tip("Also show backgrounds on the nine hotbar slots during gameplay."));
        y+=step;
        menu.editorWidget(KcaTheme.tab("Tier colors",x,y,half,!perItem,KcaTheme.ROSE,()->switchMode(false)));
        menu.editorWidget(KcaTheme.tab("Per-item styles",right,y,half,perItem,KcaTheme.ROSE,()->switchMode(true)));
        y+=step;
        EditBox idBox;
        if(perItem){
            idBox=menu.editorWidget(new EditBox(font,x,y,half,18,Component.literal("Item ID")));
            idBox.setMaxLength(128);idBox.setValue(key);idBox.setHint(Component.literal("SkyBlock item ID"));
            idBox.setTooltip(tip("Enter a SkyBlock ID such as HYPERION, a vanilla ID such as minecraft:diamond, or use your held item."));
            int small=(half-4)/2;
            var fromHand=button("Use held item",right,y,small,KcaTheme.PETAL,()->idBox.setValue(RarityBackground.itemKey(held())));
            fromHand.active=!RarityBackground.itemKey(held()).isEmpty();
            var saved=button("Saved ("+cfg.rarityItems.size()+")",right+small+4,y,half-small-4,KcaTheme.PETAL,()->{
                var ids=cfg.rarityItems.keySet().stream().sorted().toList();
                if(!ids.isEmpty())idBox.setValue(ids.get((ids.indexOf(key)+1)%ids.size()));
            });
            saved.active=!cfg.rarityItems.isEmpty();
            saved.setTooltip(tip("Cycle through your saved item styles to edit or remove one."));
        }else{
            idBox=null;
            button("Palette - "+(cfg.rarityLegacy?"Legacy":"Skyblocker"),x,y,half,KcaTheme.PETAL,()->{
                cfg.rarityLegacy=!cfg.rarityLegacy;loadColor(RarityBackground.color(tier));persist();menu.refreshEditor();
            }).setTooltip(tip("Chooses the default colors for rarity tiers. Skyblocker uses its modern colors. Legacy uses classic rarity colors. Your saved tier and item colors take priority."));
            button("Tier - "+tier.label,right,y,half,0xff000000|RarityBackground.color(tier),()->{
                tier=RarityBackground.TIERS[(tier.ordinal()+1)%RarityBackground.TIERS.length];
                loadColor(RarityBackground.color(tier));menu.refreshEditor();
            });
        }
        y+=step;
        var shape=button(shapeLabel(),x,y,half,KcaTheme.PETAL,()->{
            if(perItem)itemShape=itemShape==null?SHAPES[0]:itemShape.ordinal()+1<SHAPES.length?SHAPES[itemShape.ordinal()+1]:null;
            else{cfg.rarityShape=SHAPES[(cfg.backgroundShape().ordinal()+1)%SHAPES.length];persist();}
            menu.refreshEditor();
        });
        var color=menu.editorWidget(new EditBox(font,right,y,half,18,Component.literal("Background color")));
        color.setTooltip(tip("Enter six hex digits, such as #FF88AA. The preview changes as you type. Apply saves the color."));
        color.setMaxLength(7);color.setValue(hex);
        y+=step;
        var slider=new IntSlider(x,y,half,"Opacity",0,100,cfg.rarityOpacity,"%",v->{cfg.rarityOpacity=v;opacityDirty=true;});
        slider.accent=KcaTheme.ROSE&0xffffff;
        slider.setTooltip(tip("Sets the opacity of every rarity and per-item background."));
        menu.editorWidget(slider);
        int small=(half-4)/2;
        var apply=button("Apply",right,y,small,KcaTheme.ROSE,()->{
            if(perItem)cfg.rarityItems.put(key,new ItemBackground(previewColor,itemShape));
            else cfg.rarityColors.put(tier.name(),previewColor);
            persist();menu.refreshEditor();
        });
        var reset=button(perItem?"Remove":"Reset",right+small+4,y,half-small-4,KcaTheme.MUTED,()->{
            if(perItem){cfg.rarityItems.remove(key);loadRule(key);}
            else{cfg.rarityColors.remove(tier.name());loadColor(RarityBackground.color(tier));}
            persist();menu.refreshEditor();
        });
        Runnable validity=()->{
            apply.active=validColor && (!perItem || ITEM_ID.matcher(key).matches());
            reset.active=perItem?cfg.rarityItems.containsKey(key):cfg.rarityColors.containsKey(tier.name());
        };
        color.setResponder(text->{
            hex=text;validColor=HEX.matcher(text).matches();
            if(validColor)previewColor=Integer.parseInt(text.replace("#",""),16);
            validity.run();
        });
        if(idBox!=null)idBox.setResponder(text->{
            loadRule(RarityBackground.canonicalKey(text));color.setValue(hex);shape.setMessage(Component.literal(shapeLabel()));validity.run();
        });
        validity.run();
        previewTop=y+22;
    }
    void preview(GuiGraphicsExtractor g,int x,int w,int footer){
        int size=Math.min(36,footer-previewTop-5);
        if(size<12)return;
        var mc=Minecraft.getInstance();
        var item=held();
        boolean showHeld=item!=null && !item.isEmpty() && (!perItem || RarityBackground.itemKey(item).equals(key));
        var font=mc.font;
        int left=x+5,top=previewTop+1;
        g.fill(left-1,top-1,left+size+1,top+size+1,0xff363038);
        g.fill(left,top,left+size,top+size,0xff59525a);
        RarityBackground.swatch(g,left,top,size,perItem && itemShape!=null?itemShape:cfg.backgroundShape(),previewColor);
        if(showHeld){
            var pose=g.pose();pose.pushMatrix();
            pose.translate(left,top);pose.scale(size/16f,size/16f);
            g.item(item,0,0);pose.popMatrix();
        }else g.blit(DIAMOND,left,top,left+size,top+size,0,1,0,1);
        int tx=left+size+8,ty=top+Math.max(0,(size-19)/2);
        g.text(font,"Preview",tx,ty,KcaTheme.PETAL,false);
        String caption=showHeld?item.getHoverName().getString():"Example diamond";
        if(size>=19)g.text(font,font.plainSubstrByWidth(caption,w-size-20),tx,ty+10,0xffede5e9,false);
    }
}
