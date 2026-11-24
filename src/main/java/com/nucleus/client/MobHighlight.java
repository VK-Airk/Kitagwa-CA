package com.nucleus.client;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.nucleus.NucleusMod;

/**
 * Important-mob discovery: the first time a watched NPC (Yolkar, Professor
 * Robot, the four Keepers, both Door Guardians) is seen, a player-sized box
 * (0.6 x 1.8) is stored at its feet and filled from then on — even when the
 * mob itself is unloaded. Markers clear on lobby switch.
 *
 * <p>Hypixel rarely puts the name on the NPC body itself: nameplates usually
 * come from hologram armor stands at the NPC's spot or scoreboard-team
 * display names. So every candidate is matched against BOTH its custom name
 * and its display name, and a 1s sweep re-scans all loaded entities (catches
 * NPCs that were already around before the toggle was flipped, plus anything
 * the load event missed). Each marker records exactly once — never rewritten,
 * so boxes can't drift or vanish — and the sweep stops once all are found.
 */
public final class MobHighlight {
	private MobHighlight() {
	}

	/** Player hitbox: 0.6 wide, 1.8 tall. */
	private static final float BOX_W = 0.6f;
	private static final float BOX_H = 1.8f;

	private record Watch(String id, String match) {
	}

	private static final Watch[] WATCH = {
		new Watch("yolkar", "king yolkar"),
		new Watch("robot", "professor robot"),
		new Watch("keeper_lapis", "keeper of lapis"),
		new Watch("keeper_gold", "keeper of gold"),
		new Watch("keeper_diamond", "keeper of diamond"),
		new Watch("keeper_emerald", "keeper of emerald"),
		new Watch("door", "door guardian")
	};

    private static final IntArrayList pending=new IntArrayList(),guardians=new IntArrayList();
    private static Level scanLevel;
    private static int cursor,cooldown;
    private static boolean seekTemple;

    static void reset(){
        pending.clear();guardians.clear();cursor=cooldown=0;scanLevel=null;
    }

	public static void onEntityLoad(Entity entity, Level level) {
		if (watching() && entity!=null && !(entity instanceof AbstractClientPlayer)) {
			tryRecord(entity,namesOf(entity));
		}
	}

	private static boolean watching() {
		return NucleusMod.CONFIG.mobHighlightEnabled && HollowsDetector.isInCrystalHollows()
			&& !MobMarkers.complete();
	}

    /** Snapshot IDs so loading and unloading entities between slices never invalidates an iterator. */
    public static void tick(Minecraft client){
        boolean highlights=watching(),temple=TempleAutoPlace.watching();
        if(scanLevel!=null && client.level!=scanLevel)reset();
        if(client.level==null || client.player==null || (!highlights&&!temple)){
            if(scanLevel!=null)reset();
            return;
        }
        scanLevel=client.level;
        if(cooldown>0)cooldown--;
        if(pending.isEmpty()){
            if(cooldown>0)return;
            cooldown=20;cursor=0;guardians.clear();seekTemple=temple;
            for(Entity entity:client.level.entitiesForRendering())
                if(entity!=null && !(entity instanceof AbstractClientPlayer))pending.add(entity.getId());
        }
        int limit=Math.min(pending.size(),cursor+32);
        long start=System.nanoTime();
        while(cursor<limit){
            Entity entity=client.level.getEntity(pending.getInt(cursor++));
            if(entity!=null){
                String names=namesOf(entity);
                if(highlights)tryRecord(entity,names);
                if(seekTemple&&temple&&names.contains("door guardian"))guardians.add(entity.getId());
            }
            if(System.nanoTime()-start>=500_000)break;
        }
        if(cursor==pending.size()){
            if(temple&&seekTemple)TempleAutoPlace.finish(client,guardians);
            pending.clear();guardians.clear();
        }
    }

    private static void tryRecord(Entity entity,String hay) {
        try {
			if (hay.isBlank()) {
				return;
			}
			// Both Door Guardians: same name, two ids — the second records
			// only when it's well away from the first (opposite door sides).
			if (hay.contains("door guardian")) {
				if (!MobMarkers.has("door_1")) {
					record("door_1", entity);
				} else if (!MobMarkers.has("door_2")) {
					Vec3 c = MobMarkers.get("door_1").getCenter();
					Vec3 p = entity.position();
					if ((p.x - c.x) * (p.x - c.x) + (p.z - c.z) * (p.z - c.z) > 16.0) {
						record("door_2", entity);
					}
				}
				return;
			}
			Watch hit = null;
			for (Watch w : WATCH) {
				if (hay.contains(w.match())) {
					hit = w;
					break;
				}
			}
			if (hit == null) {
				return;
			}
			// Recorded once, never rewritten: exact markers are final.
			if (!MobMarkers.has(hit.id())) {
				record(hit.id(), entity);
			}
		} catch (Exception ignored) {
		}
	}

	private static void record(String id, Entity entity) {
		Vec3 feet = entity.position();
		AABB box = new AABB(
			feet.x - BOX_W / 2.0, feet.y, feet.z - BOX_W / 2.0,
			feet.x + BOX_W / 2.0, feet.y + BOX_H, feet.z + BOX_W / 2.0);
		MobMarkers.put(id, box);
		NucleusMod.LOGGER.info("Mob marker recorded: {} at {} ({})",
			id, box, entity.getClass().getSimpleName());
	}

	/** Custom name + display name (team formatting included), cleaned for matching. Shared NPC matcher. */
	static String namesOf(Entity entity) {
		StringBuilder hay = new StringBuilder();
		try {
			if (entity.getCustomName() != null) {
				hay.append(HollowsDetector.stripFormatting(
					entity.getCustomName().getString()).toLowerCase().trim()).append('\n');
			}
		} catch (Exception ignored) {
		}
		try {
			Component display = entity.getDisplayName();
			if (display != null) {
				hay.append(HollowsDetector.stripFormatting(
					display.getString()).toLowerCase().trim());
			}
		} catch (Exception ignored) {
		}
		return hay.toString();
	}
}
