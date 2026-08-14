package dev.pvpbot.bot.entity;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface BotAnimation {
    void playAttack(Player bot, Player viewer);
}
