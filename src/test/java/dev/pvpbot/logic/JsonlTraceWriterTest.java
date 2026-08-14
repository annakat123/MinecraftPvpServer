package dev.pvpbot.logic;

import dev.pvpbot.bot.trace.AsyncJsonlTraceWriter;
import dev.pvpbot.bot.trace.JsonlCombatTraceSink;
import dev.pvpbot.bot.trace.TraceEvents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonlTraceWriterTest {
    @TempDir Path directory;

    @Test void sharedWriterPreservesOrderAndWritesFinalDropSummary() throws Exception {
        Path file = directory.resolve("ordered.jsonl");
        AsyncJsonlTraceWriter writer = new AsyncJsonlTraceWriter(8, Logger.getAnonymousLogger());
        JsonlCombatTraceSink sink = new JsonlCombatTraceSink(writer, file);
        assertEquals(8, writer.capacity());

        sink.emit(new TraceEvents.MatchStart(1, "1.0.11", "match", 42, "QA", Map.of(), Map.of()));
        sink.emit(new TraceEvents.MatchEnd(2, "QA", 1, 0, 1, 0, 0, 0, 0));
        sink.close();
        writer.close();

        var lines = Files.readAllLines(file);
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("\"event\":\"MATCH_START\""));
        assertTrue(lines.get(1).contains("\"event\":\"MATCH_END\""));
        assertTrue(lines.get(2).contains("\"event\":\"TRACE_SUMMARY\""));
        assertTrue(lines.get(2).contains("\"droppedEvents\":0"));
    }
}
