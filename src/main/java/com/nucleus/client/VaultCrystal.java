package com.nucleus.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nucleus.client.mixin.GuiSceneAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Draw in screen coordinates so every flight starts beyond the actual viewport. */
enum VaultCrystal {
    // The launch and two control points are fractions of the viewport. The socket is the fourth point.
    AMETHYST(0xCB86FF, -.10f,.82f, .40f,1.04f, .34f,.34f),
    AMBER   (0xFFAB55, .16f,-.14f, -.10f,.63f, .59f,.65f),
    JADE    (0xA8F366, 1.10f,.20f, .61f,-.05f, .38f,.64f),
    SAPPHIRE(0x72C6FC, .78f,1.18f, 1.01f,.57f, .56f,.22f),
    TOPAZ   (0xFFDC65, -.12f,.38f, .19f,-.12f, .88f,.60f);

    final int rgb;
    final Identifier sprite;
    private final float startX,startY,bendX,bendY,approachX,approachY;

    VaultCrystal(int rgb,float startX,float startY,float bendX,float bendY,float approachX,float approachY) {
        this.rgb=rgb;
        this.startX=startX; this.startY=startY;
        this.bendX=bendX; this.bendY=bendY;
        this.approachX=approachX; this.approachY=approachY;
        String name = name().toLowerCase(java.util.Locale.ROOT);
        sprite = Identifier.fromNamespaceAndPath("kca", "textures/item/" + name + "_crystal.png");
    }

    void draw(GuiGraphicsExtractor g, int width, int height, float x, float y, float size,
              float flight, float turn, int alpha, boolean trial) {
        if (alpha <= 0) return;
        stamp(g, x, y + size * .05f, size, 0, alpha << 24 | net.minecraft.util.ARGB.srgbLerp(.19f,0xFF201A29,0xFF000000|rgb)&0xFFFFFF);
        if (flight <= 0) return;
        var config=com.nucleus.NucleusMod.CONFIG;
        float t=Math.min(1,flight);
        float tail=Math.max(0,flight-.50f);
        float px=x,py=y;
        if(t<1 || (config.vaultKeyTrails && tail<t)){
            var path=new Flight(this,width,height,x,y,trial,config.vaultKeyLoops && (trial || this==TOPAZ));
            if(config.vaultKeyTrails && tail<t){
                var ribbon=new Ribbon(path,width,height,size,t,tail,alpha,new Matrix3x2f(g.pose()));
                ((GuiSceneAccessor)g).kcaSceneState().addGuiElement(ribbon);
            }
            px=path.sample(t,true);
            py=path.sample(t,false);
        }
        stamp(g,px,py,size,(turn-1)*(float)Math.PI/2,alpha<<24|0xFFFFFF);
    }

    /** The key and its ribbon use the same curve. The loop meets both arcs at a horizontal tangent. */
    private record Flight(VaultCrystal key,int w,int h,float x,float y,boolean trial,boolean loop){
        float sample(float t,boolean horizontal){
            float start=horizontal ? w*key.startX : h*key.startY;
            float end=horizontal ? x : y;
            if(!loop)return position(t,start,horizontal?w*key.bendX:h*key.bendY,
                horizontal?w*key.approachX:h*key.approachY,end);
            float span=Math.min(w,h);
            float direction=(key==JADE || key==SAPPHIRE)?-1:1;
            float bottom=key==TOPAZ?h*.74f:.50f*y+.44f*h;
            float middle=key==TOPAZ?w*.50f:x*.52f+w*.24f;
            float gate=horizontal?middle:bottom;
            float radius=(trial?.21f:.15f)*span;
            if(t<.32f){
                return position(t/.32f,start,horizontal?start+.12f*w*direction:h*.40f,
                    horizontal?gate-radius*.75f*direction:gate,gate);
            }
            if(t<.76f){
                double angle=(t-.32f)/.44f*(2*Math.PI);
                return horizontal ? gate+direction*radius*(float)Math.sin(angle)
                    : gate-radius*(1-(float)Math.cos(angle));
            }
            return position((t-.76f)/.24f,gate,horizontal?gate+direction*radius:gate,
                horizontal?end:end+.15f*h,end);
        }
    }

    private static float position(float t,float a,float b,float c,float d){
        float u=1-t;
        return u*u*u*a + 3*u*u*t*b + 3*u*t*t*c + t*t*t*d;
    }

    /** Three continuous strips share their edges around the bend and taper to a single point. */
    private record Ribbon(Flight path,int width,int height,float size,
                          float head,float tail,int alpha,Matrix3x2f pose) implements GuiElementRenderState {
        public RenderPipeline pipeline(){return RenderPipelines.GUI;}
        public TextureSetup textureSetup(){return TextureSetup.noTexture();}
        public ScreenRectangle scissorArea(){return null;}
        public ScreenRectangle bounds(){return new ScreenRectangle(0,0,width,height);}
        public void buildVertices(VertexConsumer out){
            float prevX=0,prevY=0,prevNx=0,prevNy=0;
            float fade=Math.min(1,(head-tail)*8);
            int segments=path.loop?40:24;
            float px=path.sample(tail,true),py=path.sample(tail,false);
            prevX=px;prevY=py;
            for(int i=0;i<=segments;i++){
                float f=(float)i/segments;
                float nextT=tail+(head-tail)*Math.min(i+1,segments)/segments;
                float nextX=i==segments?px:path.sample(nextT,true);
                float nextY=i==segments?py:path.sample(nextT,false);
                float dx=nextX-prevX,dy=nextY-prevY;
                float radius=size*.52f*f*fade/(float)Math.max(.001,Math.hypot(dx,dy));
                float nx=-dy*radius,ny=dx*radius;
                if(i>0){
                    int opacity=Math.round(alpha*fade*f);
                    strip(out,prevX,prevY,prevNx,prevNy,px,py,nx,ny,1f,opacity/4<<24|path.key.rgb);
                    strip(out,prevX,prevY,prevNx,prevNy,px,py,nx,ny,.60f,opacity*3/4<<24|path.key.rgb);
                    strip(out,prevX,prevY,prevNx,prevNy,px,py,nx,ny,.16f,opacity<<24|0xFFF1DE);
                }
                prevX=px;prevY=py;prevNx=nx;prevNy=ny;
                px=nextX;py=nextY;
            }
        }
        private void strip(VertexConsumer out,float ax,float ay,float an,float am,
                           float bx,float by,float bn,float bm,float band,int color){
            out.addVertexWith2DPose(pose,ax-an*band,ay-am*band).setColor(color);
            out.addVertexWith2DPose(pose,ax+an*band,ay+am*band).setColor(color);
            out.addVertexWith2DPose(pose,bx+bn*band,by+bm*band).setColor(color);
            out.addVertexWith2DPose(pose,bx-bn*band,by-bm*band).setColor(color);
        }
    }

    private void stamp(GuiGraphicsExtractor g, float x, float y, float size, float rotation, int tint) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.rotate(rotation);
        pose.scale(size / 16, size / 16);
        g.blit(RenderPipelines.GUI_TEXTURED, sprite, -8, -8, 0, 0, 16, 16, 16, 16, 16, 16, tint);
        pose.popMatrix();
    }
}
