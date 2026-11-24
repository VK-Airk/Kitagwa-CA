package dev.purplepad;

/** Saved positions are fractions of the available space after accounting for the counter's size. */
record CounterPlacement(int x,int y,int width,int height,int travelX,int travelY) {
    static CounterPlacement at(int screenW,int screenH,int w,int h,float x,float y){
        int dx=Math.max(0,screenW-w-8),dy=Math.max(0,screenH-h-8);
        return new CounterPlacement(4+Math.round(dx*x),4+Math.round(dy*y),w,h,dx,dy);
    }
    static float fraction(double origin,int travel){
        return (float)Math.clamp((origin-4)/Math.max(1,travel),0,1);
    }
}
