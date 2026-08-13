package dev.pvpbot.bot.combat.attack;

/** Physical execution result only; successful damage remains event-confirmed. */
public enum AttackExecutionResult {
    CONTACT,
    WHIFF,
    TARGET_INVALID,
    ALREADY_CONSUMED
}
