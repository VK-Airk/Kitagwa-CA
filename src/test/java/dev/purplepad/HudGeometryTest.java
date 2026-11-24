/* SPDX-License-Identifier: GPL-3.0-only */
package dev.purplepad;

public final class HudGeometryTest {
    private static int checks;
    private static void near(float actual, float expected, String message) {
        checks++;
        if (Math.abs(actual - expected) > 0.001f) throw new AssertionError(message + ": " + actual);
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var centered = HudGeometry.bounds(-1, 58, 2f, 30, 9, 320, 240);
        near(centered.x(), 130, "Centered timer must account for its scaled width");
        near(centered.y(), 58, "Top coordinate must be retained");
        var edges = HudGeometry.bounds(500, 500, 6f, 40, 17, 320, 240);
        near(edges.x(), 80, "Right edge must keep the full pixel card visible");
        near(edges.y(), 138, "Bottom edge must keep the full pixel card visible");
        near(HudGeometry.clampPosition(-30, 320, 60), 0, "Dragging left must stop at the edge");
        near(HudGeometry.clampPosition(30, 20, 60), 0, "Oversized element must not get negative position");
        near(HudGeometry.resizeScale(2, 30, 9, 30, 9), 3, "Diagonal drag must preserve aspect ratio");
        near(HudGeometry.resizeScale(2, -3000, -900, 30, 9), 0.5f, "Shrink must stop at minimum size");
        near(HudGeometry.resizeScale(2, 3000, 900, 30, 9), 6, "Enlarge must stop at maximum size");
        check(centered.contains(130, 58), "Top-left corner should be selectable");
        check(!centered.contains(129, 58), "Area outside timer should not select it");
        for (float scale : new float[]{0.5f, 1, 2.34f, 6}) {
            for (int x : new int[]{-1, 0, 100, 10000}) {
                var b = HudGeometry.bounds(x, 10000, scale, 40, 17, 320, 240);
                check(b.x() >= 0 && b.y() >= 0 && b.x() + b.width() <= 320.001f
                    && b.y() + b.height() <= 240.001f, "All supported scales must remain onscreen");
            }
        }
        System.out.println("PASS: " + checks + " HUD geometry checks");
    }
}
