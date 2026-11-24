package dev.purplepad;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class KcaTheme {
    public static final int ROSE=0xFFF499B8, PETAL=0xFFFFD6E3, MAUVE=0xFFCD91B4;
    public static final int INK=0xFF201620, EDGE=0xFF88536D, MUTED=0xFFC4A6B6;
    static final int DUNGEON_INK=0xFF171B30, DUNGEON_BLUE=0xFF99B9E6, DUNGEON_EDGE=0xFF53668D;
    public static final net.minecraft.resources.Identifier SETTINGS_ICON =
        net.minecraft.resources.Identifier.fromNamespaceAndPath("kca","textures/precursor_gear.png");
    private KcaTheme() {}

    public static void heading(GuiGraphicsExtractor g, Font font, String text, int cx, int y, int scale) {
        var pose=g.pose();
        pose.pushMatrix();
        pose.translate(cx,y);
        pose.scale(scale,scale);
        var label=Component.empty();
        if(text.equals("KCA")){
            label.append(Component.literal("K").withColor(0xEE83AC));
            label.append(Component.literal("C").withColor(0xFFAAC8));
            label.append(Component.literal("A").withColor(0xFFD6E3));
        }else label.append(Component.literal(text));
        g.centeredText(font,label.withStyle(style->style.withBold(true)),0,0,PETAL);
        pose.popMatrix();
    }

    public enum Branch { GENERAL, NUCLEUS, DUNGEONS, CHAT, KUUDRA }
    public static void backdrop(GuiGraphicsExtractor g,int w,int h,Branch branch){
        var cfg=com.nucleus.NucleusMod.CONFIG;
        int accent=switch(branch){
            case GENERAL -> ROSE;
            case CHAT -> PartyScreen.MINT;
            case KUUDRA -> KuudraScreen.ACCENT;
            case NUCLEUS -> cfg.nucleusAccent;
            case DUNGEONS -> cfg.dungeonAccent;
        };
        int base=switch(branch){
            case GENERAL -> 0xFF08070C;
            case CHAT -> 0xFF122825;
            case KUUDRA -> 0xFF421425;
            case NUCLEUS -> 0xFF2C2115;
            case DUNGEONS -> DUNGEON_INK;
        };
        com.nucleus.client.NucleusUi.backdrop(g,w,h,net.minecraft.client.Minecraft.getInstance().level!=null,base,accent);
    }
    public static com.nucleus.client.NucleusUi.NButton tab(String label,int x,int y,int width,boolean selected,int color,Runnable action){
        return com.nucleus.client.NucleusUi.NButton.create(Component.literal(label),b->action.run())
            .pos(x,y).size(width,18).accent(color).build().selected(selected);
    }
    public static int tint(int base,int accent,int percent){
        int a=100-percent;
        return 0xFF000000 | (((((base>>16)&255)*a+((accent>>16)&255)*percent)/100)<<16)
            | (((((base>>8)&255)*a+((accent>>8)&255)*percent)/100)<<8) | (((base&255)*a+(accent&255)*percent)/100);
    }
    public static void panel(GuiGraphicsExtractor g,int x,int y,int w,int h) {
        TimerSettingsScreen.card(g,x,y,w,h,EDGE,INK);
    }
    public static void button(GuiGraphicsExtractor g,int x,int y,int w,int h,boolean highlight,boolean enabled) {
        button(g,x,y,w,h,highlight,enabled,ROSE);
    }
    public static void button(GuiGraphicsExtractor g,int x,int y,int w,int h,boolean highlight,boolean enabled,int accent){
        button(g,x,y,w,h,highlight,enabled,accent,INK);
    }
    static void button(GuiGraphicsExtractor g,int x,int y,int w,int h,boolean highlight,boolean enabled,int accent,int ink){
        TimerSettingsScreen.card(g,x,y,w,h,enabled?(highlight?tint(accent,PETAL,25):tint(ink,accent,45)):EDGE,highlight?tint(ink,accent,17):tint(ink,accent,3));
    }
}
