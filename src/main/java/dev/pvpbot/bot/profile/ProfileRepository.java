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
    private static BotProfile read(String name, ConfigurationSection section, Logger logger) {
        Map<String, Double> values = new HashMap<>();
        if (section == null) { logger.warning("Missing profile " + name + "; defaults used"); return BotProfile.defaults(name); }
        ProfileSchema.PARAMETERS.forEach((key, spec) -> { double raw = section.getDouble(key, spec.fallback()), safe = spec.clamp(raw); if (raw != safe) logger.warning(name + "." + key + " clamped to " + safe); values.put(key, safe); });
        Map<String,Boolean> toggles=new HashMap<>();ProfileSchema.TOGGLES.forEach(system->toggles.put(system,section.getBoolean(system+".enabled",true)));
        return new BotProfile(name, values, toggles);
    }
    public BotProfile get(Difficulty difficulty) { return presets.get(difficulty); }
}
