package com.nucleus.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.nucleus.NucleusMod;

/**
 * Renders waypoints as simple block highlights that are
 * visible through walls (Gizmos with alwaysOnTop).
 * Text labels are optional via config.
 */
public final class NucleusWaypointRenderer {
	private NucleusWaypointRenderer() {
	}

	public static void register() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> render());
	}

	private static void render() {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		if (!NucleusMod.CONFIG.kcaEnabled) {
			return;
		}
		// Cinematic: the jackpot owns the screen, waypoints stand down.
		if (JackpotAnimation.cinematicActive()) {
			return;
		}
		boolean hasMain = NucleusWaypoints.hasWaypoints();
		boolean hasCustom = NucleusWaypoints.hasCustom();
		if (!hasMain && !hasCustom) {
			return;
		}
		boolean throughWalls = NucleusMod.CONFIG.waypointsThroughWalls;
		boolean text = NucleusMod.CONFIG.waypointText;
		float outlineWidth = NucleusMod.CONFIG.waypointOutlineWidth;
		if (outlineWidth < 0.5f) {
			outlineWidth = 0.5f;
		} else if (outlineWidth > 10.0f) {
			outlineWidth = 10.0f;
		}

		Vec3 eye = client.player.getEyePosition();
		var snapshot=NucleusWaypoints.renderSnapshot();
		for (var wp : snapshot.all()) {
			if (!NucleusWaypoints.visibleFor(wp.index())) {
				continue;
			}
			// Etherwarp reaches 61 blocks: anything further renders dimmed.
			double dist = eye.distanceTo(Vec3.atCenterOf(wp.pos()));
			int color = NucleusWaypoints.colorFor(wp.index());
			if (NucleusMod.CONFIG.waypointDimBeyondEtherwarp && dist > 61.0) {
				color = dimColor(color, 0.35f);
			}
			int fill = withAlpha(color, 0x55);
			try {
				// Simple block highlight: outlined + slightly filled 1-block box.
				AABB box = new AABB(wp.pos()).inflate(0.02);
				var boxProps = Gizmos.cuboid(box, GizmoStyle.strokeAndFill(color, outlineWidth, fill));
				if (throughWalls) {
					boxProps.setAlwaysOnTop();
				}

				if (text) {
					String label = wp.label() + " §7(" + (int) dist + "m)";
					var textProps = Gizmos.billboardTextOverBlock(label, wp.pos(), 1, color, 1.0f);
					if (throughWalls) {
						textProps.setAlwaysOnTop();
					}
				}
			} catch (Exception e) {
				// Gizmos are only valid during level rendering; never crash the frame.
				NucleusMod.LOGGER.debug("Waypoint gizmo failed: {}", e.getMessage());
				return;
			}
		}

		// Jungle-only polylines WP1 -> WP2 -> WP3 (never custom waypoints).
		if (NucleusMod.CONFIG.templeLinkLines) {
			try {
				NucleusWaypoints.Waypoint previous=null;
                for(var b:snapshot.temple()){
                    if(!NucleusWaypoints.visibleFor(b.index()))continue;
                    var a=previous;previous=b;
                    if(a==null)continue;
                    int color = NucleusWaypoints.colorFor(a.index());
					if (NucleusMod.CONFIG.waypointDimBeyondEtherwarp
						&& eye.distanceTo(Vec3.atCenterOf(a.pos())) > 61.0
						&& eye.distanceTo(Vec3.atCenterOf(b.pos())) > 61.0) {
						color = dimColor(color, 0.35f);
					}
					var lineProps = Gizmos.line(
						Vec3.atCenterOf(a.pos()), Vec3.atCenterOf(b.pos()), color);
					if (throughWalls) {
						lineProps.setAlwaysOnTop();
					}
				}
			} catch (Exception e) {
				NucleusMod.LOGGER.debug("Waypoint link gizmo failed: {}", e.getMessage());
			}
		}
	}

	private static int withAlpha(int argb, int alpha) {
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}

	/** Keeps alpha, scales RGB (for out-of-etherwarp-range dimming). */
	static int dimColor(int argb, float f) {
		int a = (argb >>> 24) & 0xFF;
		int r = (int) (((argb >> 16) & 0xFF) * f);
		int g = (int) (((argb >> 8) & 0xFF) * f);
		int b = (int) ((argb & 0xFF) * f);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
