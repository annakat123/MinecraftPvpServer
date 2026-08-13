package dev.pvpbot.bot.profile;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.*;
import java.util.logging.Logger;

public final class ProfileRepository {
    public enum Difficulty { EASY, NORMAL, HARD, EXPERT, CUSTOM }
    private final Map<Difficulty, BotProfile> presets = new EnumMap<>(Difficulty.class);
    public ProfileRepository(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "bot-profiles.yml");
        if (!file.exists()) plugin.saveResource("bot-profiles.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (Difficulty d : Difficulty.values()) if (d != Difficulty.CUSTOM) presets.put(d, read(d.name(), yaml.getConfigurationSection("profiles." + d.name()), plugin.getLogger()));
        presets.put(Difficulty.CUSTOM, presets.get(Difficulty.NORMAL));
    }
    public static BotProfile read(String name, ConfigurationSection section, Logger logger) {
        Map<String, Double> values = new HashMap<>();
        if (section == null) { logger.warning("Missing profile " + name + "; defaults used"); return BotProfile.defaults(name); }
        ProfileSchema.PARAMETERS.forEach((key, spec) -> { if (section.isSet(key)) values.put(key, section.getDouble(key)); });
        if (section.isSet(ProfileMigration.LEGACY_BASE_REACTION_MS)) values.put(ProfileMigration.LEGACY_BASE_REACTION_MS, section.getDouble(ProfileMigration.LEGACY_BASE_REACTION_MS));
        if (section.isSet(ProfileMigration.LEGACY_REACTION_JITTER_MS)) values.put(ProfileMigration.LEGACY_REACTION_JITTER_MS, section.getDouble(ProfileMigration.LEGACY_REACTION_JITTER_MS));
        Map<String, Double> migrated = ProfileMigration.migrateLegacyReaction(values);
        ProfileSchema.PARAMETERS.forEach((key, spec) -> { double raw = migrated.getOrDefault(key, spec.fallback()), safe = spec.clamp(raw); if (raw != safe) logger.warning(name + "." + key + " clamped to " + safe); migrated.put(key, safe); });
        Map<String,Boolean> toggles=new HashMap<>();ProfileSchema.TOGGLES.forEach(system->toggles.put(system,section.getBoolean(system+".enabled",true)));
        return new BotProfile(name, migrated, toggles);
    }
    public BotProfile get(Difficulty difficulty) { return presets.get(difficulty); }
}
