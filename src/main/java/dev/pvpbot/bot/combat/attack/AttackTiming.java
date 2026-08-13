package dev.pvpbot.bot.combat.attack;

/** Separates intentional attempt cadence from event-confirmed outgoing hits. */
public final class AttackTiming {
    private long lastAttackAttemptTick = -100;
    private long lastSuccessfulOutgoingHitTick = -100;

    public void attempted(long tick) {
        lastAttackAttemptTick = tick;
    }

    public void successfulOutgoingHit(long tick) {
        lastSuccessfulOutgoingHitTick = tick;
    }

    public long lastAttackAttemptTick() {
        return lastAttackAttemptTick;
    }

    public long lastSuccessfulOutgoingHitTick() {
        return lastSuccessfulOutgoingHitTick;
    }

    public long ticksSinceSuccessfulOutgoingHit(long tick) {
        return tick - lastSuccessfulOutgoingHitTick;
    }

    public double cooldown(long tick) {
        return Math.min(1, (tick - lastAttackAttemptTick) / 12.5);
    }
}
