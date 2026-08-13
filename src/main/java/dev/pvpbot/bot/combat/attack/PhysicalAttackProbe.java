package dev.pvpbot.bot.combat.attack;

import org.bukkit.entity.Player;

/** Small runtime boundary around Paper's current-world ray trace. */
@FunctionalInterface
public interface PhysicalAttackProbe {
    AttackExecutionResult probe(Player bot, Player target, double reach);
}
