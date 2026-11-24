package dev.purplepad;

import java.util.regex.Pattern;
import java.util.List;

final class DungeonLocation {
    private static final Pattern FLOOR=Pattern.compile("\\bCatacombs\\s*\\(\\s*(?:[FM][1-7]|E)\\s*\\)",Pattern.CASE_INSENSITIVE);
    private static final Pattern FORMATTING=Pattern.compile("§.");
    private static final Pattern REPORT=Pattern.compile("(Master Mode )?(?:The )?Catacombs - (?:Floor (VII|VI|IV|V|III|II|I)|Entrance)");
    private static final List<String> ROMAN=List.of("","I","II","III","IV","V","VI","VII");
    private static final Pattern FINISH_TIME=Pattern.compile("☠ Defeated [^:\\r\\n]+ in (?:(\\d{1,3})m )?(\\d{1,2})(?:\\.(\\d{1,3}))?s");

    static long reportedTime(String line){
        if(!line.startsWith("☠ Defeated "))return -1;
        var match=FINISH_TIME.matcher(line);
        if(!match.matches())return -1;
        int minutes=match.group(1)==null?0:Integer.parseInt(match.group(1));
        int seconds=Integer.parseInt(match.group(2));
        String fraction=match.group(3);
        long ms=(60L*minutes+seconds)*1000L+(fraction==null?0:Integer.parseInt((fraction+"00").substring(0,3)));
        return seconds<60 && ms>0 ? ms : -1;
    }

    static String reportedFloor(String line){
        var match=REPORT.matcher(line);
        if(!match.matches())return "";
        if(match.group(2)==null)return "E";
        return (match.group(1)==null?"F":"M")+ROMAN.indexOf(match.group(2));
    }

    static boolean hasFloor(String line){
        return FLOOR.matcher(FORMATTING.matcher(line).replaceAll("")).find();
    }
}
