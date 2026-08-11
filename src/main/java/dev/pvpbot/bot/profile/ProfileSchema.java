package dev.pvpbot.bot.profile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ProfileSchema {
    public record Parameter(double min, double max, double fallback, double step) {
        public double clamp(double value) { return Math.max(min, Math.min(max, value)); }
    }
    public static final Map<String, Parameter> PARAMETERS = parameters();
    public static final Set<String> TOGGLES = Set.of("aim", "reach", "hitSelect", "criticals", "strafe", "spacing", "sprintReset", "wTap", "sTap", "jumpReset", "combo", "adaptation");
    private static Map<String, Parameter> parameters() {
        Map<String, Parameter> p = new LinkedHashMap<>();
        p.put("simulatedPingMs", new Parameter(0, 500, 85, 10));
        p.put("baseReactionMs", new Parameter(20, 500, 130, 10));
        p.put("reactionJitterMs", new Parameter(0, 200, 30, 5));
        p.put("reach.blocks", new Parameter(2, 6, 2.9, .1));
        p.put("aim.accuracy", unit(.68)); p.put("aim.predictionStrength", unit(.42));
        p.put("aim.maxYawSpeed", new Parameter(3, 90, 19, 2)); p.put("aim.maxPitchSpeed", new Parameter(2, 90, 13, 2));
        p.put("hitSelect.skill", unit(.55)); p.put("hitSelect.chance", unit(.85)); p.put("hitSelect.patience", unit(.5));
        p.put("hitSelect.counterHitPreference", unit(.5)); p.put("hitSelect.cooldownDiscipline", unit(.75));
        p.put("hitSelect.baitPreference", unit(.35)); p.put("criticals.skill", unit(.5)); p.put("criticals.chance", unit(.45));
        p.put("strafe.skill", unit(.6)); p.put("strafe.chance", unit(.85)); p.put("strafe.intensity", unit(.6));
        p.put("spacing.skill", unit(.58)); p.put("spacing.preferredDistance", new Parameter(1.8, 4.5, 2.85, .1));
        p.put("spacing.forwardPressure", unit(.58)); p.put("sprintReset.skill", unit(.5)); p.put("wTap.skill", unit(.5)); p.put("wTap.chance", unit(.52));
        p.put("sTap.skill", unit(.42)); p.put("sTap.chance", unit(.38)); p.put("jumpReset.skill", unit(.35)); p.put("jumpReset.chance", unit(.32));
        p.put("combo.chaseSkill", unit(.55)); p.put("combo.escapeSkill", unit(.5)); p.put("adaptation.strength", new Parameter(0, .75, .25, .05));
        return Map.copyOf(p);
    }
    private static Parameter unit(double fallback) { return new Parameter(0, 1, fallback, .05); }
    private ProfileSchema() {}
}
