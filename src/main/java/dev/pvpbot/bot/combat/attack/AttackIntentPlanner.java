package dev.pvpbot.bot.combat.attack;

import dev.pvpbot.bot.ai.perception.PerceptionSnapshot;
import dev.pvpbot.bot.combat.attack.AttackIntent.Source;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.Decision;

import java.util.Objects;
import java.util.Optional;

/** Plans one-shot attack intents without consulting current target state. */
public final class AttackIntentPlanner {
    public static final long ATTEMPT_CADENCE_TICKS = 10;
    public static final long WATCHDOG_TICKS = 30;

    private long nextSequence = 1;
    private long watchdogIntentCount;

    public Optional<AttackIntent> plan(long tick, long lastAttackAttemptTick,
                                       PerceptionSnapshot perceived, Decision decision,
                                       boolean aimEligible, double reach, boolean intendedCritical) {
        Objects.requireNonNull(perceived, "perceived");
        Objects.requireNonNull(decision, "decision");
        if (tick - lastAttackAttemptTick < ATTEMPT_CADENCE_TICKS) return Optional.empty();
        if (!Double.isFinite(reach) || reach <= 0) return Optional.empty();
        if (!perceived.lineOfSight() || perceived.distance() > reach) return Optional.empty();
        if (!aimEligible) return Optional.empty();

        Source source;
        if (isAttackDecision(decision)) {
            source = Source.DECISION;
        } else if (tick - lastAttackAttemptTick >= WATCHDOG_TICKS) {
            source = Source.WATCHDOG;
        } else {
            return Optional.empty();
        }

        AttackIntent intent = new AttackIntent(
                nextSequence++, tick, perceived.tick(), decision, source,
                perceived.distance(), reach, perceived.lineOfSight(), intendedCritical
        );
        if (source == Source.WATCHDOG) watchdogIntentCount++;
        return Optional.of(intent);
    }

    public long watchdogIntentCount() {
        return watchdogIntentCount;
    }

    private static boolean isAttackDecision(Decision decision) {
        return decision == Decision.ATTACK_NOW
                || decision == Decision.COUNTER_HIT
                || decision == Decision.CRITICAL_ATTACK;
    }
}
