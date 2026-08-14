package dev.pvpbot.bot.trace;

import dev.pvpbot.bot.profile.BotProfile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/** Owns one-use next-duel trace requests and the shared asynchronous writer. */
public final class CombatTraceService implements AutoCloseable {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private final Path directory;
    private final Logger logger;
    private final Set<UUID> armed = new HashSet<>();
    private AsyncJsonlTraceWriter writer;

    public CombatTraceService(Path pluginDataDirectory, Logger logger) {
        directory = pluginDataDirectory.resolve("combat-traces");
        this.logger = logger;
    }

    public void arm(UUID playerId) { armed.add(playerId); }
    public void disarm(UUID playerId) { armed.remove(playerId); }
    public boolean armed(UUID playerId) { return armed.contains(playerId); }
    public boolean consume(UUID playerId) { return armed.remove(playerId); }

    public JsonlCombatTraceSink open(UUID matchId, long seed, BotProfile profile) {
        String safeProfile = profile.name().replaceAll("[^A-Za-z0-9_-]", "_").toLowerCase(Locale.ROOT);
        String file = FILE_TIME.format(LocalDateTime.now()) + "_match-" + matchId
                + "_seed-" + seed + "_profile-" + safeProfile + ".jsonl";
        return new JsonlCombatTraceSink(writer(), directory.resolve(file));
    }

    public int queueCapacity() { return writer == null ? AsyncJsonlTraceWriter.DEFAULT_CAPACITY : writer.capacity(); }
    public int queuedEvents() { return writer == null ? 0 : writer.queued(); }
    public Path directory() { return directory; }

    @Override public void close() {
        armed.clear();
        if (writer != null) writer.close();
    }

    private AsyncJsonlTraceWriter writer() {
        if (writer == null) writer = new AsyncJsonlTraceWriter(AsyncJsonlTraceWriter.DEFAULT_CAPACITY, logger);
        return writer;
    }
}
