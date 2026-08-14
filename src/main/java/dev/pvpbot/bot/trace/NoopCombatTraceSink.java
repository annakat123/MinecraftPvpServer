package dev.pvpbot.bot.trace;

public enum NoopCombatTraceSink implements CombatTraceSink {
    INSTANCE;
    @Override public boolean enabled() { return false; }
    @Override public void emit(CombatTraceEvent event) {}
    @Override public long droppedEvents() { return 0; }
    @Override public void close() {}
}
