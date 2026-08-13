package dev.pvpbot.bot.combat.attack;

import dev.pvpbot.bot.combat.hitselect.HitSelectController.Decision;

import java.util.Objects;

/** Immutable record of why delayed cognition chose one intentional swing. */
public record AttackIntent(
        long sequence,
        long creationTick,
        long perceptionTick,
        Decision decision,
        Source source,
        double perceivedDistance,
        double reach,
        boolean perceivedLineOfSight,
        boolean intendedCritical
) {
    public enum Source { DECISION, WATCHDOG }

    public AttackIntent {
        if (sequence < 1) throw new IllegalArgumentException("sequence must be positive");
        decision = Objects.requireNonNull(decision, "decision");
        source = Objects.requireNonNull(source, "source");
    }
}
