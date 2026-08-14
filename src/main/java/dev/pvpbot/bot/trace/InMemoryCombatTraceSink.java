package dev.pvpbot.bot.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InMemoryCombatTraceSink implements CombatTraceSink {
    private final List<CombatTraceEvent> events = new ArrayList<>();
    @Override public boolean enabled() { return true; }
    @Override public void emit(CombatTraceEvent event) { events.add(event); }
    @Override public long droppedEvents() { return 0; }
    @Override public void close() {}
    public List<CombatTraceEvent> events() { return Collections.unmodifiableList(events); }
    public List<CombatTraceEvent> recent(int maximum) {
        int from = Math.max(0, events.size() - Math.max(0, maximum));
        return List.copyOf(events.subList(from, events.size()));
    }
}
