package dev.purplepad;

import java.util.Optional;

/** One lifetime-counter source. Only paired samples from the same source form a run delta. */
final class SecretSamples {
    private long start = -1, latest = -1;
    private boolean receivedEnd;

    void record(long total, int query, boolean end) {
        if (query == 1 && !end) start = total;
        latest = total;
        receivedEnd |= end;
    }

    Optional<Long> delta(boolean finished) {
        if (start < 0 || latest < start || (finished && !receivedEnd)) return Optional.empty();
        return Optional.of(latest - start);
    }
}
