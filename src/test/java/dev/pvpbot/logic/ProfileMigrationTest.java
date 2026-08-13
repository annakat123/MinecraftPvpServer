package dev.pvpbot.logic;

import dev.pvpbot.bot.profile.BotProfile;
import dev.pvpbot.bot.profile.ProfileRepository;
import dev.pvpbot.database.CustomProfileCodec;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.logging.Logger;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ProfileMigrationTest {
    @Test void legacyYamlReactionFieldsMigrateToAllChannels() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("profiles:\n  LEGACY:\n    baseReactionMs: 177\n    reactionJitterMs: 44\n");
        BotProfile profile = ProfileRepository.read("LEGACY", yaml.getConfigurationSection("profiles.LEGACY"), Logger.getAnonymousLogger());
        assertAllChannels(profile, 177, 44);
    }

    @Test void bundledProfilesMapOldNominalValuesToEveryChannel() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (var input = getClass().getClassLoader().getResourceAsStream("bot-profiles.yml")) {
            assertNotNull(input);
            yaml.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        assertAllChannels(ProfileRepository.read("EASY", yaml.getConfigurationSection("profiles.EASY"), Logger.getAnonymousLogger()), 190, 45);
        assertAllChannels(ProfileRepository.read("NORMAL", yaml.getConfigurationSection("profiles.NORMAL"), Logger.getAnonymousLogger()), 130, 30);
        assertAllChannels(ProfileRepository.read("HARD", yaml.getConfigurationSection("profiles.HARD"), Logger.getAnonymousLogger()), 90, 20);
        assertAllChannels(ProfileRepository.read("EXPERT", yaml.getConfigurationSection("profiles.EXPERT"), Logger.getAnonymousLogger()), 65, 14);
    }

    @Test void legacySqliteCustomProfilePayloadMigratesToAllChannels() {
        BotProfile profile = CustomProfileCodec.decode("v:baseReactionMs=188.0;v:reactionJitterMs=33.0;t:aim=true");
        assertAllChannels(profile, 188, 33);
    }

    @Test void newlySavedCustomProfileContainsOnlyNewReactionFields() {
        BotProfile profile = new BotProfile("CUSTOM", Map.of(
                "baseReactionMs", 188d,
                "reactionJitterMs", 33d
        ), Map.of());
        String encoded = CustomProfileCodec.encode(profile);
        assertFalse(encoded.contains("v:baseReactionMs="));
        assertFalse(encoded.contains("v:reactionJitterMs="));
        assertTrue(encoded.contains("v:reaction.decisionMs=188.0"));
        assertTrue(encoded.contains("v:reaction.aimMs=188.0"));
        assertTrue(encoded.contains("v:reaction.movementMs=188.0"));
    }

    @Test void explicitNewBaseAndJitterOverrideLegacyOnlyForThoseChannels() {
        BotProfile profile = new BotProfile("CUSTOM", Map.of(
                "baseReactionMs", 130d,
                "reactionJitterMs", 30d,
                "reaction.aimMs", 75d,
                "reaction.aimJitterMs", 12d
        ), Map.of());
        assertEquals(130, profile.value("reaction.decisionMs"));
        assertEquals(75, profile.value("reaction.aimMs"));
        assertEquals(130, profile.value("reaction.movementMs"));
        assertEquals(30, profile.value("reaction.decisionJitterMs"));
        assertEquals(12, profile.value("reaction.aimJitterMs"));
        assertEquals(30, profile.value("reaction.movementJitterMs"));
    }

    private static void assertAllChannels(BotProfile profile, double base, double jitter) {
        assertEquals(base, profile.value("reaction.decisionMs"));
        assertEquals(base, profile.value("reaction.aimMs"));
        assertEquals(base, profile.value("reaction.movementMs"));
        assertEquals(jitter, profile.value("reaction.decisionJitterMs"));
        assertEquals(jitter, profile.value("reaction.aimJitterMs"));
        assertEquals(jitter, profile.value("reaction.movementJitterMs"));
    }
}
