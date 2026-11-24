package dev.purplepad;

import java.util.List;

public class PartyRosterTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        var party=new PartyRoster();
        party.accept("Party Members (4)");
        party.accept("Party Leader: [MVP+] Tester ●");
        party.accept("Party Moderators: ♲ [MVP++] Garrulous_ ●");
        check(!party.complete());
        party.accept("Party Members: Rose ● Rosetta ●");
        check(party.complete() && party.contains("tester"));
        check(party.matches("gAr").equals(List.of("Garrulous_")));
        check(party.matches("ros").equals(List.of("Rose","Rosetta")));
        check(party.matches("ROSE").equals(List.of("Rose")));
        check(party.matches("absent").isEmpty());
        party.accept("Party Members (2)");
        party.accept("Party > Guest: Party Leader: Guest");
        party.accept("Party Members: Fake ● Other ●");
        check(!party.complete() && !party.contains("Garrulous_"));
        party.accept("Party Members (2) (3)");
        party.accept("Party Leader: [MVP++] Tester ●");
        party.accept("Party Members: [MVP+] Caandy_ ●");
        check(party.complete() && party.matches("caan").equals(List.of("Caandy_")));
        check(PartyCatalog.aliases.get("rpt")==PartyCatalog.aliases.get("reinv"));
        check(PartyCatalog.aliases.get("reinvite").key().equals("reinv"));
        check(PartyCatalog.aliases.get("p")==PartyCatalog.aliases.get("invite"));
        System.out.println("Party roster and alias tests passed.");
    }
}
