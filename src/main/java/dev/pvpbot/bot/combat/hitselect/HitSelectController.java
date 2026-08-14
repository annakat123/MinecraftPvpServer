package dev.pvpbot.bot.combat.hitselect;
import dev.pvpbot.bot.ai.perception.PerceptionSnapshot;
import dev.pvpbot.bot.profile.BotProfile;
public final class HitSelectController {
    public enum Decision { ATTACK_NOW, WAIT, CLOSE_DISTANCE, HOLD_DISTANCE, BAIT_ATTACK, COUNTER_HIT, CRITICAL_ATTACK, COMBO_CHASE, ESCAPE_COMBO }
    public enum DecisionReason {
        NO_LINE_OF_SIGHT,
        INCOMING_COMBO_ESCAPE,
        OUT_OF_REACH_COMBO_CHASE,
        OUT_OF_REACH_CLOSE_DISTANCE,
        HIT_SELECT_DISABLED_ATTACK,
        HIT_SELECT_DISABLED_WAIT,
        COOLDOWN_DISCIPLINE_WAIT,
        COUNTER_WINDOW,
        CRITICAL_GROUND_WINDOW,
        SPACING_BAIT,
        LOW_COMMITMENT_HOLD,
        DEFAULT_ATTACK
    }
    public record DecisionInputs(
            double reach,
            double cooldown,
            double cooldownThreshold,
            double counterWindowTicks,
            double preferredDistance,
            double baitScore,
            double commitmentScore,
            double adaptedAggression
    ) {}
    public record DecisionResult(Decision decision, DecisionReason reason, DecisionInputs inputs) {}

    public DecisionResult decide(PerceptionSnapshot s, BotProfile p, double cooldown, double adaptedAggression) {
        double reach=p.enabled("reach")?p.value("reach.blocks"):3.0;
        double skill=p.value("hitSelect.skill");
        double cooldownThreshold=.65 + .30 * p.value("hitSelect.cooldownDiscipline")+.03*skill;
        double counterWindow=4 + 9 * p.value("hitSelect.counterHitPreference")*skill;
        double preferredDistance=p.value("spacing.preferredDistance");
        double baitScore=p.value("hitSelect.patience")*p.value("hitSelect.baitPreference");
        double commitmentScore=p.value("hitSelect.chance")*skill;
        DecisionInputs inputs=new DecisionInputs(reach,cooldown,cooldownThreshold,counterWindow,
                preferredDistance,baitScore,commitmentScore,adaptedAggression);
        if (!s.lineOfSight()) return result(Decision.CLOSE_DISTANCE,DecisionReason.NO_LINE_OF_SIGHT,inputs);
        if (s.incomingCombo() >= 2 && s.ticksSinceIncomingHit() <= 6 && p.enabled("combo")) return result(Decision.ESCAPE_COMBO,DecisionReason.INCOMING_COMBO_ESCAPE,inputs);
        if (s.distance() > reach) return s.outgoingCombo() > 0
                ? result(Decision.COMBO_CHASE,DecisionReason.OUT_OF_REACH_COMBO_CHASE,inputs)
                : result(Decision.CLOSE_DISTANCE,DecisionReason.OUT_OF_REACH_CLOSE_DISTANCE,inputs);
        if(!p.enabled("hitSelect"))return cooldown>=.9
                ? result(Decision.ATTACK_NOW,DecisionReason.HIT_SELECT_DISABLED_ATTACK,inputs)
                : result(Decision.WAIT,DecisionReason.HIT_SELECT_DISABLED_WAIT,inputs);
        if (cooldown < cooldownThreshold) return result(Decision.WAIT,DecisionReason.COOLDOWN_DISCIPLINE_WAIT,inputs);
        if (s.ticksSinceIncomingHit() <= counterWindow && s.closingSpeed() > -.08) return result(Decision.COUNTER_HIT,DecisionReason.COUNTER_WINDOW,inputs);
        if (p.enabled("criticals") && s.botOnGround() && p.value("criticals.skill") > .55 && s.distance() < reach - .15) return result(Decision.CRITICAL_ATTACK,DecisionReason.CRITICAL_GROUND_WINDOW,inputs);
        if (s.distance() > preferredDistance && baitScore > .35 && adaptedAggression < .55) return result(Decision.BAIT_ATTACK,DecisionReason.SPACING_BAIT,inputs);
        if(commitmentScore<.25&&s.closingSpeed()<.05)return result(Decision.HOLD_DISTANCE,DecisionReason.LOW_COMMITMENT_HOLD,inputs);
        return result(Decision.ATTACK_NOW,DecisionReason.DEFAULT_ATTACK,inputs);
    }

    private static DecisionResult result(Decision decision, DecisionReason reason, DecisionInputs inputs) {
        return new DecisionResult(decision,reason,inputs);
    }
}
