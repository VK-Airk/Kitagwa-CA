package dev.purplepad;

/** Build-time branch choices. Regular development builds include every branch. */
public final class KcaEdition {
    public static final boolean NUCLEUS=true;
    public static final boolean GENERAL=true;
    public static final boolean DUNGEONS=true;
    public static final boolean CHAT=true;
    public static final boolean KUUDRA=true;
    public static final boolean HAS_HUD=NUCLEUS || DUNGEONS;

    static boolean includesNode(int node){
        return switch(node){
            case 0 -> NUCLEUS;
            case 1 -> GENERAL;
            case 2 -> DUNGEONS;
            case 3 -> CHAT;
            case 4 -> KUUDRA;
            default -> false;
        };
    }

    private KcaEdition(){}
}
