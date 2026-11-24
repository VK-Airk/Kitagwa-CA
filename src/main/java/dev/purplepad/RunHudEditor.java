package dev.purplepad;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

final class RunHudEditor extends Screen {
    private final Screen parent;
    private float x=RunStats.positionX,y=RunStats.positionY;
    private double offsetX,offsetY;
    private boolean dragging;
    private float scale=RunStats.scale;
    private int toolbarTop;
    RunHudEditor(Screen parent){super(Component.literal("Move counter"));this.parent=parent;}
    static List<String> sample(){
        return RunStats.simple?List.of(RunStats.sampleLine(RunStats.messageTemplate,false,true)):RunStats.reportSample();
    }
    private static CounterPlacement box(int screenW,int screenH,Font font,List<String> lines,int textWidth,boolean simple,float x,float y,float scale){
        int w=textWidth+(simple?2:12);
        int h=lines.size()*(font.lineHeight+1)+(simple?0:8);
        return CounterPlacement.at(screenW,screenH,(int)Math.ceil(w*scale),(int)Math.ceil(h*scale),x,y);
    }
    static CounterPlacement draw(GuiGraphicsExtractor g,Font font,List<String> lines,int textWidth,boolean simple,float x,float y,float scale){
        var box=box(g.guiWidth(),g.guiHeight(),font,lines,textWidth,simple,x,y,scale);
        paint(g,font,lines,box.x(),box.y(),box.width(),box.height(),scale);
        return box;
    }
    static void paint(GuiGraphicsExtractor g,Font font,List<String> lines,int x,int y,int w,int h,float scale){
        boolean simple=RunStats.simple;
        RunStats.style().backdrop(g,x,y,x+w,y+h,com.nucleus.NucleusMod.CONFIG.dungeonAccent);
        var pose=g.pose();pose.pushMatrix();pose.translate(x,y);pose.scale(scale,scale);
        int lineY=simple?0:4;
        for(String line:lines){
            g.text(font,line,simple?0:6,lineY,KcaTheme.PETAL,true);
            lineY+=font.lineHeight+1;
        }
        pose.popMatrix();
    }
    private CounterPlacement bounds(){var l=sample();return box(width,height,font,l,l.stream().mapToInt(font::width).max().orElse(0),RunStats.simple,x,y,scale);}
    @Override protected void init(){
        int cx=width/2;
        var counter=bounds();
        toolbarTop=height-87;
        if(counter.y()+counter.height()>toolbarTop && counter.y()<toolbarTop+45 &&
            counter.x()<cx+130 && counter.x()+counter.width()>cx-130) toolbarTop=5;
        int row=toolbarTop+20;
        addRenderableWidget(new TimerSettingsScreen.PixelButton(cx-122,row,28,20,"-",KcaTheme.PETAL,b->adjust(-1)));
        addRenderableWidget(new TimerSettingsScreen.PixelButton(cx+94,row,28,20,"+",KcaTheme.PETAL,b->adjust(1)));
        addRenderableWidget(new TimerSettingsScreen.PixelButton(cx-84,row,80,20,"Cancel",KcaTheme.MUTED,b->onClose()));
        addRenderableWidget(new TimerSettingsScreen.PixelButton(cx+4,row,80,20,"Save",KcaTheme.ROSE,b->save()));
    }
    private void save(){RunStats.scale=scale;RunStats.positionX=x;RunStats.positionY=y;RunStats.saveOptions();onClose();}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){
        if(super.mouseClicked(e,twice))return true;
        var b=bounds();
        if(e.button()==0 && e.x()>=b.x() && e.x()<b.x()+b.width() && e.y()>=b.y() && e.y()<b.y()+b.height()){
            dragging=true;offsetX=e.x()-b.x();offsetY=e.y()-b.y();return true;
        }
        return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){
        if(!dragging)return super.mouseDragged(e,dx,dy);
        var b=bounds();
        x=CounterPlacement.fraction(e.x()-offsetX,b.travelX());
        y=CounterPlacement.fraction(e.y()-offsetY,b.travelY());
        return true;
    }
    private void adjust(double step){scale=Math.clamp(Math.round((scale+Math.signum(step)*0.1f)*10)/10f,0.5f,3f);}
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){
        var b=bounds();if(mx>=b.x()&&mx<b.x()+b.width()&&my>=b.y()&&my<b.y()+b.height()){
            adjust(dy);return true;}
        return super.mouseScrolled(mx,my,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent e){
        if(dragging){dragging=false;return true;}
        return super.mouseReleased(e);
    }
    @Override public boolean keyPressed(KeyEvent e){
        if(e.key()==257 || e.key()==335){save();return true;}
        return super.keyPressed(e);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float tick){g.fill(0,0,width,height,0x801B101B);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float tick){
        var l=sample();draw(g,font,l,l.stream().mapToInt(font::width).max().orElse(0),RunStats.simple,x,y,scale);
        int cx=width/2;
        KcaTheme.panel(g,cx-130,toolbarTop,260,45);
        g.centeredText(font,"Drag to move  ·  Scroll to resize  ·  "+Math.round(scale*100)+"%",cx,toolbarTop+6,KcaTheme.PETAL);
        super.extractRenderState(g,mx,my,tick);
    }
}
