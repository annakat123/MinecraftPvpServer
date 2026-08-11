package dev.pvpbot.bot.combat.hitselect;
import dev.pvpbot.bot.ai.perception.PerceptionSnapshot;
import dev.pvpbot.bot.profile.BotProfile;
public final class HitSelectController {
    public enum Decision { ATTACK_NOW, WAIT, CLOSE_DISTANCE, HOLD_DISTANCE, BAIT_ATTACK, COUNTER_HIT, CRITICAL_ATTACK, COMBO_CHASE, ESCAPE_COMBO }
    public Decision decide(PerceptionSnapshot s, BotProfile p, double cooldown, double adaptedAggression) {
        double reach=p.enabled("reach")?p.value("reach.blocks"):3.0;
        if (!s.lineOfSight()) return Decision.CLOSE_DISTANCE;
        if (s.incomingCombo() >= 2 && p.enabled("combo")) return Decision.ESCAPE_COMBO;
        if (s.distance() > reach) return s.outgoingCombo() > 0 ? Decision.COMBO_CHASE : Decision.CLOSE_DISTANCE;
        if(!p.enabled("hitSelect"))return cooldown>=.9?Decision.ATTACK_NOW:Decision.WAIT;
        double skill=p.value("hitSelect.skill");
        if (cooldown < .65 + .30 * p.value("hitSelect.cooldownDiscipline")+.03*skill) return Decision.WAIT;
        if (s.ticksSinceIncomingHit() <= 4 + 9 * p.value("hitSelect.counterHitPreference")*skill && s.closingSpeed() > -.08) return Decision.COUNTER_HIT;
        if (p.enabled("criticals") && s.botOnGround() && p.value("criticals.skill") > .55 && s.distance() < reach - .15) return Decision.CRITICAL_ATTACK;
        if (s.distance() > p.value("spacing.preferredDistance") && p.value("hitSelect.patience")*p.value("hitSelect.baitPreference") > .35 && adaptedAggression < .55) return Decision.BAIT_ATTACK;
        if(p.value("hitSelect.chance")*skill<.25&&s.closingSpeed()<.05)return Decision.HOLD_DISTANCE;
        return Decision.ATTACK_NOW;
    }
}
