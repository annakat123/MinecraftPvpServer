package dev.pvpbot.bot.combat.attack;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.UUID;

/** Neutral Paper API ray trace from the bot's current eye and view direction. */
public final class PaperPhysicalAttackProbe implements PhysicalAttackProbe {
    @Override
    public AttackExecutionResult probe(Player bot, Player target, double reach) {
        if (!valid(bot) || !valid(target) || !target.isOnline()) return AttackExecutionResult.TARGET_INVALID;
        World world = bot.getWorld();
        if (!world.equals(target.getWorld())) return AttackExecutionResult.TARGET_INVALID;
        if (!Double.isFinite(reach) || reach <= 0) return AttackExecutionResult.WHIFF;

        Location eye = bot.getEyeLocation();
        Vector direction = eye.getDirection();
        if (!finite(direction) || direction.lengthSquared() == 0) return AttackExecutionResult.WHIFF;
        UUID botId = bot.getUniqueId();
        RayTraceResult hit = world.rayTrace(
                eye,
                direction.normalize(),
                reach,
                FluidCollisionMode.NEVER,
                true,
                0.0,
                entity -> !entity.getUniqueId().equals(botId) && entity.isValid() && entity.isInWorld()
        );
        return hit != null && hit.getHitEntity() != null
                && hit.getHitEntity().getUniqueId().equals(target.getUniqueId())
                ? AttackExecutionResult.CONTACT
                : AttackExecutionResult.WHIFF;
    }

    private static boolean valid(Player player) {
        return player != null && player.isValid() && player.isInWorld() && !player.isDead();
    }

    private static boolean finite(Vector vector) {
        return Double.isFinite(vector.getX())
                && Double.isFinite(vector.getY())
                && Double.isFinite(vector.getZ());
    }
}
