package dev.pvpbot.bot.trace;

public interface CombatTraceSink extends AutoCloseable {
    boolean enabled();
    void emit(CombatTraceEvent event);
    long droppedEvents();
    @Override void close();
}
