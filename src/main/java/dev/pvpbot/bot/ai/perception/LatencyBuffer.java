package dev.pvpbot.bot.ai.perception;
import java.util.*;
public final class LatencyBuffer<T> {
    private record Entry<T>(long availableAt, T value) {}
    private final Deque<Entry<T>> queue = new ArrayDeque<>(); private T latest;
    public void offer(long nowMs, long delayMs, T value) { queue.addLast(new Entry<>(nowMs + Math.max(0, delayMs), value)); }
    public Optional<T> poll(long nowMs) { while (!queue.isEmpty() && queue.peekFirst().availableAt <= nowMs) latest = queue.removeFirst().value; return Optional.ofNullable(latest); }
    public void clear() { queue.clear(); latest = null; }
    public int pending() { return queue.size(); }
}
