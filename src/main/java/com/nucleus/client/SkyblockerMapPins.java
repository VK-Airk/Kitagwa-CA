package com.nucleus.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import com.nucleus.NucleusMod;

/** Optional, read-only view of the current lobby's Skyblocker waypoints. */
final class SkyblockerMapPins {
    private record Access(Field active, Field position, Method category) {}
    private static Access access = findAccess();

    private static Access findAccess(){
        if(!FabricLoader.getInstance().isModLoaded("skyblocker"))return null;
        try{
            String pkg="de.hysky.skyblocker.skyblock.dwarven.";
            ClassLoader loader=SkyblockerMapPins.class.getClassLoader();
            Class<?> manager=Class.forName(pkg+"CrystalsLocationsManager",false,loader);
            Class<?> label=Class.forName(pkg+"MiningLocationLabel",false,loader);
            Field active=manager.getDeclaredField("activeWaypoints");
            if(!active.trySetAccessible())return null;
            return new Access(active,label.getField("pos"),label.getMethod("category"));
        }catch(ReflectiveOperationException | LinkageError e){
            NucleusMod.LOGGER.debug("Skyblocker map pins unavailable",e);
            return null;
        }
    }

    static void read(List<HollowsMap.Pin> pins){
        if(access==null)return;
        try{
            if(!(access.active.get(null) instanceof Map<?,?> waypoints))return;
            for(Object label:waypoints.values()){
                if(!(access.category.invoke(label) instanceof Enum<?> category))continue;
                HollowsMap.Place place;
                if("UNKNOWN".equals(category.name()))continue;
                try{place=HollowsMap.Place.valueOf(category.name());}
                catch(IllegalArgumentException unknownCategory){continue;}
                if(access.position.get(label) instanceof BlockPos pos && HollowsMap.inside(pos.getX(),pos.getZ()))
                    pins.add(new HollowsMap.Pin(place,pos.getX(),pos.getY(),pos.getZ()));
            }
        }catch(ReflectiveOperationException | LinkageError e){
            NucleusMod.LOGGER.debug("Skyblocker map pins unavailable",e);
            access=null;
        }
    }
    private SkyblockerMapPins(){}
}
