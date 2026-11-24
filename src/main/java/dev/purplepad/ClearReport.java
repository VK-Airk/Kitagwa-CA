package dev.purplepad;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.ToLongFunction;
import java.util.regex.Pattern;

record ClearReport(String name, String role, long rooms, long secrets) {
    record Counts(String name, int solo, int shared, long secrets) {}
    private static final Pattern NOAMM = Pattern.compile(
        "([A-Za-z0-9_]{1,16}):\\s*(\\d+)(?:-(\\d+))? Rooms?\\s*\\|\\s*(\\d+) Secrets?(?:\\s*\\|.*)?$", Pattern.DOTALL);

    // Called only on components produced by Noamm's end-of-run reporter.
    static Counts fromNoamm(String text) {
        var m=NOAMM.matcher(text.strip());
        if(!m.find())return null;
        try {
            int solo=Integer.parseInt(m.group(2)), total=m.group(3)==null?solo:Integer.parseInt(m.group(3));
            return total<solo?null:new Counts(m.group(1),solo,total-solo,Long.parseLong(m.group(4)));
        }catch(NumberFormatException ignored){return null;}
    }

    static List<String> lowest(Collection<ClearReport> team) {
        return lowest(team,DEFAULT_LOWEST);
    }
    static final String DEFAULT_LOWEST="Fewest {metric} (non-Mage): {names} ({count})";
    static List<String> lowest(Collection<ClearReport> team,String template) {
        var eligible=team.stream().filter(p->!p.role.isEmpty()&&!p.role.equalsIgnoreCase("Mage")).toList();
        if(eligible.isEmpty())return List.of("Lowest non-Mage: no eligible players with a known class.");
        boolean missingClass=team.stream().anyMatch(p->p.role.isEmpty());
        return List.of(minimum("rooms",eligible,ClearReport::rooms,missingClass,template),
                       minimum("secrets",eligible,ClearReport::secrets,missingClass,template));
    }
    // Compare shares of the non-Mage team totals so a secret and a room
    // don't get the same weight just because both are counted as integers.
    static String lowestContribution(Collection<ClearReport> team){
        var eligible=team.stream().filter(p->!p.role.equalsIgnoreCase("Mage")).toList();
        if(eligible.size()<2 || eligible.stream().anyMatch(p->p.role.isBlank() || p.secrets<0 || p.rooms<0))return "";
        double secrets=eligible.stream().mapToDouble(ClearReport::secrets).sum();
        double rooms=eligible.stream().mapToDouble(ClearReport::rooms).sum();
        double min=Double.POSITIVE_INFINITY;
        String lowest="";
        for(var p:eligible){
            double score=(secrets>0?.65*p.secrets/secrets:0)+(rooms>0?.35*p.rooms/rooms:0);
            if(score<min-1e-10){min=score;lowest=p.name;}
            else if(Math.abs(score-min)<1e-10)lowest="";
        }
        return lowest;
    }
    private static String minimum(String metric,List<ClearReport> group,ToLongFunction<ClearReport> value,boolean partial,String template){
        long min=Long.MAX_VALUE;
        var names=new ArrayList<String>();
        for(var player:group){
            long n=value.applyAsLong(player);
            if(n<0){partial=true;continue;}
            if(n<min){min=n;names.clear();}
            if(n==min)names.add(player.name);
        }
        if(names.isEmpty())return "Fewest "+metric+" (non-Mage): no data.";
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return (template.isBlank()?DEFAULT_LOWEST:template).replace("{metric}",metric).replace("{names}",String.join(", ",names)).replace("{count}",Long.toString(min))+(partial?" [partial]":"");
    }
}
