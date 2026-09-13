package dev.whilo.whilocommands;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class Cooldown {
    private final long duration;
    private final LongSupplier clock;
    private final Map<UUID, Long> lastUse = new HashMap<>();

    public Cooldown(long duration) {
        this(duration, System::nanoTime);
    }

    Cooldown(long duration, LongSupplier clock) {
        this.duration = duration;
        this.clock = clock;
    }

    public synchronized long acquire(UUID player) {
        if (duration == 0) {
            return 0;
        }
        long now = clock.getAsLong();
        Long previous = lastUse.get(player);
        if (previous != null) {
            long remaining = duration - (now - previous);
            if (remaining > 0) {
                return 1 + (remaining - 1) / 1_000_000_000L;
            }
        }
        lastUse.put(player, now);
        return 0;
    }

    public synchronized void forget(UUID player) {
        lastUse.remove(player);
    }
}
