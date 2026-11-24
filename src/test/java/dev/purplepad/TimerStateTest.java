/* SPDX-License-Identifier: GPL-3.0-only */
package dev.purplepad;

public final class TimerStateTest {
    private static int checks;
    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        TimerState timer = new TimerState();
        timer.onMessage(TimerState.ENERGY);
        check(timer.remaining() == 0, "Must not trigger outside Storm");
        timer.onMessage(TimerState.START);
        timer.onMessage("[VIP] Player: " + TimerState.ENERGY);
        check(timer.remaining() == 0, "Must not accept player-prefixed dialogue");
        timer.onMessage(TimerState.ENERGY);
        check(timer.remaining() == 56, "Must use Devonian's 56 tick delay");
        timer.onPing(1);
        timer.onPing(0);
        check(timer.remaining() == 56, "Non-negative pings must not count");
        for (int i = 0; i < 55; i++) timer.onPing(-1);
        check(timer.remaining() == 1, "Must still show 0.05s after 55 server ticks");
        timer.onMessage(TimerState.THUNDER);
        check(timer.remaining() == 1, "Second lightning dialogue must not restart the timer");
        timer.onPing(-1);
        check(timer.remaining() == 0, "Must hide exactly after 56 server ticks");
        timer.onPing(-1);
        check(timer.remaining() == 0, "Timer must not become negative");
        timer.onMessage(TimerState.ENERGY);
        check(timer.remaining() == 0, "Must only trigger once per world/run");
        timer.reset();
        timer.onMessage(TimerState.START);
        timer.onMessage(TimerState.THUNDER);
        check(timer.remaining() == 56, "Alternative dialogue must work after a world reset");
        timer.onMessage("[BOSS] Goldor: Who dares trespass into my domain?");
        check(timer.remaining() == 0, "Phase exit must immediately clear timer");
        timer.reset();
        timer.onMessage(TimerState.START);
        timer.onMessage(TimerState.ENERGY);
        timer.reset();
        check(timer.remaining() == 0, "Disconnect/world change must clear active timer");
        System.out.println("PASS: " + checks + " timer behavior checks");
    }
}
