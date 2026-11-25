package com.nucleus.client;

import com.nucleus.NucleusMod;
import com.nucleus.NucleusConfig;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;

final class HollowsMap {
    static final int SIZE = 124;
    static final int LEGEND_ROW = 12;
    private static final int MIN = 202, MAX = 823;
    private static final Pattern COORDS = Pattern.compile(
        "(?<![\\w.-])(?:x\\s*[:=]\\s*)?(\\d{3})\\s*[,; ]\\s*(?:y\\s*[:=]\\s*)?(\\d{1,3})\\s*[,; ]\\s*(?:z\\s*[:=]\\s*)?(\\d{3})(?![\\w.])",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern SHARED_SEPARATOR=Pattern.compile(" \\| ");
    enum Place {
        JUNGLE_TEMPLE("T","Temple","jungle temple"), MINES_OF_DIVAN("D","Divan","mines of divan"),
        GOBLIN_QUEENS_DEN("Q","Queen","goblin queen"), LOST_PRECURSOR_CITY("C","City","precursor city"),
        KHAZAD_DUM("B","Bal","khazad"), FAIRY_GROTTO("F","Grotto","fairy grotto"),
        DRAGONS_LAIR("L","Dragon","dragon's lair"), KING_YOLKAR("K","King","king yolkar"),
        CORLEONE("H","Corleone","corleone"), ODAWA("O","Odawa","odawa"),
        KEY_GUARDIAN("G","Guardian","key guardian"), XALX("X","Xalx","xalx");
        final String symbol, label, match;
        private ItemStack icon;
        ItemStack icon() {
            if(icon==null)icon=(switch(this){
                case JUNGLE_TEMPLE -> Items.VINE;
                case MINES_OF_DIVAN -> Items.DIAMOND_PICKAXE;
                case GOBLIN_QUEENS_DEN -> Items.GOLDEN_HELMET;
                case LOST_PRECURSOR_CITY -> Items.COMPARATOR;
                case KHAZAD_DUM -> Items.MAGMA_CREAM;
                case FAIRY_GROTTO -> Items.DYE.pick(net.minecraft.world.item.DyeColor.PINK);
                case DRAGONS_LAIR -> Items.DRAGON_EGG;
                case KING_YOLKAR -> Items.EGG;
                case CORLEONE -> Items.IRON_SWORD;
                case ODAWA -> Items.EMERALD;
                case KEY_GUARDIAN -> Items.TRIAL_KEY;
                case XALX -> Items.SPYGLASS;
            }).getDefaultInstance();
            return icon;
        }
        Place(String symbol,String label,String match){this.symbol=symbol;this.label=label;this.match=match;}
    }
    record Pin(Place place, int x, int y, int z, int mapX, int mapY) {
        Pin(Place place,int x,int y,int z){this(place,x,y,z,pixel(x),pixel(z));}
    }
    private static final List<Pin> combined = new ArrayList<>();
    private static final Place[] PLACES = Place.values();
    private static final Map<Place,Pin> local = new EnumMap<>(Place.class);
    private static final Map<Place,Pin> manual = new EnumMap<>(Place.class);
    private static final java.util.ArrayDeque<String> outgoing = new java.util.ArrayDeque<>();
    private static long nextShare;
    private static List<Pin> pins = List.of();
    private static final List<Pin> PREVIEW = List.of(new Pin(Place.JUNGLE_TEMPLE,322,100,430),new Pin(Place.MINES_OF_DIVAN,710,100,462));
    private static final long PREVIEW_MASK = mask(PREVIEW);
    private static long knownPlaces;
    private static ClientLevel world;
    private static NucleusWaypoints.RenderSnapshot projectedSnapshot;
    private static int[] waypointPixels = new int[0];

    static void reset(){world=null; projectedSnapshot=null; local.clear(); manual.clear(); outgoing.clear(); nextShare=0; pins=List.of(); knownPlaces=0;}
    static boolean inside(int x,int z){return x>=MIN && x<=MAX && z>=MIN && z<=MAX;}
    static int pixel(double coord){return 4+(int)Math.round(Math.clamp((coord-MIN)/(MAX-MIN),0d,1d)*(SIZE-9));}

    private static Place match(String text){
        for(Place place:PLACES) if(text.contains(place.match)) return place;
        return null;
    }

    static void update(Minecraft mc, List<String> sidebar){
        if(world!=null && world!=mc.level) reset();
        if(!HollowsDetector.isInCrystalHollows() || mc.player==null){
            if(world!=null)reset();
            return;
        }
        world=mc.level;
        for(String line:sidebar){
            int marker=line.indexOf('⏣');
            if(marker<0) marker=line.indexOf('ф');
            if(marker<0) continue;
            Place place=match(line.substring(marker+1).replace('’','\''));
            if(place!=null && !local.containsKey(place)){
                var pos=mc.player.blockPosition();
                if(inside(pos.getX(),pos.getZ()))local.put(place,new Pin(place,pos.getX(),pos.getY(),pos.getZ()));
            }
        }
        refreshPins();
    }

    private static void refreshPins(){
        combined.clear();
        SkyblockerMapPins.read(combined);
        long imported=mask(combined);
        for(Pin p:local.values())if((imported & (1L<<p.place.ordinal()))==0)combined.add(p);
        if(!manual.isEmpty()){
            combined.removeIf(pin -> manual.containsKey(pin.place));
            combined.addAll(manual.values());
        }
        if(!pins.equals(combined)){
            pins=List.copyOf(combined);
            knownPlaces=mask(pins);
        }
    }

    static void chat(String raw){
        var mc=Minecraft.getInstance();
        if(world==null || world!=mc.level || !HollowsDetector.isInCrystalHollows())return;
        boolean hasDigit=false;
        for(int i=0;i<raw.length();i++)if(Character.isDigit(raw.charAt(i))){hasDigit=true;break;}
        if(!hasDigit)return;
        String text=HollowsDetector.stripFormatting(raw).toLowerCase(Locale.ROOT).replace('’','\'');
        if(!text.contains(" | ")){readChatPin(text);return;}
        for(String part:SHARED_SEPARATOR.split(text))readChatPin(part);
    }

    private static void readChatPin(String text){
        Place place=match(text);
        if(place==null)return;
        var coords=COORDS.matcher(text);
        if(coords.find()){
            int x=Integer.parseInt(coords.group(1)), z=Integer.parseInt(coords.group(3));
            int y=Integer.parseInt(coords.group(2));
            if(inside(x,z) && y<=255)local.put(place,new Pin(place,x,y,z));
        }
    }

    static String set(Minecraft mc, Place place, int x, int y, int z) {
        if (!HollowsDetector.isInCrystalHollows() || mc.level == null) return "Join a Crystal Hollows lobby to set a landmark.";
        if (!inside(x,z) || y < 0 || y > 255) return "Use Hollows coordinates: X/Z 202–823 and Y 0–255.";
        if (world != mc.level) update(mc,List.of());
        manual.put(place,new Pin(place,x,y,z));
        refreshPins();
        return place.label + " set at " + x + ", " + y + ", " + z + " for this lobby."
            + (NucleusMod.CONFIG.hollowsMap ? "" : " Enable Crystal Hollows map in HUD & alerts to see it.");
    }

    static String share(Minecraft mc) {
        if (!HollowsDetector.isInCrystalHollows() || mc.level == null || mc.getConnection()==null)
            return "Join a Crystal Hollows lobby to share its landmarks.";
        if (!outgoing.isEmpty() || System.nanoTime() < nextShare) return "Please wait before sharing again.";
        update(mc,List.of());
        if (pins.isEmpty()) return "No landmark coordinates known in this lobby yet.";
        String message="";
        for (Pin pin:pins) {
            String label=pin.place.name().toLowerCase(Locale.ROOT).replace('_',' ');
            if(pin.place==Place.DRAGONS_LAIR) label="dragon's lair";
            String point=label+" "+pin.x+","+pin.y+","+pin.z;
            if (message.length()+3+point.length()>240) {outgoing.add(message);message="";}
            message=message.isEmpty()?point:message+" | "+point;
        }
        outgoing.add(message);
        sendNext(mc);
        return "Sharing "+pins.size()+" Hollows landmark coordinates in party chat.";
    }

    static void sendNext(Minecraft mc) {
        if (outgoing.isEmpty() || System.nanoTime()<nextShare) return;
        if (world!=mc.level || !HollowsDetector.isInCrystalHollows() || mc.getConnection()==null) {outgoing.clear();return;}
        mc.getConnection().sendCommand("pchat "+outgoing.removeFirst());
        nextShare=System.nanoTime()+1_500_000_000L;
    }

    static int height(boolean preview){
        var cfg=NucleusMod.CONFIG;
        long visible=(preview?PREVIEW_MASK:knownPlaces) & ~cfg.hiddenMapPins;
        return legendHeight(visible);
    }
    private static int legendHeight(long visible) {
        return NucleusMod.CONFIG.mapLegend ? SIZE+((Long.bitCount(visible)+1)/2)*LEGEND_ROW : SIZE;
    }
    private static long mask(List<Pin> points){
        long bits=0;
        for(Pin pin:points)bits|=1L<<pin.place.ordinal();
        return bits;
    }

    static void render(GuiGraphicsExtractor g){
        var cfg=NucleusMod.CONFIG;
        if(!cfg.kcaEnabled || !cfg.hollowsMap || !cfg.showGameplayHud || world==null) return;
        var mc=Minecraft.getInstance();
        if(world!=mc.level || mc.player==null || !HollowsDetector.isInCrystalHollows() || mc.gui.screen() instanceof MoveHudScreen || JackpotAnimation.cinematicActive())return;
        float scale=cfg.hollowsMapScale;
        int x=Math.clamp(cfg.hollowsMapX,0,Math.max(0,g.guiWidth()-(int)Math.ceil(SIZE*scale)));
        int y=Math.clamp(cfg.hollowsMapY,0,Math.max(0,g.guiHeight()-(int)Math.ceil(height(false)*scale)));
        draw(g,x,y,scale,false);
    }

    static void draw(GuiGraphicsExtractor g,int x,int y,float scale,boolean preview){
        draw(g, x, y, scale, preview, preview ? PREVIEW : pins, preview ? PREVIEW_MASK : knownPlaces);
    }

    static void drawEditor(GuiGraphicsExtractor g, int x, int y, float scale, List<Pin> sample, long sampleMask) {
        draw(g, x, y, scale, true, sample, sampleMask);
    }

    private static void draw(GuiGraphicsExtractor g, int x, int y, float scale, boolean preview,
                             List<Pin> points, long present) {
        var mc=Minecraft.getInstance();
        var cfg=NucleusMod.CONFIG;
        var pose=g.pose();
        pose.pushMatrix();
        try{
            pose.translate((float)x,(float)y);pose.scale(scale,scale);
            int mid=SIZE/2;
            present &= ~cfg.hiddenMapPins;
            g.fill(0,0,SIZE,legendHeight(present),0xE9241C29);
            g.fill(2,2,mid,mid,0xFF29473A);g.fill(mid,2,SIZE-2,mid,0xFF28505C);
            g.fill(2,mid,mid,SIZE-2,0xFF57412D);g.fill(mid,mid,SIZE-2,SIZE-2,0xFF41425B);
            if(cfg.mapRegions){
                g.centeredText(mc.font,"Jungle",mid/2,12,0xFFBBDAC7);
                g.centeredText(mc.font,"Mithril",mid+mid/2,9,0xFFC1E0E9);
                g.centeredText(mc.font,"Deposits",mid+mid/2,19,0xFFC1E0E9);
                g.centeredText(mc.font,"Goblin",mid/2,SIZE-25,0xFFE3CCAA);
                g.centeredText(mc.font,"Holdout",mid/2,SIZE-15,0xFFE3CCAA);
                g.centeredText(mc.font,"Precursor",mid+mid/2,SIZE-25,0xFFD0CDE6);
                g.centeredText(mc.font,"Remnants",mid+mid/2,SIZE-15,0xFFD0CDE6);
            }
            if(cfg.mapNucleus){
                g.fill(mid-2,mid-2,mid+3,mid+3,0xFFFFACCF);
                g.centeredText(mc.font,"Nucleus",mid,mid+4,0xFFFFD7E8);
            }
            float factor = cfg.mapMarkerPercent / 100f;
            int inset = (int)Math.ceil(6 * factor);
            for(Pin pin:points){
                if((cfg.hiddenMapPins & (1L<<pin.place.ordinal()))!=0)continue;
                int px = Math.clamp(pin.mapX, inset, SIZE - inset);
                int py = Math.clamp(pin.mapY, inset, SIZE - inset);
                if (factor == 1f) {
                    drawMarker(g, pin.place, px, py, cfg);
                } else {
                    pose.pushMatrix();
                    try {
                        pose.translate((float)px, (float)py);
                        pose.scale(factor, factor);
                        drawMarker(g, pin.place, 0, 0, cfg);
                    } finally { pose.popMatrix(); }
                }
            }
            if (cfg.mapWaypoints) {
                if (preview) g.fill(83, 83, 86, 86, 0xFFF7A6CC);
                else drawWaypoints(g);
            }
            int px=preview?mid-12:pixel(mc.player.getX()),pz=preview?mid-15:pixel(mc.player.getZ());
            g.fill(px-3,pz-1,px+4,pz+2,0xFF20202A);g.fill(px-1,pz-3,px+2,pz+4,0xFF20202A);
            g.fill(px-2,pz,px+3,pz+1,0xFFFFFFFF);g.fill(px,pz-2,px+1,pz+3,0xFFFFFFFF);
            int index=0;
            if(!cfg.mapLegend)return;
            for(Place place:PLACES)if((present&(1L<<place.ordinal()))!=0){
                int left=3+mid*(index%2),top=SIZE+(index/2)*LEGEND_ROW;
                drawMarker(g,place,left+5,top+5,cfg);
                g.text(mc.font,place.label,left+13,top+2,0xFFEAE4E7,true);
                index++;
            }
        }finally{pose.popMatrix();}
    }
    private static void drawWaypoints(GuiGraphicsExtractor g) {
        var snapshot=NucleusWaypoints.renderSnapshot();
        if(snapshot!=projectedSnapshot){
            int[] pixels=new int[snapshot.all().size()];
            int count=0;
            for(var point:snapshot.all()){
                var pos=point.pos();
                if(inside(pos.getX(),pos.getZ()))pixels[count++]=(pixel(pos.getX())<<8)|pixel(pos.getZ());
            }
            waypointPixels=java.util.Arrays.copyOf(pixels,count);
            projectedSnapshot=snapshot;
        }
        for(int point:waypointPixels){
            int x=point>>>8,y=point&255;
            g.fill(x-1,y-1,x+2,y+2,0xFFF7A6CC);
        }
    }
    private static void drawMarker(GuiGraphicsExtractor g,Place place,int x,int y,NucleusConfig cfg){
        boolean highlight=(cfg.highlightedMapPins & (1L<<place.ordinal()))!=0;
        var tint = cfg.mapPinColors.getOrDefault(place.name(), NucleusConfig.PinColor.Auto);
        int color = tint == NucleusConfig.PinColor.Auto ? (highlight ? 0xFFFFA4D1 : 0xFFFFDA81) : 0xFF000000 | tint.rgb;
        if(highlight)g.fill(x-5,y-5,x+6,y+6,color);
        g.fill(x-4,y-4,x+5,y+5,0xFF322431);
        switch(cfg.mapIcons.getOrDefault(place.name(),NucleusConfig.MapIcon.Letter)){
            case Letter -> {
                var font = Minecraft.getInstance().font;
                // The glyph advance includes a trailing pixel of spacing. Capitals occupy seven rows.
                int inkWidth = Math.max(1, font.width(place.symbol) - 1);
                g.text(font, place.symbol, x - inkWidth / 2, y - 3, color, false);
            }
            case Item -> {
                if (tint != NucleusConfig.PinColor.Auto) g.fill(x-4,y-4,x+5,y+5,color);
                var pose=g.pose();
                pose.pushMatrix();
                try {
                    pose.translate(x-3.5f,y-3.5f);
                    pose.scale(0.5f,0.5f);
                    g.item(place.icon(),0,0);
                } finally { pose.popMatrix(); }
            }
            case Square -> g.fill(x-2,y-2,x+3,y+3,color);
            case Cross -> {g.fill(x-3,y-1,x+4,y+2,color); g.fill(x-1,y-3,x+2,y+4,color);}
            case Diamond -> {for(int dy=-3;dy<=3;dy++){int r=3-Math.abs(dy);g.fill(x-r,y+dy,x+r+1,y+dy+1,color);}}
        }
    }
    private HollowsMap(){}
}
