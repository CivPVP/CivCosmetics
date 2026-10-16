package net.civmc.civcosmetics;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player cooldown bookkeeping. The caller supplies the clock so the logic is testable. */
final class Cooldowns {

    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    /** Milliseconds the player still has to wait, or 0 when the action is allowed. */
    long remainingMillis(UUID id, long nowMillis, long cooldownMillis) {
        Long last = lastUse.get(id);
        if (last == null || cooldownMillis <= 0L) return 0L;
        long elapsed = nowMillis - last;
        if (elapsed < 0L) return cooldownMillis; // clock went backwards: be strict
        return Math.max(0L, cooldownMillis - elapsed);
    }

    void mark(UUID id, long nowMillis) {
        lastUse.put(id, nowMillis);
    }

    void clear(UUID id) {
        lastUse.remove(id);
    }
}
