package dev.pvpbot.qa;

import dev.pvpbot.bot.combat.attack.AttackExecutionResult;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.Decision;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.DecisionReason;
import dev.pvpbot.bot.movement.VerticalAction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InvariantNegativeControls {
    private InvariantNegativeControls() {}

    public static Map<String, List<QaFrame>> fixtures() {
        Map<String, List<QaFrame>> fixtures = new LinkedHashMap<>();
        fixtures.put("JRESET_WITHOUT_HIT", List.of(frame(1, 0, 0, 0, false, 0,
                false, null, false, -100, 0, 1, VerticalAction.JUMP_RESET,
                false, false, false)));
        fixtures.put("MOVEMENT_WRITE_DURING_KB_LOCK", List.of(frame(1, 0, 0, 0, false, 0,
                false, null, false, -100, 0, 0, VerticalAction.INCOMING_KNOCKBACK,
                true, true, false)));
        fixtures.put("DUPLICATE_INTENT_EXECUTION", List.of(
                frame(1, 1, 0, 77, true, 1, false, AttackExecutionResult.WHIFF,
                        false, -100, 0, 0, VerticalAction.NONE, false, false, false),
                frame(2, 2, 0, 77, true, 1, false, AttackExecutionResult.WHIFF,
                        false, -100, 0, 0, VerticalAction.NONE, false, false, false)
        ));
        fixtures.put("WHIFF_COUNTED_AS_CONFIRMED_HIT", List.of(frame(1, 1, 1, 1, true, 1,
                false, AttackExecutionResult.WHIFF, true, 1, 0, 0, VerticalAction.NONE,
                false, false, false)));
        fixtures.put("TWO_ANIMATIONS_FOR_ONE_ATTEMPT", List.of(frame(1, 1, 0, 1, true, 2,
                false, AttackExecutionResult.WHIFF, false, -100, 0, 0, VerticalAction.NONE,
                false, false, false)));
        return fixtures;
    }

    public static Map<String, List<CombatInvariantEngine.Failure>> run() {
        CombatInvariantEngine engine = new CombatInvariantEngine();
        Map<String, List<CombatInvariantEngine.Failure>> results = new LinkedHashMap<>();
        fixtures().forEach((name, frames) -> results.put(name, engine.validate(frames)));
        return results;
    }

    private static QaFrame frame(long tick, int attempts, int hits, long sequence,
                                 boolean attempted, int animations, boolean melee,
                                 AttackExecutionResult result, boolean confirmed,
                                 long lastHitTick, long jumpOpportunities, long jumpExecutions,
                                 VerticalAction vertical, boolean kbLocked,
                                 boolean movementWrite, boolean sTapWrite) {
        return new QaFrame("NEGATIVE_CONTROL", tick, attempts, hits,
                Math.max(0, attempts - hits), sequence, attempted, animations, melee, result,
                confirmed, lastHitTick, jumpOpportunities, jumpOpportunities, jumpExecutions,
                0, false, vertical, kbLocked, movementWrite, sTapWrite, false, kbLocked,
                movementWrite ? .2 : 0, 0, Decision.WAIT,
                DecisionReason.COOLDOWN_DISCIPLINE_WAIT, 1, 1, 1, 1,
                true, true, true, false, true);
    }
}
