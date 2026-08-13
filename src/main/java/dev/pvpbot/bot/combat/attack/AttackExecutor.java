package dev.pvpbot.bot.combat.attack;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Consumes each intent once and keeps physical contact separate from damage confirmation. */
public final class AttackExecutor {
    public interface Runtime {
        void recordAttempt(AttackIntent intent);
        void swingMainHand();
        AttackExecutionResult probePhysicalContact();
        void attackTarget(AttackIntent intent);
    }

    public record Outcome(AttackExecutionResult result, boolean attempted, boolean attackInvoked) {}

    private final Set<Long> consumedSequences = new HashSet<>();

    public Outcome execute(AttackIntent intent, Runtime runtime) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(runtime, "runtime");
        if (!consumedSequences.add(intent.sequence())) {
            return new Outcome(AttackExecutionResult.ALREADY_CONSUMED, false, false);
        }

        runtime.recordAttempt(intent);
        runtime.swingMainHand();
        AttackExecutionResult result = Objects.requireNonNull(
                runtime.probePhysicalContact(), "physical contact result"
        );
        if (result == AttackExecutionResult.CONTACT) {
            runtime.attackTarget(intent);
            return new Outcome(result, true, true);
        }
        return new Outcome(result, true, false);
    }
}
