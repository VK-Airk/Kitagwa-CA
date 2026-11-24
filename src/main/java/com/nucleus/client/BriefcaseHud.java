package com.nucleus.client;

import com.nucleus.NucleusConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;

/** Two banks of three reels share one hinged lid. The matched item releases both catches. */
final class BriefcaseHud {
    private static final float[] STOPS={.34f,.52f,.73f,.43f,.62f,.82f};
    private static final Identifier[][] STRIPS=new Identifier[6][JackpotAnimation.DropType.values().length];
    private static final TextureAtlasSprite[][] ATLAS_STRIPS=new TextureAtlasSprite[STRIPS.length][STRIPS[0].length];
    private static final Identifier LEATHER=Identifier.withDefaultNamespace("textures/block/brown_terracotta.png");
    private static final Identifier FELT=Identifier.withDefaultNamespace("textures/block/white_wool.png");
    private static final int GOLD=0xFFE3B97B, EDGE=0xFF916744, INK=0xFF19171E;
    private static final int[] SIDES={-1,1};

    static void prepare(JackpotAnimation.DropType reward,String gem){
        var others=new ArrayList<>(List.of(JackpotAnimation.DropType.values()));
        // Each reel needs one entry for every drop, including Bundle.
        others.remove(reward);
        Identifier winner=JackpotHud.dropIcon(reward,gem);
        var atlas=Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.ITEMS);
        for(int bank=0;bank<STRIPS.length;bank++){
            var reel=STRIPS[bank];
            Collections.shuffle(others);
            reel[0]=winner;
            for(int i=1;i<reel.length;i++)reel[i]=JackpotHud.dropIcon(others.get(i-1),gem);
            for(int i=0;i<reel.length;i++){
                var drop=i==0?reward:others.get(i-1);
                ATLAS_STRIPS[bank][i]=switch(drop){
                    case BUNDLE,DIVANS_ALLOY,QUICK_CLAW,JADE_DYE -> null;
                    default -> atlas.getSprite(reel[i].withPath(path -> path.replace("textures/item/","item/mado/").replace(".png","")));
                };
            }
        }
    }
    static int locks(float p){
        int count=0;
        for(float stop:STOPS) if(p>=stop) count++;
        return count;
    }
    static final float RELEASE=.89f;
    private static final Identifier MAT=Identifier.fromNamespaceAndPath("kca","textures/casino_mat.png");
    static void backdrop(GuiGraphicsExtractor g,int w,int h){
        float ratio=(float)w/h,source=509f/360f;
        float u=Math.min(1,ratio/source)*.5f,v=Math.min(1,source/ratio)*.5f;
        g.blit(MAT,0,0,w,h,.5f-u,.5f+u,.5f-v,.5f+v);
        g.fillGradient(0,0,w,h,0x380C1018,0x68030308);
    }
    private static float ease(float p){p=Math.clamp(p,0f,1f);return p*p*(3-2*p);}
    private static void panel(GuiGraphicsExtractor g,int x,int y,int w,int h,int c){
        g.fill(x+2,y,x+w-2,y+h,c);g.fill(x,y+2,x+w,y+h-2,c);
    }
    // Repeat the native block texture in square tiles. Crop only the final tile at each edge.
    private static void lining(GuiGraphicsExtractor g,Identifier id,int x,int y,int w,int h,int tint){
        for(int dy=0;dy<h;dy+=16)for(int dx=0;dx<w;dx+=16){
            int tw=Math.min(16,w-dx),th=Math.min(16,h-dy);
            g.blit(RenderPipelines.GUI_TEXTURED,id,x+dx,y+dy,0,0,tw,th,tw,th,16,16,tint);
        }
    }
    static void draw(GuiGraphicsExtractor g,int w,int h,float p,boolean ending){
        boolean paired=JackpotAnimation.game==NucleusConfig.Game.Briefcase;
        float spin=ending?(paired?1:Math.min(1,p/.25f)):p;
        float open=ending?ease((p-(paired?.035f:.24f))/.29f)
            :JackpotAnimation.ending==NucleusConfig.Ending.Briefcase?0:ease((p-RELEASE)/.105f);
        if(!ending)backdrop(g,w,h);
        final int depth=86;
        float angle=open*2.58f;
        float projection=(float)Math.cos(angle);
        float rear=Math.min(0,depth*projection);
        // Pull back continuously as the lid opens, with one uniform scale for the whole case.
        float scale=Math.min(w/173f,h/(151f+57f*open));
        var pose=g.pose();
        pose.pushMatrix();
        try{
            pose.translate(w*.5f,h*.51f-scale*(rear+depth+8)*.5f);
            pose.scale(scale,scale);
            panel(g,-77,4,154,depth+5,0xAA08080E);
            panel(g,-76,0,152,depth,INK);
            panel(g,-74,2,148,depth-4,EDGE);
            lining(g,LEATHER,-72,4,144,depth-8,0xFF947366);
            panel(g,-69,6,138,72,0xFF23362F);
            lining(g,FELT,-67,8,134,68,0xFF45665A);
            for(int side:SIDES){
                int start=side<0?-64:20;
                for(int i=0;i<3;i++){
                    int x=start+i*15;
                    panel(g,x,34,13,16,0xFF658170);
                    panel(g,x+1,36,11,12,0xFF243C35);
                }
                int x=side*57;
                g.fill(x-4,78,x+4,85,GOLD);g.fill(x-2,79,x+2,83,EDGE);
            }
            panel(g,-17,26,34,34,0xFF607565);panel(g,-15,28,30,30,0xFF172B28);
            if(open>0 && (!ending || !JackpotAnimation.rewardShown()))
                drawIcon(g,0,0,-10,33,20,33,53);
            panel(g,-21,83,42,10,INK);panel(g,-16,85,32,4,0xFF735A4C);
            pose.pushMatrix();
            pose.scale(1,Math.max(.012f,Math.abs(projection)));
            if(projection<0)pose.translate(0,-depth);
            panel(g,-76,0,152,depth,INK);panel(g,-74,2,148,depth-4,EDGE);
            lining(g,LEATHER,-72,4,144,depth-8,0xFF997F75);
            if(projection>=0){
                for(int bank=0;bank<2;bank++){
                    int x=bank==0?-70:8;
                    panel(g,x,28,62,30,GOLD);panel(g,x+1,29,60,28,INK);
                    pose.pushMatrix();
                    pose.translate(x+2,32);
                    pose.scale(.58f,.58f);
                    for(int i=0;i<3;i++)reel(g,i*35,0,bank*3+i,spin);
                    pose.popMatrix();
                }
                int lift=Math.round(ease((spin-RELEASE)/.07f)*5);
                panel(g,-7,36-lift,14,22,INK);panel(g,-5,37-lift,10,16,GOLD);
                g.fill(-1,41-lift,1,47-lift,INK);
                for(int side:SIDES){
                    int x=side<0?-74:61;
                    g.fill(x,3,x+13,6,GOLD);g.fill(x,80,x+13,83,GOLD);
                }
            }else{
                panel(g,-69,6,138,74,INK);lining(g,FELT,-67,8,134,70,0xFF36564D);
                for(int y=18;y<72;y+=14)for(int x=-59;x<=53;x+=17){
                    int dx=x+(y%28==4?7:0);g.fill(dx,y,dx+2,y+1,0xFF80917A);
                }
                g.fill(-62,11,62,12,EDGE);g.fill(-62,74,62,75,EDGE);
            }
            pose.popMatrix();
            for(int side:SIDES){
                int x=side*41;g.fill(x-6,0,x+6,2,EDGE);g.fill(x-3,0,x+3,1,GOLD);
            }
        }finally{pose.popMatrix();}
    }

    private static void drawIcon(GuiGraphicsExtractor g,int reel,int index,int x,int top,int size,int clipTop,int clipBottom){
        float v0=(clipTop-top)/(float)size,v1=(clipBottom-top)/(float)size;
        TextureAtlasSprite sprite=ATLAS_STRIPS[reel][index];
        Identifier id=STRIPS[reel][index];
        float u0=0,u1=1;
        if(sprite!=null){
            id=sprite.atlasLocation();u0=sprite.getU0();u1=sprite.getU1();
            v0=sprite.getV(v0);v1=sprite.getV(v1);
        }
        g.blit(id,x,clipTop,x+size,clipBottom,u0,u1,v0,v1);
    }
    private static void reel(GuiGraphicsExtractor g,int x,int y,int reel,float spin){
        var icons=STRIPS[reel];
        float progress=Math.min(1,spin/STOPS[reel]);
        float distance=(1-progress)*(1-progress)*(10+reel*3);
        int whole=(int)distance;
        float fraction=distance-whole;
        g.fill(x,y,x+31,y+34,INK);
        g.fillGradient(x+1,y+1,x+30,y+33,0xFFBAAD96,0xFFE8DAAF);
        for(int row=-1;row<=2;row++){
            float center=y+17+(row-fraction)*28;
            int top=Math.round(center-9),bottom=top+18;
            int clippedTop=Math.max(y+1,top),clippedBottom=Math.min(y+33,bottom);
            if(clippedBottom<=clippedTop)continue;
            drawIcon(g,reel,Math.floorMod(whole+row,icons.length),x+6,top,18,clippedTop,clippedBottom);
        }
        g.fillGradient(x+1,y+1,x+30,y+8,0x990F1920,0x00121921);
        g.fillGradient(x+1,y+26,x+30,y+33,0x00121921,0xAA26202A);
        int line=spin>=STOPS[reel]?0xFFF9D587:0xFF954D50;
        g.fill(x,y+16,x+4,y+18,line);g.fill(x+27,y+16,x+31,y+18,line);
    }
}
