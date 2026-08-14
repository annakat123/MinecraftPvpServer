package dev.pvpbot.bot.trace;

/** Immutable structured event emitted at an observational combat boundary. */
public interface CombatTraceEvent {
    int SCHEMA_VERSION = 1;
    long tick();
    TraceEventType event();
}
