package dev.pvpbot.qa;

import dev.pvpbot.bot.combat.attack.AttackExecutionResult;
import dev.pvpbot.bot.movement.MovementController;
import dev.pvpbot.bot.movement.VerticalAction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CombatInvariantEngine {
    public record Failure(String invariant, long tick, String state) {}

    public List<Failure> validate(List<QaFrame> frames) {
        List<Failure> failures = new ArrayList<>();
        Map<Long, Integer> executions = new HashMap<>();
        QaFrame previous = null;
        for (QaFrame frame : frames) {
            check(frame.hits() <= frame.attempts(), "botHits <= botAttempts", frame, failures);
            check(frame.reportedMisses() == Math.max(0, frame.attempts() - frame.hits()),
                    "misses == max(0, attempts - hits)", frame, failures);
            if (frame.attemptedThisTick()) {
                int count = executions.merge(frame.intentSequence(), 1, Integer::sum);
                check(count <= 1, "one AttackIntent executes at most once", frame, failures);
                check(frame.animationsThisTick() == 1,
                        "one consumed intent requests one attack animation", frame, failures);
            } else {
                check(frame.animationsThisTick() == 0,
                        "no animation without a consumed intent", frame, failures);
            }
            if (frame.executionResult() == AttackExecutionResult.WHIFF) {
                check(!frame.meleeInvokedThisTick(), "WHIFF never invokes melee damage path", frame, failures);
                check(!frame.confirmedHitThisTick(), "confirmed hit timing cannot update on a WHIFF", frame, failures);
            }
            if (frame.executionResult() == AttackExecutionResult.TARGET_INVALID) {
                check(!frame.meleeInvokedThisTick(), "TARGET_INVALID never invokes melee attack", frame, failures);
            }
            check(frame.jumpResetExecutions() <= frame.jumpResetOpportunities(),
                    "no Jump Reset without a confirmed incoming hit opportunity", frame, failures);
            check(frame.jumpResetExecutions() <= frame.jumpResetOpportunities(),
                    "at most one JRESET per opportunity", frame, failures);
            check(frame.jumpResetChanceSamples() <= frame.jumpResetOpportunities(),
                    "failed JRESET chance is not rerolled every tick", frame, failures);
            if (frame.verticalAction() == VerticalAction.JUMP_RESET) {
                check(!frame.intendedCritical(), "JRESET alone cannot mark an attack critical", frame, failures);
            }
            if (frame.knockbackLocked() && !frame.emergencyRecovery()) {
                check(!frame.movementWrite(), "normal movement does not overwrite X/Z during KB lock", frame, failures);
                check(!frame.sTapWrite(), "S-tap does not overwrite KB lock", frame, failures);
            }
            check(Double.isFinite(frame.horizontalX()) && Double.isFinite(frame.horizontalZ()),
                    "no non-finite vectors", frame, failures);
            if (!frame.externalImpulse() && !frame.emergencyRecovery()) {
                check(Math.hypot(frame.horizontalX(), frame.horizontalZ())
                                <= MovementController.MAX_HORIZONTAL_SPEED + 1.0e-9,
                        "controlled horizontal movement stays within controller limits", frame, failures);
            }
            if (previous != null) {
                if (!frame.aimUpdated()) check(frame.aimPlanPerceptionTick() == previous.aimPlanPerceptionTick(),
                        "reaction-held aim plan does not consume newer perception early", frame, failures);
                if (!frame.movementUpdated()) check(frame.movementPlanPerceptionTick() == previous.movementPlanPerceptionTick(),
                        "reaction-held movement plan does not consume newer perception early", frame, failures);
                if (frame.executionResult() == AttackExecutionResult.WHIFF) {
                    check(frame.lastSuccessfulHitTick() == previous.lastSuccessfulHitTick(),
                            "confirmed hit timing cannot update on a WHIFF", frame, failures);
                }
            }
            if (frame.watchdogIntent()) {
                check(frame.cadenceAllowed(), "watchdog cannot bypass cadence or physical validation", frame, failures);
            }
            previous = frame;
        }
        return failures;
    }

    private static void check(boolean condition, String invariant, QaFrame frame, List<Failure> failures) {
        if (!condition) failures.add(new Failure(invariant, frame.tick(), compact(frame)));
    }

    private static String compact(QaFrame frame) {
        return "attempts=" + frame.attempts() + ",hits=" + frame.hits()
                + ",intent=" + frame.intentSequence() + ",result=" + frame.executionResult()
                + ",vertical=" + frame.verticalAction() + ",kb=" + frame.knockbackLocked()
                + ",move=" + frame.movementWrite() + ",decision=" + frame.decision();
    }
}
