/* SPDX-License-Identifier: GPL-3.0-only
 * Timing adapted from Synnerz/devonian 1.34.9, PurplePadTimer.kt.
 */
package dev.purplepad;

public final class TimerState {
    public static final int DURATION = 56;
    public static final String START = "[BOSS] Storm: Pathetic Maxor, just like expected.";
    public static final String ENERGY = "[BOSS] Storm: ENERGY HEED MY CALL!";
    public static final String THUNDER = "[BOSS] Storm: THUNDER LET ME BE YOUR CATALYST!";
    private boolean storm;
    private boolean triggered;
    private int remaining;

    public void onMessage(String message) {
        if (START.equals(message)) {
            storm = true;
        } else if (message.startsWith("[BOSS] Goldor:")
                || message.equals("[BOSS] Storm: I should have known that I stood no chance.")
                || message.startsWith("[BOSS] Necron:")) {
            storm = false;
            remaining = 0;
        } else if (storm && !triggered && (ENERGY.equals(message) || THUNDER.equals(message))) {
            triggered = true;
            remaining = DURATION;
        }
    }

    public void onPing(int id) {
        if (id < 0 && remaining > 0) remaining--;
    }

    public int remaining() { return remaining; }

    public void reset() {
        storm = false;
        triggered = false;
        remaining = 0;
    }
}
