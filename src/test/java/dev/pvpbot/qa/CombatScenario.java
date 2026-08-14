package dev.pvpbot.qa;

import dev.pvpbot.bot.combat.attack.AttackExecutionResult;
import dev.pvpbot.bot.profile.BotProfile;

import java.util.List;

public record CombatScenario(
        String name,
        long matchSeed,
        BotProfile profile,
        List<TickInput> ticks,
        Expectation expectation
) {
    public record TickInput(
            double distance,
            double closingSpeed,
            double forwardVelocity,
            double lateralVelocity,
            double targetVerticalVelocity,
            double botVerticalVelocity,
            boolean botGrounded,
            boolean targetGrounded,
            boolean lineOfSight,
            int incomingCombo,
            int outgoingCombo,
            boolean incomingHit,
            boolean knockback,
            boolean forceAimEligible,
            boolean arenaEdgeRecovery,
            AttackExecutionResult executionResult,
            boolean confirmDamage
    ) {
        public TickInput {
            if (!Double.isFinite(distance) || distance < 0 || distance > 8) {
                throw new IllegalArgumentException("distance outside QA bounds");
            }
            if (!Double.isFinite(closingSpeed) || !Double.isFinite(forwardVelocity)
                    || !Double.isFinite(lateralVelocity) || !Double.isFinite(targetVerticalVelocity)
                    || !Double.isFinite(botVerticalVelocity)) {
                throw new IllegalArgumentException("non-finite QA input");
            }
        }

        public static TickInput stable(double distance) {
            return new TickInput(distance, 0, 0, 0, 0, 0, true, true, true,
                    0, 0, false, false, true, false, AttackExecutionResult.CONTACT, false);
        }

        public TickInput withExecution(AttackExecutionResult result, boolean damage) {
            return new TickInput(distance, closingSpeed, forwardVelocity, lateralVelocity,
                    targetVerticalVelocity, botVerticalVelocity, botGrounded, targetGrounded,
                    lineOfSight, incomingCombo, outgoingCombo, incomingHit, knockback,
                    forceAimEligible, arenaEdgeRecovery, result, damage);
        }
    }

    @FunctionalInterface
    public interface Expectation {
        List<String> validate(CombatScenarioRunner.Result result);
    }
}
