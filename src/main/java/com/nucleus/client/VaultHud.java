package com.nucleus.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
/** Five crystal tumblers seat in sequence before the vault releases the bundle. */
final class VaultHud {
    private static final Identifier FRAME = texture("block/chiseled_tuff_bricks");
    private static final Identifier CLOSED = texture("block/vault_front_off");
    private static final Identifier LIT = texture("block/vault_front_on");
    private static final Identifier PINK_CLOSED=Identifier.fromNamespaceAndPath("kca","textures/block/pink_vault_front_off.png");
    private static final Identifier PINK_LIT=Identifier.fromNamespaceAndPath("kca","textures/block/pink_vault_front_on.png");
    private static final Identifier TOP=texture("block/vault_top"), TOP_OPEN=texture("block/vault_top_ejecting");
    private static final Identifier PINK_EJECTING=Identifier.fromNamespaceAndPath("kca","textures/block/pink_vault_front_ejecting.png");
    private static final Identifier SIDE_ON=texture("block/vault_side_on"),SIDE_OFF=texture("block/vault_side_off");
    private static final org.joml.Matrix3x2f SIDE_FACE=new org.joml.Matrix3x2f(.14f,-.21f,0,1,64,-64);
    private static final Identifier EJECTING=texture("block/vault_front_ejecting");
    // Oblique view of a 128-pixel square top face, with its front edge on top of the door.
    private static final org.joml.Matrix3x2f TOP_FACE=new org.joml.Matrix3x2f(1,0,-.14f,.21f,-46.08f,-90.88f);
    private static Identifier reward;
    private static Identifier[] drops={};
    private static long bundle;
    static void prepare(JackpotAnimation.DropType drop,String gem,long start){
        reward=JackpotHud.dropIcon(drop,gem);
        bundle=JackpotDetector.bundleForReward(start);
        drops=new Identifier[]{reward};
    }
    static void collectDrops(boolean preview){
        var icons=new java.util.LinkedHashSet<Identifier>();
        if(preview){
            icons.add(JackpotHud.dropIcon(JackpotAnimation.DropType.DIVAN_FRAGMENT,""));
            icons.add(JackpotHud.dropIcon(JackpotAnimation.DropType.GEMSTONE_MIXTURE,""));
        } else {
            for(String line:JackpotDetector.lootForVault(bundle)){
                String name=line.toLowerCase(java.util.Locale.ROOT).replace("'","").replace("’","");
                for(var drop:JackpotAnimation.DropType.values()){
                    if(drop==JackpotAnimation.DropType.FLAWLESS_GEMSTONE)continue;
                    if(!name.contains(drop.displayName.toLowerCase(java.util.Locale.ROOT).replace("'","")))continue;
                    var icon=JackpotHud.dropIcon(drop,"");
                    if(!icon.equals(reward))icons.add(icon);
                }
                if(icons.size()>=2)break;
            }
        }
        icons.remove(reward);
        icons.add(reward);
        drops=icons.toArray(Identifier[]::new);
    }
    static float finalFlight(float progress){
        float start=LAUNCH+(drops.length-1)*.15f;
        return Math.clamp((progress-start)/(1-start),0f,1f);
    }
    static float scale(int w,int h){return Math.min(w/185f,h/209f);}
    static int launched(float progress){
        if(progress<LAUNCH)return 0;
        return Math.min(drops.length,1+(int)((progress-LAUNCH)/.15f));
    }
    static final float LAUNCH=.20f;
    private static final VaultCrystal[] KEYS={VaultCrystal.AMETHYST,VaultCrystal.AMBER,VaultCrystal.JADE,
        VaultCrystal.SAPPHIRE,VaultCrystal.TOPAZ};
    private static final int[] LOCK_X={-61,-43,43,61,0}, LOCK_Y={8,-43,-43,8,-61};

    private static float shake(float since){
        if(since<0 || since>=.048f)return 0;
        float t=since/.048f;
        return (float)Math.sin(3*Math.PI*t)*(1-t)*(1-t);
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath("minecraft","textures/"+name+".png");
    }
    private static float ease(float x) {
        float t=Math.clamp(x,0f,1f);
        return t*t*(3f-2f*t);
    }
    private static final float STEP=.125f, FLIGHT=.25f, TURN=.065f;
    static int locks(float progress) {
        return Math.clamp(1+(int)Math.floor((progress-FLIGHT-TURN)/STEP),0,5);
    }
    static void draw(GuiGraphicsExtractor g,int width,int height,float progress) {
        JackpotAnimation.trialKeyCues(net.minecraft.client.Minecraft.getInstance(),progress);
        float enter=ease(progress/.075f);
        drawVault(g,width,height,progress,enter,-1);
    }

    static void eject(GuiGraphicsExtractor g,int w,int h,float progress){
        JackpotAnimation.trialEjectionCues(net.minecraft.client.Minecraft.getInstance(),progress);
        drawVault(g,w,h,1,1,progress);
    }

    private static void drawVault(GuiGraphicsExtractor g,int w,int h,float keys,float enter,float eject){
        boolean opening=eject>=0;
        int locked=locks(keys);
        float impact=opening ? 2*shake(eject-.055f) :
            locked==0?0:shake(keys-(FLIGHT+TURN+(locked-1)*STEP))*(locked==5?3:1.2f);
        float scale=scale(w,h)*(.90f+.10f*enter);
        float cx=w*.5f+impact*scale,cy=h*.61f+((1-enter)*12+impact*.5f)*scale;
        backdrop(g,w,h,enter);
        boolean cool=com.nucleus.NucleusMod.CONFIG.coolTrialAccent;
        int accent=cool?0xFFC4D9FC:0xFFF6B1CE;
        var pose=g.pose();
        pose.pushMatrix();
        pose.translate(cx,cy);pose.scale(scale,scale);
        // The vanilla ejecting texture exposes the opening in the lid. The block stays together.
        pose.pushMatrix();pose.mul(SIDE_FACE);
        stamp(g,locked==5?SIDE_ON:SIDE_OFF,0,0,128,cool?0xFF8799B8:0xFFAA8699);
        pose.popMatrix();
        pose.pushMatrix();pose.mul(TOP_FACE);
        stamp(g,opening?TOP_OPEN:TOP,0,0,128,accent);
        pose.popMatrix();
        stamp(g,FRAME,-64,-64,128,accent);
        Identifier front=opening?(cool?EJECTING:PINK_EJECTING):locked==5?(cool?LIT:PINK_LIT):(cool?CLOSED:PINK_CLOSED);
        stamp(g,front,-54,-54,108,0xFFFFFFFF);
        for(int i=0;i<KEYS.length;i++)
            g.fill(-28+i*12,59,-20+i*12,61,i<locked?0xFF000000|KEYS[i].rgb:0xFF493A4C);
        pose.popMatrix();
        for(int i=0;i<KEYS.length;i++){
            float time=keys-i*STEP;
            KEYS[i].draw(g,w,h,cx+LOCK_X[i]*scale,cy+LOCK_Y[i]*scale,15*scale,
                time/FLIGHT,ease((time-FLIGHT)/TURN),255,true);
        }
        if(opening && eject>=LAUNCH){
            float hatchX=cx+9*scale,hatchY=cy-77.5f*scale;
            g.enableScissor(0,0,w,Math.round(hatchY+scale));
            // The last drop is the actual 3D celebration item, rendered by ItemPopup.
            for(int i=0;i<Math.min(launched(eject),drops.length-1);i++){
                float start=LAUNCH+i*.15f;
                float t=Math.clamp((eject-start)/.58f,0f,1f);
                if(t==1)continue;
                float size=scale*(14+7*ease(t));
                float rise=Math.min(hatchY-size*.60f,34*scale);
                float y=hatchY+size*.5f-rise*4*t*(1-t);
                float kick=i%2==0?-34:29;
                pose.pushMatrix();
                pose.translate(hatchX+scale*kick*t,y);pose.rotate(-.27f+.64f*t);
                int side=Math.round(size);
                stamp(g,drops[i],-side/2,-side/2,side,0xFFFFFFFF);
                pose.popMatrix();
            }
            g.disableScissor();
        }
    }

    static void backdrop(GuiGraphicsExtractor g,int w,int h,float amount){
        g.fillGradient(0,0,w,h,Math.round(226*amount)<<24|0x100E18,Math.round(239*amount)<<24|0x241524);
    }

    static void alloyBurst(GuiGraphicsExtractor g,int w,int h,float p,float fade){
        float t=Math.clamp((p-.25f)/.65f,0f,1f);
        if(t<=0 || t>=1)return;
        int cx=w/2,cy=(int)(h*.45f);
        float reach=Math.min(w,h)*(.13f+.28f*ease(t));
        float light=(float)Math.sin(Math.PI*t)*fade;
        JackpotHud.drawRays(g,cx,cy,20,0xFFD5A6,.28f*light,.3+t*.9,reach*2);
        for(int i=0;i<10;i++){
            double angle=i*Math.PI/5+t*.45;
            int x=cx+Math.round((float)Math.cos(angle)*reach);
            int y=cy+Math.round((float)Math.sin(angle)*reach*.57f);
            int arm=Math.max(1,(int)(Math.min(w,h)*.014f*light));
            int tint=((int)(210*light)<<24)|(i%2==0?0xFFE2A8:0xFFB89C);
            g.fill(x-arm,y,x+arm+1,y+1,tint);g.fill(x,y-arm,x+1,y+arm+1,tint);
        }
    }
    private static void stamp(GuiGraphicsExtractor g,Identifier texture,int x,int y,int size,int tint) {
        g.blit(RenderPipelines.GUI_TEXTURED,texture,x,y,0,0,size,size,16,16,16,16,tint);
    }
}
