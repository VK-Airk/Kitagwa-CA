package dev.purplepad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

enum FanArt {
    SCHOOL("marin_credits",1545,1545,"Art by Meowfeis"), KUUDRA("kuudra",1280,1280,"Art by LuziusL474"),
    HELMET("marin_helmet",2000,2925,"Art by Piniacotta (@piniacottaalt)"), PORTRAIT("marin_portrait",406,754,"Artist unconfirmed"),
    MARIN_SUNGLASSES("marin_sunglasses",1920,1383,"Art by Midnite Blue Draws"),
    MARIN_BENCH("marin_bench",375,533,"Art by Arden"),
    MARIN_SALUTE("marin_salute",1440,1800,"Art by Takko (@_takk00)"),
    MARIN_LINE_ART("marin_line_art",1440,1800,"Art by Takko (@_takk00)"),
    UNIFORM_PORTRAIT("uniform_portrait",387,516,"Art by @shirrori");

    private static final List<FanArt> rotation = new ArrayList<>(java.util.EnumSet.complementOf(java.util.EnumSet.of(KUUDRA)));
    private static int index=rotation.size();
    private static FanArt previous;
    final Identifier texture;
    final int width,height;
    final String credit;
    FanArt(String file,int width,int height,String credit){
        this.credit=credit;
        texture=Identifier.fromNamespaceAndPath("kca","textures/"+file+".png");
        this.width=width;this.height=height;
    }
    static FanArt next(){
        if(index==rotation.size()){
            Collections.shuffle(rotation);
            if(rotation.getFirst()==previous)Collections.swap(rotation,0,1);
            index=0;
        }
        previous=rotation.get(index++);
        return previous;
    }
    void draw(GuiGraphicsExtractor g,int x,int y,int availableWidth,int availableHeight){
        float scale=Math.min(availableWidth/(float)width,availableHeight/(float)height);
        float nativeScale=1f/Math.max(1,net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
        scale=Math.min(scale,nativeScale);
        int w=Math.round(scale*width),h=Math.round(scale*height);
        int left=x+(availableWidth-w)/2,top=y+(availableHeight-h)/2;
        g.blit(texture,left,top,left+w,top+h,0,1,0,1);
    }
}
