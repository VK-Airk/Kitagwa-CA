package dev.purplepad;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RunStatsTest {
    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
    public static void main(String[] args) {
        var rooms = new RoomClears();
        byte[] map = new byte[128*128];
        var players = Map.of("Archer",0,"Mage",1,"Tank",2);
        List<List<String>> awards = new ArrayList<>();
        Runnable scan = () -> rooms.update(map,2,2,16,players,(id,names)->awards.add(names));
        for (int i=0;i<3;i++) map[2+20*i+128*2]=63;
        map[20+128*6]=63; // wide connection between the first two tiles
        scan.run();
        map[10+128*10]=34;
        scan.run();
        check(awards.size()==1 && awards.getFirst().size()==2 && awards.getFirst().containsAll(List.of("Archer","Mage")), "multi-tile shared clear");
        map[30+128*10]=30;
        scan.run();
        check(awards.size()==1,"green check or second tile must not award another room");
        map[50+128*10]=34;
        scan.run();
        check(awards.size()==2 && awards.getLast().equals(List.of("Tank")),"separate solo clear");
        rooms.reset();
        scan.run();
        check(awards.size()==2,"map already cleared at first observation");
        var replies=new SecretCountReply();
        var totals=new java.util.TreeMap<String,Long>(String.CASE_INSENSITIVE_ORDER);
        check(!replies.read("Alex: 900",totals::put),"row outside requested header");
        check(replies.read("  §6Secret Counts:\n §8♲ [VIP+] Armaraa: §b46,893\n§d$ Garrulous_ $: 4,399",totals::put),"formatted multiline server reply");
        check(totals.get("armaraa")==46893 && totals.get("Garrulous_")==4399,"attributed named lifetime totals");
        check(!replies.read("Party > Alex: 0",totals::put),"unrelated chat stays visible");
        replies.reset();
        check(!replies.read("Armaraa: 999",totals::put),"new request requires its own header");
        var run=new SecretSamples();
        run.record(totals.get("Armaraa"),1,false);
        check(replies.read("Secret Counts:",totals::put) && replies.read("Armaraa: 46,898",totals::put),"separate header and count packets");
        run.recordBoss(totals.get("Armaraa"));
        check(run.delta(true).or(run::bossCheckpoint).orElseThrow()==5,"post-clear reading survives a disband with no final reply");
        run.record(0,4,true);
        check(run.delta(true).or(run::bossCheckpoint).orElseThrow()==5,"invalid end counter preserves boss checkpoint");
        run.record(46900,4,true);
        check(run.delta(true).orElseThrow()==7,"final reading supersedes boss-entry checkpoint");
        var command = new SecretSamples();
        command.record(500,1,false);
        command.record(507,2,false);
        check(command.delta(false).orElseThrow()==7, "live delta");
        check(command.delta(true).isEmpty(),"missing end sample");
        command.record(511,3,true);
        check(command.delta(true).orElseThrow()==11,"final delta");
        var api = new SecretSamples();
        api.record(900,3,true);
        check(api.delta(true).isEmpty(),"missing baseline");
        command.record(3,4,true);
        check(command.delta(true).isEmpty(),"counter reset");
        check(DungeonLocation.hasFloor("§7⏣ §cThe Cata§ccombs §7(F7)"),"formatted floor");
        check(DungeonLocation.hasFloor("The Catacombs (M5)"),"master mode");
        check(DungeonLocation.hasFloor("The Catacombs (E)"),"entrance");
        check(!DungeonLocation.hasFloor("⏣ Dungeon Hub"),"hub is outside");
        check(!DungeonLocation.hasFloor("Catacombs Level 42"),"skill level is not a location");
        var start=CounterPlacement.at(320,240,100,28,0,0);
        check(start.x()==4 && start.y()==4,"default counter position");
        var end=CounterPlacement.at(640,360,260,60,1,1);
        check(end.x()+end.width()==636 && end.y()+end.height()==356,"party stays on screen at the corner");
        check(CounterPlacement.fraction(end.x(),end.travelX())==1,"drag round trip");
        check(CounterPlacement.fraction(-10,80)==0 && CounterPlacement.fraction(500,80)==1,"drag outside edges");
        var custom=SecretMessage.format("{name} {secrets}/{secrets} {rooms} {solo} {shared} {deaths}",
            "Archer",3,2,"?",1,false,'b','d');
        check(custom.equals("Archer §b?§r/§b?§r §d3-5§r 3 2 1"),"all message fields, repeated values and missing sample");
        check(SecretMessage.format("", "Mage",1,0,"1",1,false,'a','e')
            .equals("Mage | Secrets: §a1§r | Rooms: §e1§r | Deaths: 1"),"default singular report");
        check(SecretMessage.format(" ", "Mage",1,1,"2",0,true,'b','d')
            .equals("§b2 Secrets  §8·  §d1-2 Rooms"),"blank template restores compact default");
        check(SecretMessage.format("{name} {deaths}", "Tank",0,0,"?",0,true,'b','d')
            .equals("Tank 0"),"custom compact message includes player and zero deaths");
        for(var floor:List.of("I","II","III","IV","V","VI","VII")){
            int number=List.of("I","II","III","IV","V","VI","VII").indexOf(floor)+1;
            check(DungeonLocation.reportedFloor("The Catacombs - Floor "+floor).equals("F"+number),"normal floor "+number);
            check(DungeonLocation.reportedFloor("Master Mode Catacombs - Floor "+floor).equals("M"+number),"master floor "+number);
        }
        check(DungeonLocation.reportedFloor("Catacombs - Entrance").equals("E"),"entrance report");
        check(DungeonLocation.reportedFloor("Archer: The Catacombs - Floor VII").isEmpty(),"player chat ignored");
        check(DungeonLocation.reportedFloor("The Catacombs - Floor VIII").isEmpty(),"unknown floor ignored");
        var received=ClearReport.fromNoamm("[Noamm] HealingBOB: 0-4 Rooms | 9 Secrets");
        check(received!=null && received.solo()==0 && received.shared()==4 && received.secrets()==9,"Noamm observed report");
        check(ClearReport.fromNoamm("[Noamm] Alex: 1 Room | 1 Secret | 2 Deaths").solo()==1,"singular and optional deaths");
        check(ClearReport.fromNoamm("[Noamm] unrelated")==null,"preserve unrelated rows");
        var team=java.util.List.of(new ClearReport("Mage1","Mage",0,0),new ClearReport("Mage2","Mage",0,0),
            new ClearReport("Bee","Healer",3,5),new ClearReport("Ari","Archer",5,2),new ClearReport("Zed","Tank",3,2));
        check(ClearReport.lowestContribution(team).equals("Zed"),"65/35 excludes Mages");
        check(ClearReport.lowestContribution(List.of(new ClearReport("A","Tank",5,15),new ClearReport("B","Archer",15,5))).equals("B"),"secrets carry more weight");
        check(ClearReport.lowestContribution(List.of(new ClearReport("A","Tank",1,1),new ClearReport("B","Archer",1,1))).isEmpty(),"tie clears streak");
        check(ClearReport.lowestContribution(List.of(new ClearReport("A","Tank",-1,0),new ClearReport("B","Archer",1,1))).isEmpty(),"missing rooms interrupt streak");
        var lows=ClearReport.lowest(team);
        check(lows.get(0).endsWith("Bee, Zed (3)"),"tied rooms excluding every Mage");
        check(lows.get(1).endsWith("Ari, Zed (2)"),"independent secret winners");
        var incomplete=ClearReport.lowest(java.util.List.of(new ClearReport("Unknown","",0,0),new ClearReport("Archer","Archer",1,-1)));
        check(incomplete.get(0).endsWith("[partial]")&&incomplete.get(1).endsWith("no data."),"unknown class and counters");
        check(ClearReport.lowest(java.util.List.of(team.getFirst())).size()==1,"Mage only");
        check(DungeonLocation.reportedTime("☠ Defeated Maxor, Storm, Goldor, and Necron in 04m 52s")==292000,"server F7 time");
        check(DungeonLocation.reportedTime("☠ Defeated Bonzo in 59.25s")==59250,"fractional seconds");
        check(DungeonLocation.reportedTime("Party > Alex: ☠ Defeated Bonzo in 1s")==-1,"ignore player message");
        check(DungeonLocation.reportedTime("Run Time 4m 52.14s (287.65s)")==-1,"ignore mod recap");
        check(DungeonLocation.reportedTime("☠ Defeated Bonzo in 04m 60s")==-1,"invalid seconds");
        System.out.println("Dungeon report, secret sampling, time, location and placement checks passed.");
    }
}
