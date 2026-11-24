package com.nucleus.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nucleus.NucleusConfig;
import com.nucleus.client.mixin.GuiSceneAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/** A miniature block scene. All masonry, glass and treasure share a single atlas draw. */
final class CrystalVaultHud {
    private record Lock(int x,int y,VaultCrystal crystal) {}
    private static final Lock[] LOCKS={
        new Lock(-54,8,VaultCrystal.AMETHYST), new Lock(-41,-31,VaultCrystal.AMBER),
        new Lock(41,-31,VaultCrystal.JADE), new Lock(54,8,VaultCrystal.SAPPHIRE),
        new Lock(0,-47,VaultCrystal.TOPAZ)
    };
    private static final int FIXED=0, LEFT=-1, RIGHT=1;
    private record Block(float x,float y,float z,float w,float h,float d,Identifier texture,int color,int lamp,int shutter,Sample sample) {}
    // Dimensions are in the same units as the surrounding eleven-unit ore blocks.
    // UV bounds omit transparent margins so the visible edge meets the supporting surface.
    private enum Sample {
        FRAGMENT("kca:block/vault/divan_fragment",2,2,14,14,5,2),
        JADE("kca:block/vault/jaderald",1,0,15,16,5.5f,1.5f),
        MIXTURE("kca:block/vault/gemstone_mixture",2,2,15,15,5,2.5f),
        RUBY("kca:block/vault/flawless_ruby_gem",2,2,15,13,5,2),
        PEARL("kca:block/vault/precious_pearl",1,1,15,15,5,2.5f),
        LANTERN("kca:block/vault/lantern",5,1,11,14,3.5f,7.5f);
        final Identifier texture;
        final int left,top,right,bottom;
        final float width,rise;
        Sample(String id,int left,int top,int right,int bottom,float width,float rise){
            texture=Identifier.parse(id);
            this.left=left;this.top=top;this.right=right;this.bottom=bottom;
            this.width=width;this.rise=rise;
        }
    }
    private static final class Builder {
        final List<Block> blocks=new ArrayList<>();
        void box(float x,float y,float z,float w,float h,float d,String texture,int color){
            add(x,y,z,w,h,d,texture,color,-1,FIXED);
        }
        void rest(Sample sample){
            Block b=blocks.removeLast();
            blocks.add(new Block(b.x,b.y,b.z,b.w,b.h,b.d,b.texture,b.color,b.lamp,b.shutter,sample));
        }
        void add(float x,float y,float z,float w,float h,float d,String name,int color,int lamp,int shutter){
            blocks.add(new Block(x,y,z,w,h,d,Identifier.withDefaultNamespace("block/"+name),color,lamp,shutter,null));
        }
    }
    private static Block[] buildScene() {
        var b=new Builder();
        // Vault floor, rear masonry and ribs run back almost twice the width of the doorway.
        for(int col=-5;col<5;col++){
            for(int row=-4;row<4;row++)
                b.box(col*13,row*13,104,13,13,4,(row+col)%4==0?"cracked_stone_bricks":"stone_bricks",0xFF696074);
            for(int depth=0;depth<8;depth++){
                b.box(col*13,41,depth*13,13,4,13,(depth+col)%4==0?"chiseled_stone_bricks":"smooth_stone",0xFFC3AE96);
                b.box(col*13,-56,13+depth*13,13,4,13,"stone_bricks",0xFF5C5869);
            }
        }
        for(int side:new int[]{-1,1}){
            for(int depth=0;depth<9;depth++){
                for(int row=-4;row<3;row++)
                    b.box(side<0?-64:54,row*14,depth*12,10,14,12,"stone_bricks",0xFF897B85);
                if(depth%3==1){
                    b.box(side<0?-55:48,-47,depth*12,7,89,8,"polished_basalt_side",0xFF9A9093);
                    b.box(side<0?-49:45,-18,depth*12,4,15,5,"shroomlight",0xFFFFD49A);
                }
            }
        }
        // Three staggered banks of loot. The front gap leaves a sightline to the rear plinth.
        for(int rank=0;rank<3;rank++){
            for(int col=0;col<9;col++){
                int z=73-rank*20, x=(col-4)*11+rank%2*3;
                int count=switch(rank){
                    case 0 -> new int[]{3,5,4,2,4,3,5,4,3}[col];
                    case 1 -> new int[]{3,4,2,1,2,1,3,4,3}[col];
                    default -> new int[]{2,2,1,0,0,0,1,2,2}[col];
                };
                for(int level=0;level<count;level++){
                    boolean ore=(level+col+rank*3)%5==0;
                    b.box(x,30-11*level,z,11,11,11,ore?"gold_ore":"gold_block",
                        rank==0?0xFFECC278:0xFFFFE39B);
                    if(level==count-1){
                        if(rank==0 && col==4)b.rest(Sample.JADE);
                        if(rank==0 && col==7)b.rest(Sample.PEARL);
                        if(rank==1 && col==2)b.rest(Sample.MIXTURE);
                    }
                }
            }
        }
        // A broken ore face backs each buried pick. The near slabs cover the tips of the heads.
        for(int side:new int[]{-1,1}){
            int x=side<0?-46:24;
            b.box(x,25,23,22,16,13,"deepslate_gold_ore",0xFFFFD096);
            b.box(x+3,19,26,12,6,10,"raw_gold_block",0xFFFFE0A1);
            if(side>0)b.rest(Sample.FRAGMENT);
            b.box(x+4,30,14,12,11,10,"deepslate_iron_ore",0xFFE9C49A);
        }
        // A short ore-cart siding runs beside the right bank. The track sits on oak sleepers.
        for(int z=23;z<=47;z+=8)b.box(15,39,z,19,2,3,"dark_oak_planks",0xFFE0B995);
        for(int x:new int[]{18,29})b.box(x,38,23,2,1,27,"iron_block",0xFFB9A7A2);
        b.box(16,32,30,18,6,10,"iron_block",0xFF857D82);
        b.box(17,29,31,16,4,8,"deepslate_diamond_ore",0xFFE6F4E8);
        b.rest(Sample.RUBY);
        for(int x:new int[]{18,29})b.box(x,36,28,3,3,4,"polished_blackstone",0xFFAAA3A5);
        b.box(-45,16,52,10,14,10,"barrel_side",0xFFDEC296);
        b.box(-45,15,52,10,2,10,"barrel_top",0xFFF1D6A4);
        b.rest(Sample.LANTERN);
        // The inner arch sits well behind the stone surround. Its soffit is visible through the glass.
        for(int row=0;row<8;row++){
            int y=32-row*12,half=halfWidth(row);
            for(int side:new int[]{-1,1}){
                for(int ring=0;ring<2;ring++){
                    int x=side<0?-half-12-ring*12:half+ring*12;
                    b.box(x,y,-4+ring*5,12,12,18,ring==0?"polished_deepslate":"stone_bricks",
                        ring==0?0xFFC0B8B5:0xFFCEBFB7);
                }
                if(half>0){
                    int panes=half/12;
                    for(int k=0;k<panes;k++){
                        int x=side<0?-12*(k+1):k*12;
                        b.add(x,y,7,12,12,1,"light_gray_stained_glass",0xBEFFFFFF,-1,side);
                    }
                }
            }
        }
        // The courses behind the arch continue out to the edges of the viewport.
        for(int row=-8;row<=8;row++){
            int half=(row < -4 || row > 2) ? 0 : halfWidth(2-row);
            for(int side:new int[]{-1,1})for(int col=0;col<15;col++){
                int edge=half+col*12;
                int x=side<0?-edge-12:edge;
                int y=row*12+8;
                String tex=(row+col)%5==0?"cracked_stone_bricks":"stone_bricks";
                b.box(x,y,9,12,12,12,tex,0xFFA79DA4);
            }
        }
        // Stagger the crystal tips around each jamb. Each solid core and glass shell share a foot.
        for(int side:new int[]{-1,1}){
            int lower=side<0?0:3, upper=side<0?1:2;
            String glass=side<0?"purple_stained_glass":"light_blue_stained_glass";
            spire(b,side*64,34,2,17,47,lower,glass);
            spire(b,side*73,21,-1,18,57,lower,glass);
            spire(b,side*83,30,3,19,45,lower,glass);
            spire(b,side*89,15,1,13,33,lower,glass);
            String shoulder=side<0?"orange_stained_glass":"lime_stained_glass";
            spire(b,side*50,-21,-7,15,24,upper,shoulder);
            spire(b,side*57,-20,-6,12,18,upper,shoulder);
            // Shorter crystals follow the arch toward a continuous stone crown.
            spire(b,side*21,-38,-6,13,16,4,"yellow_stained_glass");
            spire(b,side*13,-40,-7,11,14,4,"yellow_stained_glass");
            b.box(side<0?-24:9,-51,-5,15,7,8,"polished_deepslate",0xFFCCC0B4);
        }
        b.box(-9,-54,-6,18,13,9,"chiseled_stone_bricks",0xFFD5C8B2);
        b.blocks.sort(Comparator.comparingDouble(Block::z).reversed()
            .thenComparingDouble(block->-Math.abs(block.x+block.w/2))
            .thenComparingDouble(block->-Math.abs(block.y+block.h/2+17)));
        return b.blocks.toArray(Block[]::new);
    }
    private static void spire(Builder b,int x,int foot,int z,int width,int height,int key,String glass){
        int left=x-width/2,top=foot-height;
        b.add(left+2,top+2,z+1,width-4,height-2,width-4,"calcite",
            0xFF000000|LOCKS[key].crystal.rgb,key,FIXED);
        b.add(left,top,z,width,height,width,glass,0xCDFFFFFF,key,FIXED);
        b.add(left+2,top-3,z+1,width-4,3,width-3,glass,0xE0FFFFFF,key,FIXED);
    }
    private static final Block[] SCENE=buildScene();
    private static final int FRONT_START=frontStart();
    private static int frontStart(){
        int i=0;
        while(i<SCENE.length && SCENE[i].z>=18)i++;
        return i;
    }
    // Sprite objects are replaced when the atlas is stitched again, including on pack reload.
    private record Materials(TextureAtlasSprite first,TextureAtlasSprite[] blocks,TextureAtlasSprite[] samples){}
    private static Materials materials;
    private static Materials materials(TextureAtlas atlas){
        var first=atlas.getSprite(SCENE[0].texture);
        if(materials==null || materials.first!=first){
            var blocks=new TextureAtlasSprite[SCENE.length];
            for(int i=0;i<blocks.length;i++)blocks[i]=atlas.getSprite(SCENE[i].texture);
            var samples=new TextureAtlasSprite[Sample.values().length];
            for(var sample:Sample.values())samples[sample.ordinal()]=atlas.getSprite(sample.texture);
            materials=new Materials(first,blocks,samples);
        }
        return materials;
    }
    private static final int[][] STONE_LIGHT=bakeLight();
    private static int[][] bakeLight(){
        int[][] palette=new int[LOCKS.length+1][SCENE.length];
        for(int i=0;i<SCENE.length;i++){
            Block b=SCENE[i];
            float red=.43f,green=.40f,blue=.49f;
            for(int step=0;step<=LOCKS.length;step++){
                if(step>0){
                    Lock key=LOCKS[step-1];
                    float dx=b.x+b.w*.5f-key.x,dy=b.y+b.h*.5f-key.y,dz=(b.z+b.d*.5f+9)*.57f;
                    float weight=.92f/(1+(dx*dx+dy*dy+dz*dz)/700f);
                    red+=weight*((key.crystal.rgb>>16)&255)/255f;
                    green+=weight*((key.crystal.rgb>>8)&255)/255f;
                    blue+=weight*(key.crystal.rgb&255)/255f;
                }
                int c=b.color;
                int r=Math.min(255,Math.round(((c>>16)&255)*red));
                int g=Math.min(255,Math.round(((c>>8)&255)*green));
                int bl=Math.min(255,Math.round((c&255)*blue));
                palette[step][i]=c&0xFF000000|r<<16|g<<8|bl;
            }
        }
        return palette;
    }

    private static int halfWidth(int row){ return switch(row){case 7->0;case 6->24;case 5->36;default->48;}; }
    private static float ease(float t){t=Math.clamp(t,0f,1f);return t*t*(3-2*t);}
    private record View(float cx,float cy,float zoom,float focal,float advance,float eyeX,float eyeY){
        float factor(float z){return zoom*focal/(focal+z-advance);}
        float x(float x,float z){return cx+zoom*eyeX+(x-eyeX)*factor(z);}
        float y(float y,float z){return cy+zoom*eyeY+(y-eyeY)*factor(z);}
    }
    private static int shade(int argb,float f){
        int r=(int)(((argb>>16)&255)*f),g=(int)(((argb>>8)&255)*f),b=(int)((argb&255)*f);
        return argb&0xFF000000 | r<<16 | g<<8 | b;
    }

    private record Scene(View view,float open,float keys,Materials materials,TextureSetup textureSetup,
                         ScreenRectangle bounds,Matrix3x2f pose,boolean behindTools) implements GuiElementRenderState {
        public RenderPipeline pipeline(){return RenderPipelines.GUI_TEXTURED;}
        public ScreenRectangle scissorArea(){return null;}
        public void buildVertices(VertexConsumer out){
            int lit=locks(keys);
            float fade=lit==0?0:ease((keys-KEY_SEATED-(lit-1)*KEY_STEP)/.07f);
            int start=behindTools?0:FRONT_START, end=behindTools?FRONT_START:SCENE.length;
            float originX=view.cx+view.zoom*view.eyeX,originY=view.cy+view.zoom*view.eyeY;
            for(int i=start;i<end;i++){
                Block b=SCENE[i];
                float x=b.x;
                float w=b.w;
                if(b.shutter!=FIXED){
                    // Slide into a recess behind each jamb; trim panes as they leave the aperture.
                    int half=halfWidth(Math.round((32-b.y)/12));
                    x+=b.shutter*open*51;
                    float right=Math.min(half,x+w);
                    x=Math.max(-half,x);w=right-x;if(w<=0)continue;
                }
                float near=view.factor(b.z),far=view.factor(b.z+b.d);
                float dx=x-view.eyeX,dy=b.y-view.eyeY;
                float l=originX+dx*near,r=l+w*near,t=originY+dy*near,d=t+b.h*near;
                float fl=originX+dx*far,fr=fl+w*far,ft=originY+dy*far,fd=ft+b.h*far;
                if(Math.max(r,fr)<0 || Math.min(l,fl)>bounds.width() || Math.max(d,fd)<0 || Math.min(t,ft)>bounds.height())continue;
                int color=STONE_LIGHT[lit][i];
                if(b.lamp>=0)color=shade(b.color,.18f+.82f*ease((keys-KEY_SEATED-b.lamp*KEY_STEP)/.07f));
                else if(lit>0 && fade<1)color=net.minecraft.util.ARGB.srgbLerp(fade,STONE_LIGHT[lit-1][i],color);
                var tex=materials.blocks[i];
                float u=tex.getU0(),v=tex.getV0(),u1=tex.getU1(),v1=tex.getV1();
                if(b.y>view.eyeY)quad(out,fl,ft,l,t,r,t,fr,ft,u,v,u1,v1,color);
                if(b.y+b.h<view.eyeY)quad(out,l,d,fl,fd,fr,fd,r,d,u,v,u1,v1,shade(color,.56f));
                if(x>view.eyeX)quad(out,fl,ft,fl,fd,l,d,l,t,u,v,u1,v1,shade(color,.75f));
                if(x+w<view.eyeX)quad(out,r,t,r,d,fr,fd,fr,ft,u,v,u1,v1,shade(color,.69f));
                quad(out,l,t,l,d,r,d,r,t,u,v,u1,v1,shade(color,.93f));
                if(b.sample!=null)drawSample(out,b,color);
            }
        }
        private void drawSample(VertexConsumer out,Block b,int light){
            Sample item=b.sample;
            var sprite=materials.samples[item.ordinal()];
            float du=(sprite.getU1()-sprite.getU0())/16, dv=(sprite.getV1()-sprite.getV0())/16;
            float u=sprite.getU0()+du*item.left,v=sprite.getV0()+dv*item.top;
            float u1=sprite.getU0()+du*item.right,v1=sprite.getV0()+dv*item.bottom;
            float x=b.x+(b.w-item.width)*.5f;
            float depth=item==Sample.LANTERN?0:item.width*(item.bottom-item.top)/(item.right-item.left)*.87f;
            float front=b.z+(b.d-depth)*.5f,back=front+depth;
            float l=view.x(x,front),r=view.x(x+item.width,front),fl=view.x(x,back),fr=view.x(x+item.width,back);
            float base=view.y(b.y,front),floor=view.y(b.y,back),top=view.y(b.y-item.rise,back);
            float foot=view.y(b.y-.5f,front);
            // A shallow lower edge touches the block. The upper face recedes across its top.
            quad(out,fl,floor,l,base,r,base,fr,floor,u,v,u1,v1,0xA0382A24);
            quad(out,fl,top+.5f,l,base,r,base,fr,top+.5f,u,v,u1,v1,0xFF63554B);
            quad(out,fl,top,l,foot,r,foot,fr,top,u,v,u1,v1,net.minecraft.util.ARGB.srgbLerp(.65f,light,0xFFFFFFFF));
        }
        private void quad(VertexConsumer out,float ax,float ay,float bx,float by,float cx,float cy,
                          float dx,float dy,float u,float v,float u1,float v1,int color){
            out.addVertexWith2DPose(pose,ax,ay).setUv(u,v).setColor(color);
            out.addVertexWith2DPose(pose,bx,by).setUv(u,v1).setColor(color);
            out.addVertexWith2DPose(pose,cx,cy).setUv(u1,v1).setColor(color);
            out.addVertexWith2DPose(pose,dx,dy).setUv(u1,v).setColor(color);
        }
    }

    static void draw(GuiGraphicsExtractor g,int w,int h,float p,boolean ending){
        boolean fromVault=JackpotAnimation.game==NucleusConfig.Game.CrystalVault;
        boolean toVault=JackpotAnimation.ending==NucleusConfig.Ending.CrystalVault;
        float keys=ending?endingKeys(p,fromVault):p;
        float open=ending?ease((p-endingGate(fromVault))/.30f):toVault?0:ease((p-GATE_START)/(1-GATE_START));
        JackpotAnimation.crystalCues(Minecraft.getInstance(),keys,ending,open>0);
        // Frame the arch itself, with headroom for the crown and clearance for both outside keys.
        float zoom=Math.min(w/126f,h/100f);
        var view=new View(w*.50f,h*.585f,zoom*(1+.07f*open),143,open*16,0,-16);
        if(!ending)backdrop(g,w,h);
        var atlas=Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        var tiles=materials(atlas);
        var setup=TextureSetup.singleTexture(atlas.getTextureView(),atlas.getSampler());
        var state=((GuiSceneAccessor)g).kcaSceneState();
        var bounds=new ScreenRectangle(0,0,w,h);
        var matrix=new Matrix3x2f(g.pose());
        state.addGuiElement(new Scene(view,open,keys,tiles,setup,bounds,matrix,true));
        tool(g,view,-32,25,2.92f,DIAMOND_PICK);
        state.addGuiElement(new Scene(view,open,keys,tiles,setup,bounds,matrix,false));
        for(int i=0;i<LOCKS.length;i++){
            Lock lock=LOCKS[i];
            float t=keys-i*KEY_STEP;
            float factor=view.factor(-7);
            int alpha=Math.round(255*(1-ease((open-.1f)/.8f)));
            lock.crystal.draw(g,w,h,view.x(lock.x,-7),view.y(lock.y,-7),13*factor,
                t/FLIGHT_TIME,ease((t-FLIGHT_TIME)/TURN_TIME),alpha,false);
        }
        if(ending){
            g.fillGradient(0,0,w,h/3,0x950E0B16,0x000E0B16);
            g.fillGradient(0,h/2,w,h,0x00120E19,0xCB120E19);
        }
    }
    static void backdrop(GuiGraphicsExtractor g,int w,int h){
        g.fillGradient(0,0,w,h,0xFF0D0C16,0xFF211C29);
    }
    private static final Identifier DIAMOND_PICK=Identifier.withDefaultNamespace("textures/item/diamond_pickaxe.png");
    private static void tool(GuiGraphicsExtractor g,View view,int x,int y,float angle,Identifier pick){
        var pose=g.pose();pose.pushMatrix();
        pose.translate(view.x(x,17),view.y(y,17));pose.scale(view.factor(17),view.factor(17));pose.rotate(angle);
        g.blit(RenderPipelines.GUI_TEXTURED,pick,-8,-8,0,0,17,17,16,16,16,16,0xFF342632);
        g.blit(RenderPipelines.GUI_TEXTURED,pick,-9,-9,0,0,17,17,16,16,16,16,0xFFFFE7C8);
        pose.popMatrix();
    }
    private static final float KEY_STEP=.125f, FLIGHT_TIME=.265f, TURN_TIME=.105f, KEY_SEATED=FLIGHT_TIME+TURN_TIME;
    static float endingKeys(float p,boolean fromVault){return fromVault?1:Math.min(1,p/.23f);}
    static float fullyOpen(boolean fromVault){return endingGate(fromVault)+.30f;}
    static float endingGate(boolean fromVault){return fromVault?.035f:.21f;}
    static final float GATE_START=KEY_STEP*4+KEY_SEATED+.025f;
    static int locks(float p){
        int count=0;
        while(count<LOCKS.length && p>=KEY_SEATED+count*KEY_STEP)count++;
        return count;
    }
}
