package dev.purplepad;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class BetaBadges {
    private static final Set<String> TESTERS=load();
    public static final Component LABEL=Component.empty()
        .append(Component.literal("B").withColor(0xFF589A))
        .append(Component.literal("E").withColor(0xFF83B1))
        .append(Component.literal("T").withColor(0xFFAACB))
        .append(Component.literal("A").withColor(0xFFD4E5));
    private BetaBadges(){}
    private static Set<String> load(){
        var players=new HashSet<String>();
        try(var stream=BetaBadges.class.getResourceAsStream("/assets/kca/beta-testers.txt")){
            if(stream==null)return Set.of();
            try(var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
                reader.lines().map(String::strip).filter(s->!s.isEmpty() && !s.startsWith("#"))
                    .map(s->s.toLowerCase(Locale.ROOT)).forEach(players::add);
            }
        }catch(java.io.IOException e){com.nucleus.NucleusMod.LOGGER.warn("Could not read the KCA beta tester list",e);}
        return Set.copyOf(players);
    }
    public static boolean active(){
        return !TESTERS.isEmpty() && com.nucleus.NucleusMod.CONFIG.showBetaBadges && PurplePadClient.hudEnabled();
    }
    public static boolean listed(Player player){
        return TESTERS.contains(player.getGameProfile().id().toString())
            || TESTERS.contains(player.getGameProfile().name().toLowerCase(Locale.ROOT));
    }
}
