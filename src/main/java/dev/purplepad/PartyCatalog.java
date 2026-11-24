package dev.purplepad;

import java.util.*;

final class PartyCatalog {
    record Entry(String names,String usage,String description,String source) {
        String key(){int cut=names.indexOf(' ');return cut<0?names:names.substring(0,cut);}
    }
    static final List<Entry> entries=new ArrayList<>();
    static final Set<String> dianaTrackers=new HashSet<>();
    static final Map<String,Entry> aliases=new LinkedHashMap<>();
    private static void add(String source,String names,String usage,String desc){
        var entry=new Entry(names,usage,desc,source);entries.add(entry);
        for(String alias:names.split(" ")){
            if(aliases.put(alias,entry)!=null)throw new IllegalStateException("Duplicate "+alias);
        }
    }
    static {
        add("KCA","help h","[page or command]","List commands or explain one.");
        add("KCA","coords cords co","","When a party member types !coords, KCA replies in party chat with your current X, Y and Z block coordinates.");
        if(KcaEdition.DUNGEONS)add("KCA","lowest lowestsecrets","","Share the last completed dungeon’s lowest non-Mage counts.");
        add("KCA","ping","","Show your ping.");
        add("KCA","fps","","Show your frame rate.");
        add("KCA","time","","Show your local time.");
        add("KCA","holding showitem show","","Reply in party chat with the name of the item in the KCA player’s main hand.");
        add("KCA","cf","","Flip a coin.");
        add("KCA","warp w wp","","Warp the party.");
        add("KCA","allinv allinvite invites ai","","Toggle the party's All Invite setting.");
        add("KCA","pt ptme transfer","<name or prefix>","Use !pt gar or !transfer gar to pass leadership to a named party member. Use !ptme by itself to request leadership for yourself.");
        add("KCA","promote","[player]","Promote the sender or named player.");
        add("KCA","demote","[player]","Demote the sender or named player.");
        add("KCA","kick k","<name or prefix>","Use !k gar to kick the party member whose name starts with gar. If several match, type more letters.");
        add("KCA","kpt invlast reinvlast","","Invite the last player Hypixel confirmed was kicked from this party. Use !warp first if you want to warp the remaining members.");
        add("KCA","kickoffline ko","","Remove offline party members.");
        add("KCA","invite inv kidnap p","<player>","Invite the named player.");
        add("KCA","boop","[player]","Boop the sender or named player.");
        add("KCA","f0","","Queue the Catacombs Entrance.");
        for(String family:(KcaEdition.KUUDRA?List.of("f","m","t"):List.of("f","m"))){
            int count=family.equals("t")?5:7;
            for(int i=1;i<=count;i++)add("KCA",family+i,"","Queue "+(family.equals("t")?"Kuudra tier ":family.equals("f")?"Catacombs floor ":"Master Mode floor ")+i+".");
        }
        add("Odin / Noamm","dt downtime","[reason]","Set a downtime reminder with the source mod.");
        add("Odin","undt undowntime","","Remove your downtime reminder.");
        add("KCA","reinv reinvite rpt","","Invite the requester back after they leave. Type this before leaving. A party member running KCA sends the invite.");
        add("Odin","8ball","[question]","Ask Odin's eight ball.");
        add("Odin","dice","","Roll Odin's dice.");
        add("Odin","odin od","","Show Odin's party reply.");
        add("Odin","location","","Share Odin's current location.");
        add("Odin","racism","[player]","Odin's novelty roll.");
        add("Noamm","gay","[player]","Noamm's novelty roll.");
        add("IQ / SBO / Odin / Noamm","tps","","Share the source mod's server TPS.");
        if(KcaEdition.KUUDRA)add("IQ","runs","[player]","Kuudra session runs, failures and average time.");
        if (KcaEdition.NUCLEUS) {
            add("KCA","pb cnpb","","Share your fastest recorded Crystal Nucleus run.");
            add("KCA","tools toolsets","","Share how many complete Divan tool sets your KCA counter has left.");
            add("KCA","cnruns","","Share your Crystal Nucleus runs this lobby and your total.");
        }
        if (KcaEdition.DUNGEONS) add("KCA","tpb theoreticalbest theroeticalbest","<floor>",
            "Share the sum of your best dungeon segments. Example: !tpb m7.");
        if(KcaEdition.KUUDRA)add("IQ","kuudrapb kpb","","Kuudra session and lifetime personal best.");
        if(KcaEdition.KUUDRA)add("IQ","chests","","Kuudra chest progress.");
        if(KcaEdition.KUUDRA)add("IQ / SBO","profit","","Kuudra or Diana profit. Choose a source in Routing.");
        else add("SBO","profit","","Share your Diana profit.");
        add("SBO","since","<drop>","SBO's counters since your last drop.");
        add("SBO","stats stat","<player>","Request the named player's Diana stats.");
        add("SBO","totalstats totalstat","<player>","Request the named player's total Diana stats.");
        add("SBO","sessionstats sessionstat","<player>","Request the named player's session Diana stats.");
        add("SBO","q","","Check your SBO party finder queue.");
        add("SBO","carrot c","","Pick from SBO's party replies.");
        add("SBO","version","","Show SBO's version.");
        add("SBO","block","<player>","Add the named player to the server block list.");
        add("SBO","unblock","<player>","Remove the named player from the server block list.");
        String[] groups={
            "chim chimera chims chimeras book books","inqsls inquisitorls inquisls lsinq lsinqs lsinquisitor lsinquis",
            "inq inqs inquisitor inquis","kingls kingsls","king kings","sphinxls sphinxsls","sphinx sphinxs",
            "mantils mantisls","manti mantis","dye dyes","burrows burrow","relic relics",
            "chimls chimerals bookls lschim lsbook lootsharechim lschimera","core manticore","corels manticorels lscore lsmanticore",
            "stinger fatefulstinger","stingerls fatefulstingerls lsstinger lsfatefulstinger","wool shimmering shimmeringwool",
            "woolls shimmeringwoolls lsshimmering lsshimmeringwool","food brainfood brain","foodls brainfoodls lsbrainfood lsbrain",
            "looting looting7 loot7","lootingls looting7ls lslooting lsloot7","braided braideds","kingshard kingshards",
            "sphinxshard sphinxshards","minotaurshard minotaurshards","certanshard certanshards","mythofrag frags",
            "urns urn cretanurn","hilt hiltofrevelations","sticks stick","feathers feather","coins coin","mobs mob",
            "mf magicfind","playtime","profits"
        };
        for(String names:groups){
            add("SBO",names,"","Share the corresponding Diana tracker result.");
            dianaTrackers.add(entries.getLast().key());
        }
    }
    private PartyCatalog(){}
}
