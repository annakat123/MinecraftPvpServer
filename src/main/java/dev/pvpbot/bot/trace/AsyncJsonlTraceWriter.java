package dev.pvpbot.bot.trace;

import dev.pvpbot.bot.trace.TraceEvents.TraceSummary;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/** One ordered bounded queue and one writer thread shared by all live traces. */
public final class AsyncJsonlTraceWriter implements AutoCloseable {
    public static final int DEFAULT_CAPACITY = 4096;
    private sealed interface Item permits Line, Finish {}
    private record Line(Path path, String json) implements Item {}
    private record Finish(Path path, long tick, AtomicLong dropped) implements Item {}

    private final ArrayBlockingQueue<Item> queue;
    private final ConcurrentLinkedQueue<Finish> deferredFinishes = new ConcurrentLinkedQueue<>();
    private final Logger logger;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread worker;

    public AsyncJsonlTraceWriter(int capacity, Logger logger) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.logger = logger;
        worker = new Thread(this::run, "pvpbot-combat-trace-writer");
        worker.setDaemon(true);
        worker.start();
    }

    boolean offer(Path path, String json) {
        return running.get() && queue.offer(new Line(path, json));
    }

    void finish(Path path, long tick, AtomicLong dropped) {
        Finish finish = new Finish(path, tick, dropped);
        if (!running.get() || !queue.offer(finish)) deferredFinishes.add(finish);
    }

    public int capacity() { return queue.size() + queue.remainingCapacity(); }
    public int queued() { return queue.size(); }

    private void run() {
        Map<Path, BufferedWriter> writers = new HashMap<>();
        try {
            while (running.get() || !queue.isEmpty()) {
                Item item = queue.poll(250, TimeUnit.MILLISECONDS);
                if (item != null) process(item, writers);
                if (queue.isEmpty()) drainDeferredFinishes(writers);
            }
            drainDeferredFinishes(writers);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            writers.forEach((path, writer) -> closeWriter(path, writer));
        }
    }

    private void process(Item item, Map<Path, BufferedWriter> writers) {
        try {
            if (item instanceof Line line) {
                BufferedWriter writer = writers.get(line.path());
                if (writer == null) {
                    Files.createDirectories(line.path().getParent());
                    writer = Files.newBufferedWriter(line.path(), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                    writers.put(line.path(), writer);
                }
                writer.write(line.json());
                writer.newLine();
            } else if (item instanceof Finish finish) {
                finish(finish, writers);
            }
        } catch (IOException error) {
            logger.log(Level.SEVERE, "Combat trace write failed for " + pathOf(item), error);
        }
    }

    private void finish(Finish finish, Map<Path, BufferedWriter> writers) throws IOException {
        BufferedWriter writer = writers.get(finish.path());
        if (writer == null) {
            Files.createDirectories(finish.path().getParent());
            writer = Files.newBufferedWriter(finish.path(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        writer.write(TraceJson.encode(new TraceSummary(finish.tick(), finish.dropped().get())));
        writer.newLine();
        writer.flush();
        writer.close();
        writers.remove(finish.path());
    }

    private void drainDeferredFinishes(Map<Path, BufferedWriter> writers) {
        Finish finish;
        while ((finish = deferredFinishes.poll()) != null) process(finish, writers);
    }

    private static Path pathOf(Item item) {
        return item instanceof Line line ? line.path() : ((Finish) item).path();
    }

    private void closeWriter(Path path, BufferedWriter writer) {
        try {
            writer.flush();
            writer.close();
        } catch (IOException error) {
            logger.log(Level.WARNING, "Could not close combat trace " + path, error);
        }
    }

    @Override public void close() {
        if (!running.compareAndSet(true, false)) return;
        try {
            worker.join(5000);
            if (worker.isAlive()) {
                worker.interrupt();
                worker.join(1000);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
