package dev.pvpbot.bot.entity;

import net.citizensnpcs.util.PlayerAnimation;
import org.bukkit.entity.Player;

/** Citizens packet-backed player animation boundary. */
public final class CitizensBotAnimation implements BotAnimation {
    @Override
    public void playAttack(Player bot, Player viewer) {
        PlayerAnimation.ARM_SWING.play(bot, viewer);
    }
}
