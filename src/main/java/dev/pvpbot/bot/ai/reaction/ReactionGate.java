package dev.pvpbot.bot.ai.reaction;

import java.util.random.RandomGenerator;

/**
 * Tick-based scheduled reaction gate. Jitter is drawn once after a completed
 * update; the final non-negative millisecond interval is rounded up to nominal
 * 20 TPS ticks (50 ms), with at least the next server tick between updates.
 */
public final class ReactionGate {
    public static final int NOMINAL_TICK_MS = 50;

    private long nextUpdateTick = Long.MIN_VALUE;
    private long lastUpdateTick = -1;
    private int scheduledIntervalMs;
    private int scheduledIntervalTicks;

    public boolean ready(long currentTick) {
        return currentTick >= nextUpdateTick;
    }

    public void scheduleNext(long currentTick, int baseMs, int jitterMs, RandomGenerator random) {
        int safeBase = Math.max(0, baseMs);
        int safeJitter = Math.max(0, jitterMs);
        int offset = safeJitter == 0 ? 0 : random.nextInt(safeJitter * 2 + 1) - safeJitter;
        scheduledIntervalMs = Math.max(0, safeBase + offset);
        scheduledIntervalTicks = millisecondsToTicks(scheduledIntervalMs);
        lastUpdateTick = currentTick;
        nextUpdateTick = saturatingAdd(currentTick, scheduledIntervalTicks);
    }

    public static int millisecondsToTicks(int intervalMs) {
        long safeMs = Math.max(0L, intervalMs);
        return (int) Math.max(1L, (safeMs + NOMINAL_TICK_MS - 1L) / NOMINAL_TICK_MS);
    }

    public long ageTicks(long currentTick) {
        return lastUpdateTick < 0 ? 0 : Math.max(0, currentTick - lastUpdateTick);
    }

    public long ticksUntilReady(long currentTick) {
        return nextUpdateTick == Long.MIN_VALUE ? 0 : Math.max(0, nextUpdateTick - currentTick);
    }

    public int scheduledIntervalMs() {
        return scheduledIntervalMs;
    }

    public int scheduledIntervalTicks() {
        return scheduledIntervalTicks;
    }

    private static long saturatingAdd(long value, int increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }
}
