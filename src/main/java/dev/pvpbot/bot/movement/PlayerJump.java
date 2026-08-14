package dev.pvpbot.bot.movement;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Applies vanilla's normal unmodified player jump velocity without replacing horizontal velocity. */
public final class PlayerJump {
    public static final double VANILLA_JUMP_VELOCITY = 0.42D;

    private PlayerJump() {
    }

    public static void jump(Player player) {
        Vector velocity = player.getVelocity();
        velocity.setY(VANILLA_JUMP_VELOCITY);
        player.setVelocity(velocity);
    }
}
