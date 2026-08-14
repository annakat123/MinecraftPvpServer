package dev.pvpbot.qa;

import dev.pvpbot.bot.combat.attack.AttackExecutionResult;
import dev.pvpbot.bot.movement.VerticalAction;
import dev.pvpbot.bot.profile.BotProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

public final class CombatScenarios {
    private CombatScenarios() {}

    public static List<CombatScenario> scripted() {
        return List.of(
                highPingSideStep(),
                incomingKnockback(),
                jumpResetValid(),
                jumpResetInvalid(),
                criticalSetup(),
                reactionHold(),
                watchdogAttack(),
                contactWithoutDamage(),
                arenaEdgeRecovery()
        );
    }

    public static CombatScenario fuzz(long qaSeed, int ticks) {
        SplittableRandom qaRandom = new SplittableRandom(qaSeed);
        long matchSeed = qaRandom.nextLong();
        int ping = List.of(0, 50, 100, 150, 300, 500).get(qaRandom.nextInt(6));
        int decisionMs = qaRandom.nextInt(0, 501);
        int aimMs = qaRandom.nextInt(0, 501);
        int movementMs = qaRandom.nextInt(0, 501);
        Map<String, Double> values = Map.ofEntries(
                Map.entry("simulatedPingMs", (double) ping),
                Map.entry("reaction.decisionMs", (double) decisionMs),
                Map.entry("reaction.aimMs", (double) aimMs),
                Map.entry("reaction.movementMs", (double) movementMs),
                Map.entry("reaction.decisionJitterMs", (double) qaRandom.nextInt(0, 201)),
                Map.entry("reaction.aimJitterMs", (double) qaRandom.nextInt(0, 201)),
                Map.entry("reaction.movementJitterMs", (double) qaRandom.nextInt(0, 201)),
                Map.entry("reach.blocks", qaRandom.nextDouble(2.0, 6.0)),
                Map.entry("hitSelect.skill", qaRandom.nextDouble()),
                Map.entry("hitSelect.chance", qaRandom.nextDouble()),
                Map.entry("hitSelect.patience", qaRandom.nextDouble()),
                Map.entry("hitSelect.counterHitPreference", qaRandom.nextDouble()),
                Map.entry("hitSelect.cooldownDiscipline", qaRandom.nextDouble()),
                Map.entry("hitSelect.baitPreference", qaRandom.nextDouble()),
                Map.entry("criticals.skill", qaRandom.nextDouble()),
                Map.entry("criticals.chance", qaRandom.nextDouble()),
                Map.entry("spacing.preferredDistance", qaRandom.nextDouble(1.8, 4.5)),
                Map.entry("spacing.forwardPressure", qaRandom.nextDouble()),
                Map.entry("spacing.skill", qaRandom.nextDouble()),
                Map.entry("adaptation.strength", qaRandom.nextDouble(0, .75))
        );
        BotProfile profile = new BotProfile("FUZZ", values, Map.of());
        List<CombatScenario.TickInput> sequence = new ArrayList<>(ticks);
        double distance = qaRandom.nextDouble(2.0, 5.5);
        double previous = distance;
        int incomingCombo = 0;
        int outgoingCombo = 0;
        for (int tick = 0; tick < ticks; tick++) {
            int motion = qaRandom.nextInt(6);
            double delta = switch (motion) {
                case 0, 1 -> -.08 - qaRandom.nextDouble(.12);
                case 2 -> .08 + qaRandom.nextDouble(.12);
                case 3 -> -.25;
                case 4 -> .25;
                default -> 0;
            };
            distance = Math.max(.6, Math.min(7.5, distance + delta));
            double closing = previous - distance;
            previous = distance;
            double lateral = qaRandom.nextDouble(-.24, .24);
            if (qaRandom.nextDouble() < .08) lateral = -lateral;
            boolean incomingHit = qaRandom.nextDouble() < .025;
            boolean knockback = incomingHit && qaRandom.nextDouble() < .88;
            if (incomingHit) {
                incomingCombo = Math.min(8, incomingCombo + 1);
                outgoingCombo = 0;
            } else if (qaRandom.nextDouble() < .04) {
                outgoingCombo = Math.min(8, outgoingCombo + 1);
                incomingCombo = 0;
            }
            boolean grounded = qaRandom.nextDouble() > .14;
            double botVertical = grounded ? 0 : qaRandom.nextDouble(-.42, .42);
            boolean targetGrounded = qaRandom.nextDouble() > .18;
            double targetVertical = targetGrounded ? 0 : qaRandom.nextDouble(-.42, .42);
            boolean los = qaRandom.nextDouble() > .035;
            AttackExecutionResult result;
            double contactRoll = qaRandom.nextDouble();
            if (contactRoll < .58) result = AttackExecutionResult.CONTACT;
            else if (contactRoll < .96) result = AttackExecutionResult.WHIFF;
            else result = AttackExecutionResult.TARGET_INVALID;
            boolean damage = result == AttackExecutionResult.CONTACT && qaRandom.nextDouble() < .82;
            sequence.add(new CombatScenario.TickInput(distance, closing, -delta, lateral,
                    targetVertical, botVertical, grounded, targetGrounded, los,
                    incomingCombo, outgoingCombo, incomingHit, knockback,
                    qaRandom.nextDouble() < .75, qaRandom.nextDouble() < .008,
                    result, damage));
        }
        return new CombatScenario("FUZZ_seed-" + qaSeed, matchSeed, profile, List.copyOf(sequence), ok());
    }

    private static CombatScenario highPingSideStep() {
        BotProfile profile = profile(Map.of("simulatedPingMs", 150d));
        List<CombatScenario.TickInput> ticks = repeat(8, 2.7);
        for (int i = 0; i < ticks.size(); i++) {
            ticks.set(i, ticks.get(i).withExecution(AttackExecutionResult.WHIFF, false));
        }
        return new CombatScenario("HIGH_PING_SIDE_STEP", 472918234L, profile, ticks, result -> {
            List<String> failures = new ArrayList<>();
            if (result.metrics().attempts != 1) failures.add("expected one intent/attempt");
            if (result.metrics().whiffs != 1) failures.add("expected one WHIFF");
            if (result.metrics().hits != 0) failures.add("WHIFF must not damage");
            return failures;
        });
    }

    private static CombatScenario incomingKnockback() {
        List<CombatScenario.TickInput> ticks = repeat(14, 4.0);
        CombatScenario.TickInput hit = ticks.get(3);
        ticks.set(3, new CombatScenario.TickInput(hit.distance(), 0, 0, 0, 0, 0,
                true, true, true, 1, 0, true, true, true, false,
                AttackExecutionResult.WHIFF, false));
        return new CombatScenario("INCOMING_KNOCKBACK", 2002L, profile(Map.of()), ticks, result -> {
            boolean suppressed = result.frames().stream().anyMatch(frame -> frame.knockbackLocked() && !frame.movementWrite());
            boolean resumed = result.frames().stream().anyMatch(frame -> frame.tick() > 8 && frame.movementWrite());
            List<String> failures = new ArrayList<>();
            if (!suppressed) failures.add("movement was not suppressed during KB lock");
            if (!resumed) failures.add("movement did not resume after KB lock");
            return failures;
        });
    }

    private static CombatScenario jumpResetValid() {
        List<CombatScenario.TickInput> ticks = repeat(12, 3.4);
        CombatScenario.TickInput hit = ticks.get(2);
        ticks.set(2, new CombatScenario.TickInput(hit.distance(), 0, 0, 0, 0, 0,
                true, true, true, 1, 0, true, false, true, false,
                AttackExecutionResult.WHIFF, false));
        BotProfile profile = profile(Map.of("jumpReset.chance", 1d, "jumpReset.skill", 1d));
        return new CombatScenario("JUMP_RESET_VALID", 3011L, profile, ticks, result ->
                result.metrics().jumpResetOpportunities == 1 && result.metrics().jumpResetExecutions <= 1
                        ? List.of() : List.of("expected one opportunity and at most one JRESET"));
    }

    private static CombatScenario jumpResetInvalid() {
        return new CombatScenario("JUMP_RESET_INVALID", 3012L,
                profile(Map.of("jumpReset.chance", 1d, "jumpReset.skill", 1d)), repeat(16, 3.2),
                result -> result.metrics().jumpResetExecutions == 0
                        ? List.of() : List.of("JRESET occurred without incoming hit"));
    }

    private static CombatScenario criticalSetup() {
        BotProfile profile = profile(Map.of("criticals.chance", 1d, "criticals.skill", 1d));
        List<CombatScenario.TickInput> ticks = repeat(8, 2.5);
        CombatScenario.TickInput airborne = ticks.get(1);
        ticks.set(1, new CombatScenario.TickInput(2.5, 0, 0, 0, 0, .2,
                false, true, true, 0, 0, false, false, true, false,
                AttackExecutionResult.CONTACT, false));
        ticks.set(2, new CombatScenario.TickInput(2.5, 0, 0, 0, 0, -.2,
                false, true, true, 0, 0, false, false, true, false,
                AttackExecutionResult.CONTACT, true));
        return new CombatScenario("CRITICAL_SETUP", 4011L, profile, ticks, result -> {
            boolean critical = result.frames().stream().anyMatch(frame -> frame.verticalAction() == VerticalAction.CRITICAL_SETUP);
            boolean reset = result.frames().stream().anyMatch(frame -> frame.verticalAction() == VerticalAction.JUMP_RESET);
            return critical && !reset ? List.of() : List.of("critical setup was not distinct from JRESET");
        });
    }

    private static CombatScenario reactionHold() {
        BotProfile profile = profile(Map.of(
                "reaction.decisionMs", 200d,
                "reaction.aimMs", 200d,
                "reaction.movementMs", 200d
        ));
        List<CombatScenario.TickInput> ticks = repeat(14, 4.2);
        for (int i = 3; i < ticks.size(); i++) {
            ticks.set(i, new CombatScenario.TickInput(2.1, .25, 0, -.22, 0, 0,
                    true, true, true, 0, 0, false, false, true, false,
                    AttackExecutionResult.WHIFF, false));
        }
        return new CombatScenario("REACTION_HOLD", 5011L, profile, ticks, result -> {
            boolean held = false;
            for (int i = 1; i < result.frames().size(); i++) {
                QaFrame before = result.frames().get(i - 1);
                QaFrame now = result.frames().get(i);
                if (now.latestPerceptionTick() > before.latestPerceptionTick() && !now.movementUpdated()
                        && now.movementPlanPerceptionTick() == before.movementPlanPerceptionTick()) held = true;
            }
            return held ? List.of() : List.of("new perception was not observed while plans stayed held");
        });
    }

    private static CombatScenario watchdogAttack() {
        BotProfile profile = profile(Map.of("hitSelect.skill", 0d, "hitSelect.chance", 0d));
        List<CombatScenario.TickInput> ticks = repeat(5, 2.7);
        for (int i = 0; i < ticks.size(); i++) {
            ticks.set(i, ticks.get(i).withExecution(AttackExecutionResult.WHIFF, false));
        }
        return new CombatScenario("WATCHDOG_ATTACK", 6011L, profile, ticks, result ->
                result.metrics().watchdogIntents == 1 && result.metrics().whiffs == 1
                        ? List.of() : List.of("watchdog did not use the ordinary whiff-capable pipeline"));
    }

    private static CombatScenario contactWithoutDamage() {
        List<CombatScenario.TickInput> ticks = repeat(5, 2.7);
        return new CombatScenario("CONTACT_WITHOUT_DAMAGE", 7011L, profile(Map.of()), ticks, result ->
                result.metrics().attempts == 1 && result.metrics().contacts == 1 && result.metrics().hits == 0
                        ? List.of() : List.of("expected attempt=1 contact=1 confirmed hits=0"));
    }

    private static CombatScenario arenaEdgeRecovery() {
        List<CombatScenario.TickInput> ticks = repeat(10, 4.1);
        CombatScenario.TickInput edge = ticks.get(4);
        ticks.set(4, new CombatScenario.TickInput(edge.distance(), edge.closingSpeed(), 0, 0, 0, 0,
                true, true, true, 0, 0, false, true, true, true,
                AttackExecutionResult.WHIFF, false));
        return new CombatScenario("ARENA_EDGE_RECOVERY", 8011L, profile(Map.of()), ticks, result -> {
            QaFrame frame = result.frames().get(4);
            return frame.emergencyRecovery() && frame.movementWrite() && frame.latestPerceptionTick() >= 0
                    ? List.of() : List.of("safety recovery did not preserve tactical perception state");
        });
    }

    private static BotProfile profile(Map<String, Double> overrides) {
        java.util.HashMap<String, Double> values = new java.util.HashMap<>(Map.ofEntries(
                Map.entry("simulatedPingMs", 0d),
                Map.entry("reaction.decisionMs", 0d),
                Map.entry("reaction.aimMs", 0d),
                Map.entry("reaction.movementMs", 0d),
                Map.entry("reaction.decisionJitterMs", 0d),
                Map.entry("reaction.aimJitterMs", 0d),
                Map.entry("reaction.movementJitterMs", 0d),
                Map.entry("criticals.skill", 0d)
        ));
        values.putAll(overrides);
        return new BotProfile("QA", values, Map.of());
    }

    private static List<CombatScenario.TickInput> repeat(int count, double distance) {
        List<CombatScenario.TickInput> ticks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) ticks.add(CombatScenario.TickInput.stable(distance));
        return ticks;
    }

    private static CombatScenario.Expectation ok() { return result -> List.of(); }
}
