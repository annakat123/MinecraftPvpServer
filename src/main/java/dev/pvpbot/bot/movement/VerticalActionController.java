package dev.pvpbot.bot.movement;

import dev.pvpbot.bot.profile.BotProfile;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Event-relative vertical technique and external-impulse state; owns no scheduler tasks. */
public final class VerticalActionController {
    public static final int JUMP_RESET_DELAY_TICKS = 1;
    public static final int JUMP_RESET_WINDOW_TICKS = 2;
    public static final int KNOCKBACK_LOCK_TICKS = 4;
    private static final int DEBUG_ACTION_TICKS = 12;

    private record JumpResetOpportunity(long executeFromTick, long expiresAfterTick) {
    }

    private final RandomGenerator techniqueRandom;
    private final Deque<JumpResetOpportunity> jumpResets = new ArrayDeque<>();
    private long knockbackLockFromTick = Long.MAX_VALUE;
    private long knockbackLockUntilTick = Long.MIN_VALUE;
    private long lastIntentionalJumpTick = Long.MIN_VALUE;
    private long debugActionUntilTick = Long.MIN_VALUE;
    private VerticalAction verticalAction = VerticalAction.NONE;
    private boolean criticalSetupActive;
    private long jumpResetOpportunities;
    private long jumpResetChanceSamples;
    private long jumpResetExecutions;

    public VerticalActionController(RandomGenerator techniqueRandom) {
        this.techniqueRandom = Objects.requireNonNull(techniqueRandom, "techniqueRandom");
    }

    /** One confirmed player -> bot damage event creates and resolves one probability trial. */
    public void incomingHit(BotProfile profile, long hitTick) {
        jumpResetOpportunities++;
        if (!profile.enabled("jumpReset")) return;

        jumpResetChanceSamples++;
        double chance = profile.value("jumpReset.chance") * profile.value("jumpReset.skill") * .14;
        if (techniqueRandom.nextDouble() >= chance) return;

        long executeFrom = hitTick + JUMP_RESET_DELAY_TICKS;
        jumpResets.addLast(new JumpResetOpportunity(
                executeFrom,
                executeFrom + JUMP_RESET_WINDOW_TICKS - 1
        ));
    }

    /** Called synchronously from Paper's actual knockback event; cognition latency is not involved. */
    public void incomingKnockback(long signalTick) {
        long from = signalTick + 1;
        knockbackLockFromTick = Math.min(knockbackLockFromTick, from);
        knockbackLockUntilTick = Math.max(knockbackLockUntilTick, from + KNOCKBACK_LOCK_TICKS - 1);
        mark(VerticalAction.INCOMING_KNOCKBACK, signalTick);
    }

    public void beginTick(long tick) {
        expireJumpResets(tick);
        if (tick > debugActionUntilTick) verticalAction = VerticalAction.NONE;
        if (tick > knockbackLockUntilTick) {
            knockbackLockFromTick = Long.MAX_VALUE;
            knockbackLockUntilTick = Long.MIN_VALUE;
        }
    }

    public boolean tryJumpReset(Player bot, long tick) {
        return tryJumpReset(bot.isOnGround(), tick, () -> PlayerJump.jump(bot));
    }

    /** Pure runtime boundary used by deterministic QA; production delegates with the same checks. */
    public boolean tryJumpReset(boolean grounded, long tick, Runnable jump) {
        expireJumpResets(tick);
        JumpResetOpportunity opportunity = jumpResets.peekFirst();
        if (opportunity == null || tick < opportunity.executeFromTick() || !grounded) return false;

        jumpResets.removeFirst();
        jump.run();
        criticalSetupActive = false;
        lastIntentionalJumpTick = tick;
        jumpResetExecutions++;
        mark(VerticalAction.JUMP_RESET, tick);
        return true;
    }

    public void criticalSetup(Player bot, long tick) {
        criticalSetup(tick, () -> PlayerJump.jump(bot));
    }

    /** Pure runtime boundary used by deterministic QA; production supplies the physical jump. */
    public void criticalSetup(long tick, Runnable jump) {
        jump.run();
        criticalSetupActive = true;
        lastIntentionalJumpTick = tick;
        mark(VerticalAction.CRITICAL_SETUP, tick);
    }

    public boolean intentionalJumpStarted(long tick) {
        return lastIntentionalJumpTick == tick;
    }

    /** Clears critical provenance only after the setup jump has had a chance to leave the ground. */
    public void observeGrounded(boolean grounded, long tick) {
        if (grounded && tick > lastIntentionalJumpTick) criticalSetupActive = false;
    }

    public boolean criticalSetupActive() {
        return criticalSetupActive;
    }

    public boolean knockbackLocked(long tick) {
        return tick >= knockbackLockFromTick && tick <= knockbackLockUntilTick;
    }

    public int knockbackLockTicksRemaining(long tick) {
        if (knockbackLockUntilTick < knockbackLockFromTick || tick > knockbackLockUntilTick) return 0;
        long effectiveTick = Math.max(tick, knockbackLockFromTick);
        return (int) Math.min(Integer.MAX_VALUE, knockbackLockUntilTick - effectiveTick + 1);
    }

    public VerticalAction verticalAction() {
        return verticalAction;
    }

    public long jumpResetOpportunities() {
        return jumpResetOpportunities;
    }

    public long jumpResetChanceSamples() {
        return jumpResetChanceSamples;
    }

    public long jumpResetExecutions() {
        return jumpResetExecutions;
    }

    public int pendingJumpResets() {
        return jumpResets.size();
    }

    private void expireJumpResets(long tick) {
        while (!jumpResets.isEmpty() && tick > jumpResets.peekFirst().expiresAfterTick()) {
            jumpResets.removeFirst();
        }
    }

    private void mark(VerticalAction action, long tick) {
        verticalAction = action;
        debugActionUntilTick = tick + DEBUG_ACTION_TICKS;
    }
}
