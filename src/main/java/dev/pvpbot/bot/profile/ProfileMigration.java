package dev.pvpbot.bot.profile;

import java.util.HashMap;
import java.util.Map;

/** Maps 1.0.7 reaction fields into the single 1.0.8 runtime representation. */
public final class ProfileMigration {
    public static final String LEGACY_BASE_REACTION_MS = "baseReactionMs";
    public static final String LEGACY_REACTION_JITTER_MS = "reactionJitterMs";

    private static final String[] BASE_CHANNELS = {
            "reaction.decisionMs", "reaction.aimMs", "reaction.movementMs"
    };
    private static final String[] JITTER_CHANNELS = {
            "reaction.decisionJitterMs", "reaction.aimJitterMs", "reaction.movementJitterMs"
    };

    public static Map<String, Double> migrateLegacyReaction(Map<String, Double> raw) {
        Map<String, Double> migrated = new HashMap<>(raw);
        copyMissing(migrated, LEGACY_BASE_REACTION_MS, BASE_CHANNELS);
        copyMissing(migrated, LEGACY_REACTION_JITTER_MS, JITTER_CHANNELS);
        migrated.remove(LEGACY_BASE_REACTION_MS);
        migrated.remove(LEGACY_REACTION_JITTER_MS);
        return migrated;
    }

    private static void copyMissing(Map<String, Double> values, String legacyKey, String[] newKeys) {
        Double legacy = values.get(legacyKey);
        if (legacy == null) return;
        for (String key : newKeys) values.putIfAbsent(key, legacy);
    }

    private ProfileMigration() {}
}
