package com.nucleus.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.nucleus.NucleusMod;

/**
 * Automatic temple waypoints. Watches for BOTH Kalhuiki Door Guardians using
 * the same name matching as the mob boxes, but completely independent of the
 * "Highlight NPC's" toggle. Only when both are loaded does it place the 3
 * waypoints from the feet of whichever guardian sits higher on X. Runs at
 * most once per lobby (manual placements also count — it never overwrites).
 */
public final class TempleAutoPlace {
	private TempleAutoPlace() {
	}

    static boolean watching(){
        return NucleusMod.CONFIG.kcaEnabled && NucleusMod.CONFIG.templeAutoPlace
            && HollowsDetector.isInCrystalHollows() && !NucleusWaypoints.hasWaypoints();
    }

    static void finish(Minecraft client,it.unimi.dsi.fastutil.ints.IntArrayList guardians){
        if(guardians.size()<2 || !watching())return;
        List<Vec3> found=new ArrayList<>();
        for(int i=0;i<guardians.size();i++){
            Entity entity=client.level.getEntity(guardians.getInt(i));
            if(entity==null || !MobHighlight.namesOf(entity).contains("door guardian"))continue;
            Vec3 p=entity.position();
            boolean duplicate=false;
            for(Vec3 q:found){
                double dx=p.x-q.x,dz=p.z-q.z;
                if(dx*dx+dz*dz<16.0){duplicate=true;break;}
            }
            if(!duplicate)found.add(p);
        }
		if (found.size() < 2) {
			return;
		}
		Vec3 anchor = found.get(0);
		for (Vec3 p : found) {
			if (p.x > anchor.x) {
				anchor = p;
			}
		}
		BlockPos feet = new BlockPos(
			(int) Math.floor(anchor.x), (int) Math.floor(anchor.y), (int) Math.floor(anchor.z));
		NucleusWaypoints.setFromOrigin(feet);
		NucleusLobbyWatcher.onCapture();
		NucleusMod.LOGGER.info("Temple waypoints auto-placed from {}", feet);
		KcaChat.chat(client, Component.literal("§d[KCA] §fTemple waypoints placed automatically."));
	}
}
