package dev.pvpbot.bot.trace;

import dev.pvpbot.bot.combat.attack.AttackExecutionResult;
import dev.pvpbot.bot.combat.attack.AttackIntent;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.Decision;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.DecisionInputs;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.DecisionReason;
import dev.pvpbot.bot.movement.VerticalAction;

import java.util.Map;

public final class TraceEvents {
    private TraceEvents() {}

    public record MatchStart(long tick, String pvpBotVersion, String matchId, long matchSeed,
                             String profile, Map<String, Double> profileValues,
                             Map<String, Boolean> profileToggles) implements CombatTraceEvent {
        @Override public TraceEventType event() { return TraceEventType.MATCH_START; }
    }
    public record PerceptionCaptured(long tick, double distance, double closingSpeed,
                                     double forwardVelocity, double lateralVelocity,
                                     double playerVerticalVelocity, double botVerticalVelocity,
                                     double botHealth, double playerHealth, int incomingCombo,
                                     int outgoingCombo, long ticksSinceIncomingHit,
                                     long ticksSinceOutgoingHit, boolean lineOfSight,
                                     boolean botOnGround, boolean playerOnGround) implements CombatTraceEvent {
        @Override public TraceEventType event() { return TraceEventType.PERCEPTION_CAPTURED; }
    }
    public record PerceptionMatured(long tick, long captureTick, long perceptionAgeTicks,
                                    int simulatedPingMs) implements CombatTraceEvent {
        @Override public TraceEventType event() { return TraceEventType.PERCEPTION_MATURED; }
    }
    public record DecisionUpdated(long tick, long perceptionTick, long perceptionAgeTicks,
                                  Decision decision, DecisionReason reason, DecisionInputs inputs,
                                  long decisionPlanAgeTicks, long aimPlanAgeTicks,
                                  long movementPlanAgeTicks, long decisionTicksUntilUpdate,
                                  long aimTicksUntilUpdate, long movementTicksUntilUpdate,
                                  double adaptationConfidence, double observedAggression,
                                  double observedLateralBias, double observedJumpRate) implements CombatTraceEvent {
        @Override public TraceEventType event() { return TraceEventType.DECISION_UPDATED; }
    }
    public record AimPlanUpdated(long tick, long perceptionTick, long perceptionAgeTicks,
                                 TraceVector targetPoint, double errorYaw, double errorPitch,
                                 double accuracy, long ticksUntilNextUpdate) implements CombatTraceEvent {
        @Override public TraceEventType event() { return TraceEventType.AIM_PLAN_UPDATED; }
    }
    public record AimExecution(long tick, boolean eligible, long heldPlanAgeTicks) implements CombatTraceEvent {
        @Override public TraceEventType event() { return TraceEventType.AIM_EXECUTION; }
    }
    public record MovementPlanUpdated(long tick, long perceptionTick, long perceptionAgeTicks,
                                      double forwardX, double forwardZ, double rightX, double rightZ,
                                      double forwardSpeed, int incomingCombo, boolean active,
                                      int strafeDirection, long ticksUntilNextUpdate) implements CombatTraceEvent {
        @Override public TraceEventType event() { return TraceEventType.MOVEMENT_PLAN_UPDATED; }
    }
    public record VerticalActionEvent(long tick, VerticalAction action, long sourceHitTick,
                                      int knockbackLockTicksRemaining) implements CombatTraceEvent {
        @Override public TraceEventType event() { return TraceEventType.VERTICAL_ACTION; }
    }
    public record KnockbackStarted(long tick, String cause, int lockTicks,
                                   TraceVector externalVelocityDiagnostic) implements CombatTraceEvent {
        @Override public TraceEventType event() { return TraceEventType.KNOCKBACK_STARTED; }
    }
    public record KnockbackEnded(long tick) implements CombatTraceEvent {
        @Override public TraceEventType event() { return TraceEventType.KNOCKBACK_ENDED; }
    }
    public record AttackIntentCreated(long tick, long sequence, long perceptionTick,
                                      Decision decision, DecisionReason reason, AttackIntent.Source source,
                                      double perceivedDistance, double reach, boolean perceivedLineOfSight,
                                      boolean intendedCritical) implements CombatTraceEvent {
        @Override public TraceEventType event() { return TraceEventType.ATTACK_INTENT_CREATED; }
    }
    public record AttackExecuted(long tick, long sequence, AttackExecutionResult result,
                                 boolean attempted, boolean attackInvoked) implements CombatTraceEvent {
        @Override public TraceEventType event() { return TraceEventType.ATTACK_EXECUTED; }
    }
    public record ConfirmedHit(long tick, String attacker, long sequence, boolean intendedCritical) implements CombatTraceEvent {
        @Override public TraceEventType event() { return TraceEventType.CONFIRMED_HIT; }
    }
    public record MatchEnd(long tick, String outcome, int attempts, int hits, int misses,
                           long watchdogIntents, long jumpResetOpportunities,
                           long jumpResetExecutions, long droppedEvents) implements CombatTraceEvent {
        @Override public TraceEventType event() { return TraceEventType.MATCH_END; }
    }
    public record TraceSummary(long tick, long droppedEvents) implements CombatTraceEvent {
        @Override public TraceEventType event() { return TraceEventType.TRACE_SUMMARY; }
    }
}
