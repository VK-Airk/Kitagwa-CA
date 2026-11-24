package dev.purplepad;

import java.lang.reflect.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

final class PartySources {
    private static final String SBO="net.sbo.mod.general.PartyCommands";
    private static final String ODIN="com.odtheking.odin.features.impl.skyblock.ChatCommands";
    private static final String NOAMM="com.github.noamm9.features.impl.general.PartyHelper";
    private static final String IQ="net.iqaddons.mod.";
    private static Object iqFormatter;
    private PartySources(){}
    static boolean loaded(String id){return FabricLoader.getInstance().isModLoaded(id);}
    private static Class<?> type(String name)throws ClassNotFoundException{return Class.forName(name);}
    private static Object singleton(String name)throws ReflectiveOperationException{return type(name).getField("INSTANCE").get(null);}
    private static Object call(Object target,String member,Class<?>[] types,Object... args)throws ReflectiveOperationException {
        Class<?> owner=target instanceof Class<?> c?c:target.getClass();
        Method method=owner.getDeclaredMethod(member,types);
        method.setAccessible(true);
        return method.invoke(target instanceof Class<?>?null:target,args);
    }
    private static Object get(Object target,String method)throws ReflectiveOperationException{return call(target,method,new Class<?>[0]);}
    private static long number(Object target,String field)throws ReflectiveOperationException{return target.getClass().getField(field).getLong(target);}
    private static boolean addressed(String[] args){return args.length==0 || args[0].equalsIgnoreCase(Minecraft.getInstance().player.getGameProfile().name());}

    static String provider(PartyCatalog.Entry e){
        return switch(e.key()){
            case "profit" -> {
                if(!KcaEdition.KUUDRA)yield "SBO";
                String pref=com.nucleus.NucleusMod.CONFIG.partyProfitSource;
                yield "Auto".equals(pref)?first("IQ","SBO"):pref;
            }
            case "tps" -> first("IQ","SBO","Odin","Noamm");
            case "dt" -> first("Odin","Noamm");
            default -> e.source();
        };
    }
    static boolean available(String name){
        return switch(name){
            case "KCA" -> true;
            case "IQ" -> loaded("iqaddons");
            case "SBO" -> loaded("sbo");
            case "Odin" -> loaded("odin");
            case "Noamm" -> loaded("noammaddons");
            default -> false;
        };
    }
    private static String first(String... names){
        for(String name:names)if(available(name))return name;
        return String.join(" or ",names);
    }
    static void run(PartyCatalog.Entry entry,String alias,String sender,String[] args)throws ReflectiveOperationException {
        String provider=provider(entry),key=entry.key();
        if(!available(provider)){PartyChat.notice("!"+alias+" — source mod "+provider+" is not on.");return;}
        switch(provider){
            case "IQ" -> {
                if(key.equals("tps"))PartyChat.reply(String.format(Locale.ROOT,"TPS: %.1f",get(type(IQ+"utils.ServerUtils"),"getAverageTps")));
                else iq(key,args);
            }
            case "SBO" -> {
                if(key.equals("tps"))PartyChat.reply(String.valueOf(get(singleton("net.sbo.mod.utils.game.ServerStats"),"getTpsString")));
                else sbo(alias,sender,args);
            }
            case "Noamm" -> noamm(key,sender,args);
            case "Odin" -> odin(key,sender,args);
        }
    }
    private static void odin(String key,String sender,String[] args)throws ReflectiveOperationException{
        Object handler=singleton(ODIN);
        Class<?> channel=type(ODIN+"$ChatChannel");
        Field partyChannel=channel.getField("PARTY");
        partyChannel.setAccessible(true);
        call(handler,"handleChatCommands",new Class<?>[]{String.class,String.class,channel},
            "!"+key+(args.length>0?" "+String.join(" ",args):""),sender,partyChannel.get(null));
    }
    private static void noamm(String key,String sender,String[] args)throws ReflectiveOperationException{
        call(singleton(NOAMM),"handlePartyCommand",new Class<?>[]{String.class,String.class,List.class},sender,key,List.of(args));
    }
    private static void iq(String key,String[] args)throws ReflectiveOperationException{
        if(!KcaEdition.KUUDRA)return;
        if(key.equals("chests")){
            Object counter=get(type(IQ+"manager.ChestCounterManager"),"get");
            PartyChat.reply("[IQ] Chests: "+get(counter,"getChests")+"/60");return;
        }
        if(key.equals("runs")&&!addressed(args))return;
        Object manager=get(type(IQ+"manager.pricing.KuudraProfitTrackerManager"),"get");
        Object data=get(manager,key.equals("profit")?"current":"session");
        if(iqFormatter==null)iqFormatter=type(IQ+"features.generic.PartyCommandsFeature").getConstructor().newInstance();
        if(key.equals("kuudrapb")){
            Object pb=get(type(IQ+"manager.PersonalBestManager"),"get");
            boolean known=Boolean.TRUE.equals(get(pb,"hasPersonalBest"));
            long best=known?(long)get(pb,"getBestTimeMillis"):number(get(manager,"lifetime"),"bestRunMillis");
            PartyChat.reply("[IQ] Best Personal Time (Tier "+(known?get(get(pb,"getTier"),"getDisplayName"):"Unknown")
                +"): Session "+format("formatRunTime",number(data,"bestRunMillis"))+" / Lifetime: "+format("formatRunTime",best));return;
        }
        double avg=(long)get(data,"averageRunMillis")/1000.0;
        if(key.equals("runs"))PartyChat.reply(String.format(Locale.ROOT,"[IQ] Runs: %d (F:%d) | Avg: %.2fs",number(data,"runs"),number(data,"failedRuns"),avg));
        else PartyChat.reply(String.format(Locale.ROOT,"[IQ] Profit: %s | Rate: %s/h | Runs: %d | Avg: %.2fs",
            format("formatCoins",number(data,"profit")),format("formatCoins",(long)get(data,"hourlyRateCoins")),number(data,"runs"),avg));
    }
    private static Object format(String method,long value)throws ReflectiveOperationException{
        return call(iqFormatter,method,new Class<?>[]{long.class},value);
    }
    private static void sbo(String alias,String sender,String[] args)throws ReflectiveOperationException{
        Object handler=singleton(SBO);
        String key=PartyCatalog.aliases.get(alias).key();
        switch(key){
            case "since" -> call(handler,"handleSinceCommand",new Class<?>[]{String.class},args.length>0?args[0]:null);
            case "stats","totalstats","sessionstats" -> {
                if(args.length==1 && addressed(args))call(singleton("net.sbo.mod.diana.DianaStats"),"sendPlayerStats$sbo_0_8_0_26_1_2",
                    new Class<?>[]{Boolean.class},key.equals("sessionstats")?null:Boolean.valueOf(key.equals("totalstats")));
            }
            case "version" -> {Object mod=singleton("net.sbo.mod.SBOKotlin");PartyChat.reply("SBO "+get(mod,"getVersion")+" | Minecraft "+get(mod,"getMcVersion"));}
            case "q" -> {Object manager=singleton("net.sbo.mod.partyfinder.PartyFinderManager");if(Boolean.TRUE.equals(get(manager,"isLeader")))
                PartyChat.reply(Boolean.TRUE.equals(get(manager,"getInQueue"))?"Party is in queue.":"Party is not in queue.");}
            case "carrot" -> {Field f=handler.getClass().getDeclaredField("carrot");f.setAccessible(true);List<?> values=(List<?>)f.get(null);
                if(!values.isEmpty())PartyChat.reply(String.valueOf(values.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(values.size()))));}
            case "block","unblock" -> {
                if(args.length==1 && PartyChat.playerName(args[0]) && Boolean.TRUE.equals(get(get(handler,"getSettings"),"getBlockUser")))
                    PartyChat.server("block "+(key.equals("block")?"add ":"remove ")+args[0]);
            }
            default -> {
                Object cmd=((Map<?,?>)get(handler,"getCommandMap")).get("!"+alias);
                if(cmd==null)throw new NoSuchMethodException("SBO alias !"+alias);
                Object fn=get(cmd,"getExecute");
                Object line=type("kotlin.jvm.functions.Function0").getMethod("invoke").invoke(fn);
                if(line instanceof String result && !result.isBlank())PartyChat.reply(result);
            }
        }
    }
}
