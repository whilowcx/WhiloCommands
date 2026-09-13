package dev.whilo.whilocommands;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class CooldownTest {
    @Test
    void roundsUpAndAllowsUseAtTheExactDeadline() {
        AtomicLong time = new AtomicLong(100);
        Cooldown cooldown = new Cooldown(5_000_000_000L, time::get);
        UUID player = UUID.randomUUID();
        assertEquals(0, cooldown.acquire(player));
        assertEquals(5, cooldown.acquire(player));
        time.addAndGet(4_999_999_999L);
        assertEquals(1, cooldown.acquire(player));
        time.incrementAndGet();
        assertEquals(0, cooldown.acquire(player));
    }

    @Test
    void separatesPlayersAndClearsDisconnectedPlayers() {
        Cooldown cooldown = new Cooldown(5_000_000_000L, () -> 0);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertEquals(0, cooldown.acquire(first));
        assertEquals(0, cooldown.acquire(second));
        cooldown.forget(first);
        assertEquals(0, cooldown.acquire(first));
        assertEquals(5, cooldown.acquire(second));
    }

    @Test
    void zeroCooldownAlwaysAllowsUse() {
        Cooldown cooldown = new Cooldown(0, () -> 0);
        UUID player = UUID.randomUUID();
        assertEquals(0, cooldown.acquire(player));
        assertEquals(0, cooldown.acquire(player));
    }

    @Test
    void handlesNanoTimeWraparound() {
        AtomicLong time = new AtomicLong(Long.MAX_VALUE - 10);
        Cooldown cooldown = new Cooldown(100, time::get);
        UUID player = UUID.randomUUID();
        assertEquals(0, cooldown.acquire(player));
        time.addAndGet(99);
        assertEquals(1, cooldown.acquire(player));
        time.incrementAndGet();
        assertEquals(0, cooldown.acquire(player));
    }

    @Test
    void concurrentInvocationsOnlyConsumeOneUse() throws Exception {
        Cooldown cooldown = new Cooldown(5_000_000_000L, () -> 0);
        UUID player = UUID.randomUUID();
        var threads = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var attempts = new ArrayList<Future<Long>>();
            for (int i = 0; i < 16; i++) {
                attempts.add(threads.submit(() -> {
                    start.await();
                    return cooldown.acquire(player);
                }));
            }
            start.countDown();
            int allowed = 0;
            for (Future<Long> attempt : attempts) {
                if (attempt.get(5, TimeUnit.SECONDS) == 0) {
                    allowed++;
                }
            }
            assertEquals(1, allowed);
        } finally {
            threads.shutdownNow();
        }
    }
}
