package dev.purplepad;

import java.util.*;
import java.util.regex.Pattern;

/** Collects one Hypixel /party list response, including members in other lobbies. */
final class PartyRoster {
    private static final Pattern HEADER = Pattern.compile("Party Members \\((\\d{1,3})\\)(?: \\(\\d+\\))?");
    private static final Pattern ROW = Pattern.compile("Party (Leader|Moderators|Members):\\s*(.*)");
    private static final Pattern RANK = Pattern.compile("\\[[^\\]\\r\\n]+\\]");
    private static final Pattern USER = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private final Set<String> members = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private int expected;
    private boolean sawLeader;

    void accept(String line) {
        var header=HEADER.matcher(line);
        if(header.matches()){
            expected=Integer.parseInt(header.group(1));
            members.clear();
            sawLeader=false;
            return;
        }
        if(expected==0)return;
        var row=ROW.matcher(line);
        if(!row.matches())return;
        String names=RANK.matcher(row.group(2)).replaceAll(" ").replaceAll("[\\p{So}\\p{Sk}\\p{Pd}\\p{Cf}\\p{M}]"," ").strip();
        if(names.isEmpty())return;
        var tokens=names.split("\\s+");
        if(row.group(1).equals("Leader")){
            if(tokens.length!=1){expected=0;return;}
            sawLeader=true;
        }
        for(String name:tokens){
            if(!USER.matcher(name).matches()){expected=0;return;}
            members.add(name);
        }
    }

    boolean contains(String name){return members.contains(name);}
    boolean complete(){return sawLeader && members.size()==expected;}

    List<String> matches(String prefix){
        var matches=new ArrayList<String>();
        for(String name:members){
            if(name.equalsIgnoreCase(prefix))return List.of(name);
            if(name.regionMatches(true,0,prefix,0,prefix.length()))matches.add(name);
        }
        return matches;
    }
}
