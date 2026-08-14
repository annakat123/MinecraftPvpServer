package dev.pvpbot.logic;

import dev.pvpbot.bot.ai.perception.CombatFrame;
import dev.pvpbot.bot.ai.perception.PerceptionSnapshot;
import dev.pvpbot.bot.combat.hitselect.HitSelectController;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.Decision;
import dev.pvpbot.bot.profile.BotProfile;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HitSelectEquivalenceTest {
    @Test void explainableDecisionMatchesFrozenOnePointZeroTenLogicAcrossLargeGeneratedSet() {
        SplittableRandom qa = new SplittableRandom(0x11010E011A1E17L);
        HitSelectController controller = new HitSelectController();
        for (int index = 0; index < 100_000; index++) {
            BotProfile profile = profile(qa);
            PerceptionSnapshot snapshot = snapshot(qa);
            double cooldown = qa.nextDouble(0, 1.2);
            double aggression = qa.nextDouble();

            Decision frozen = oldDecision(snapshot, profile, cooldown, aggression);
            Decision explainable = controller.decide(snapshot, profile, cooldown, aggression).decision();

            assertEquals(frozen, explainable, "generated input " + index);
        }
    }

    private static Decision oldDecision(PerceptionSnapshot s, BotProfile p, double cooldown,
                                        double adaptedAggression) {
        double reach = p.enabled("reach") ? p.value("reach.blocks") : 3.0;
        if (!s.lineOfSight()) return Decision.CLOSE_DISTANCE;
        if (s.incomingCombo() >= 2 && s.ticksSinceIncomingHit() <= 6 && p.enabled("combo")) return Decision.ESCAPE_COMBO;
        if (s.distance() > reach) return s.outgoingCombo() > 0 ? Decision.COMBO_CHASE : Decision.CLOSE_DISTANCE;
        if (!p.enabled("hitSelect")) return cooldown >= .9 ? Decision.ATTACK_NOW : Decision.WAIT;
        double skill = p.value("hitSelect.skill");
        if (cooldown < .65 + .30 * p.value("hitSelect.cooldownDiscipline") + .03 * skill) return Decision.WAIT;
        if (s.ticksSinceIncomingHit() <= 4 + 9 * p.value("hitSelect.counterHitPreference") * skill
                && s.closingSpeed() > -.08) return Decision.COUNTER_HIT;
        if (p.enabled("criticals") && s.botOnGround() && p.value("criticals.skill") > .55
                && s.distance() < reach - .15) return Decision.CRITICAL_ATTACK;
        if (s.distance() > p.value("spacing.preferredDistance")
                && p.value("hitSelect.patience") * p.value("hitSelect.baitPreference") > .35
                && adaptedAggression < .55) return Decision.BAIT_ATTACK;
        if (p.value("hitSelect.chance") * skill < .25 && s.closingSpeed() < .05) return Decision.HOLD_DISTANCE;
        return Decision.ATTACK_NOW;
    }

    private static BotProfile profile(SplittableRandom qa) {
        Map<String, Double> values = new HashMap<>();
        values.put("reach.blocks", qa.nextDouble(2, 6));
        values.put("hitSelect.skill", qa.nextDouble());
        values.put("hitSelect.chance", qa.nextDouble());
        values.put("hitSelect.patience", qa.nextDouble());
        values.put("hitSelect.counterHitPreference", qa.nextDouble());
        values.put("hitSelect.cooldownDiscipline", qa.nextDouble());
        values.put("hitSelect.baitPreference", qa.nextDouble());
        values.put("criticals.skill", qa.nextDouble());
        values.put("spacing.preferredDistance", qa.nextDouble(1.8, 4.5));
        Map<String, Boolean> toggles = Map.of(
                "reach", qa.nextBoolean(),
                "combo", qa.nextBoolean(),
                "hitSelect", qa.nextBoolean(),
                "criticals", qa.nextBoolean()
        );
        return new BotProfile("EQUIVALENCE", values, toggles);
    }

    private static PerceptionSnapshot snapshot(SplittableRandom qa) {
        double distance = qa.nextDouble(0, 7);
        Location body = new Location(null, 0, 64, distance);
        return new PerceptionSnapshot(qa.nextLong(1, 1000), body, body.clone().add(0, 1.62, 0),
                new Vector(qa.nextDouble(-.3, .3), qa.nextDouble(-.5, .5), qa.nextDouble(-.3, .3)),
                CombatFrame.from(0, 0, 0, distance), distance, qa.nextDouble(-.4, .4),
                qa.nextDouble(-.3, .3), qa.nextDouble(-.3, .3), qa.nextDouble(-.5, .5),
                qa.nextDouble(-.5, .5), qa.nextDouble(1, 20), qa.nextDouble(1, 20),
                qa.nextInt(0, 9), qa.nextInt(0, 9), qa.nextLong(0, 30), qa.nextLong(0, 30),
                qa.nextBoolean(), qa.nextBoolean(), qa.nextBoolean());
    }
}
