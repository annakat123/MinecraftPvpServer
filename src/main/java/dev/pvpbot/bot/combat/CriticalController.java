package dev.pvpbot.bot.combat;

import dev.pvpbot.bot.movement.VerticalActionController;
import dev.pvpbot.bot.profile.BotProfile;
import org.bukkit.entity.Player;

import java.util.random.RandomGenerator;

public final class CriticalController {
    private final RandomGenerator random;

    public CriticalController(RandomGenerator random) {
        this.random = random;
    }

    public boolean tryStart(Player bot, BotProfile profile, VerticalActionController verticalActions, long tick) {
        if (!profile.enabled("criticals") || !bot.isOnGround()
                || random.nextDouble() > profile.value("criticals.chance") * profile.value("criticals.skill")) {
            return false;
        }
        verticalActions.criticalSetup(bot, tick);
        return true;
    }

    public boolean criticalWindow(Player bot) {
        return !bot.isOnGround() && bot.getVelocity().getY() < 0 && !bot.isInWater();
    }
}
