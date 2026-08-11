package dev.pvpbot.bot.entity;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.*;
public final class ConfiguredSkinProvider implements SkinProvider {
    private final List<String> pool; private final String fallback; private final Random random=new Random();
    public ConfiguredSkinProvider(FileConfiguration config){fallback=config.getString("skins.fallback","Steve"); List<String> names=config.getStringList("skins.pool");pool=names.isEmpty()?List.of(fallback):List.copyOf(names);}
    public String randomSkin(){return pool.get(random.nextInt(pool.size()));} public String fallbackSkin(){return fallback;}
}
