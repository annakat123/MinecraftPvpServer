package dev.pvpbot.bot.trace;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class JsonlCombatTraceSink implements CombatTraceSink {
    private final AsyncJsonlTraceWriter writer;
    private final Path path;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicBoolean open = new AtomicBoolean(true);
    private volatile long lastTick;

    public JsonlCombatTraceSink(AsyncJsonlTraceWriter writer, Path path) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.path = Objects.requireNonNull(path, "path");
    }

    @Override public boolean enabled() { return open.get(); }

    @Override public void emit(CombatTraceEvent event) {
        if (!open.get()) return;
        lastTick = event.tick();
        if (!writer.offer(path, TraceJson.encode(event))) dropped.incrementAndGet();
    }

    @Override public long droppedEvents() { return dropped.get(); }
    public Path path() { return path; }

    @Override public void close() {
        if (open.compareAndSet(true, false)) writer.finish(path, lastTick, dropped);
    }
}
