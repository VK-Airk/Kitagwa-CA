/* SPDX-License-Identifier: GPL-3.0-only */
package dev.purplepad;

/** GUI-pixel geometry shared by the live timer and the layout editor. */
public final class HudGeometry {
    private HudGeometry() { }

    public record Bounds(float x, float y, float width, float height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseY >= y && mouseX <= x + width && mouseY <= y + height;
        }
    }

    public static Bounds bounds(float x, float y, float scale, float baseWidth, float baseHeight,
                                int screenWidth, int screenHeight) {
        float width = baseWidth * scale;
        float height = baseHeight * scale;
        float actualX = x < 0 ? (screenWidth - width) / 2 : x;
        return new Bounds(clampPosition(actualX, screenWidth, width),
            clampPosition(y, screenHeight, height), width, height);
    }

    public static float clampPosition(float value, float screenSize, float objectSize) {
        return Math.clamp(value, 0, Math.max(0, screenSize - objectSize));
    }

    public static float resizeScale(float initialScale, double dx, double dy, float baseWidth, float baseHeight) {
        // Project the mouse movement onto the bottom-right corner's aspect-ratio direction.
        double delta = (dx * baseWidth + dy * baseHeight) / (baseWidth * baseWidth + baseHeight * baseHeight);
        return clampScale(initialScale + (float) delta);
    }

    public static float clampScale(float scale) { return Math.clamp(scale, 0.5f, 6f); }
}
