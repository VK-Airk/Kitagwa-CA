package dev.purplepad;

import java.util.function.ObjLongConsumer;
import java.util.regex.Pattern;

/** Reads the server's named lifetime counts, whether delivered as one packet or several. */
final class SecretCountReply {
    private static final Pattern FORMAT=Pattern.compile("§.");
    private static final Pattern ROW=Pattern.compile(
        "^(?:(?:\\[[A-Za-z0-9+ -]{1,18}\\]|[^\\p{L}\\p{N}_:>\\[\\]])\\s*+)*+"
        +"([A-Za-z0-9_]{1,16})[^\\p{L}\\p{N}_:>\\[\\]]*:\\s*([0-9]{1,3}(?:,[0-9]{3}){1,3}|[0-9]{1,12})$");
    private boolean header;
    void reset(){header=false;}
    boolean read(String text,ObjLongConsumer<String> accept){
        if(!header && !text.contains("Secret Counts"))return false;
        if(text.indexOf('§')>=0)text=FORMAT.matcher(text).replaceAll("");
        if(text.indexOf('\n')<0)return line(text.strip(),accept);
        boolean any=false,all=true;
        for(String row:text.split("\\R")){
            if(row.isBlank())continue;
            boolean match=line(row.strip(),accept);any|=match;all&=match;
        }
        return any && all;
    }
    private boolean line(String text,ObjLongConsumer<String> accept){
        if(text.equals("Secret Counts:")){header=true;return true;}
        if(!header || text.length()>160)return false;
        var row=ROW.matcher(text);
        if(!row.matches())return false;
        accept.accept(row.group(1),Long.parseLong(row.group(2).replace(",","")));
        return true;
    }
}
