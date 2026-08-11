package dev.pvpbot.duel;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
public interface KitDefinition { String id(); void apply(Player player); void apply(LivingEntity bot); }
