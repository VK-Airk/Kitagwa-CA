/* SPDX-License-Identifier: GPL-3.0-only */
package dev.purplepad;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TimerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public boolean enabled = true;
    // GUI-scaled pixels; x = -1 centers the timer horizontally.
    public int x = -1;
    public int y = 58;
    public float scale = 2.34f;
    public boolean shadow = true;
    public boolean simpleMode = true;
    @com.google.gson.annotations.SerializedName(value="kcaBackdrop", alternate={"madoBackdrop"})
    public boolean kcaBackdrop;

    HudStyle style() { return simpleMode ? HudStyle.SIMPLE : kcaBackdrop ? HudStyle.KCA : HudStyle.FANCY; }
    void setStyle(HudStyle style) {
        simpleMode = style == HudStyle.SIMPLE;
        kcaBackdrop = style == HudStyle.KCA;
    }

    public TimerConfig copy() {
        TimerConfig copy = new TimerConfig();
        copy.enabled = enabled;
        copy.x = x;
        copy.y = y;
        copy.scale = scale;
        copy.shadow = shadow;
        copy.simpleMode = simpleMode;
        copy.kcaBackdrop = kcaBackdrop;
        return copy;
    }

    public static TimerConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            TimerConfig result = new TimerConfig();
            result.save(path);
            return result;
        }
        TimerConfig result = GSON.fromJson(Files.readString(path), TimerConfig.class);
        if (result == null || result.x < -1 || result.y < 0 || !Float.isFinite(result.scale)
                || result.scale < 0.5f || result.scale > 6f) {
            throw new IllegalArgumentException("Invalid Purple Pad Timer position or scale");
        }
        return result;
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(this) + System.lineSeparator());
    }
}
