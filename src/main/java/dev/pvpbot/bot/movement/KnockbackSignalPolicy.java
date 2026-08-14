package dev.pvpbot.bot.movement;

import io.papermc.paper.event.entity.EntityKnockbackEvent;

/** Admits only a real melee impulse correlated with the confirmed player -> bot damage event. */
public final class KnockbackSignalPolicy {
    private KnockbackSignalPolicy() {
    }

    public static boolean accepts(EntityKnockbackEvent.Cause cause, boolean confirmedIncomingHitThisTick) {
        if (!confirmedIncomingHitThisTick || cause == null) return false;
        return cause == EntityKnockbackEvent.Cause.ENTITY_ATTACK
                || cause == EntityKnockbackEvent.Cause.SWEEP_ATTACK;
    }
}
