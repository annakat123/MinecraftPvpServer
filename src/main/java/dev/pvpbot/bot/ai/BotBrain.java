package dev.pvpbot.bot.ai;

import dev.pvpbot.arena.Arena;
import dev.pvpbot.bot.ai.adaptation.AdaptationController;
import dev.pvpbot.bot.ai.perception.CombatFrame;
import dev.pvpbot.bot.ai.perception.LatencyBuffer;
import dev.pvpbot.bot.ai.perception.PerceptionSnapshot;
import dev.pvpbot.bot.ai.perception.RelativeMotion;
import dev.pvpbot.bot.ai.random.MatchRandom;
import dev.pvpbot.bot.ai.random.MatchRandom.Subsystem;
import dev.pvpbot.bot.ai.reaction.ReactionGate;
import dev.pvpbot.bot.combat.AimController;
import dev.pvpbot.bot.combat.AimController.AimPlan;
import dev.pvpbot.bot.combat.CriticalController;
import dev.pvpbot.bot.combat.attack.AttackExecutionResult;
import dev.pvpbot.bot.combat.attack.AttackExecutor;
import dev.pvpbot.bot.combat.attack.AttackIntent;
import dev.pvpbot.bot.combat.attack.AttackIntentPlanner;
import dev.pvpbot.bot.combat.attack.AttackTiming;
import dev.pvpbot.bot.combat.attack.PaperPhysicalAttackProbe;
import dev.pvpbot.bot.combat.attack.PhysicalAttackProbe;
import dev.pvpbot.bot.combat.combo.ComboTracker;
import dev.pvpbot.bot.combat.hitselect.HitSelectController;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.Decision;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.DecisionReason;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.DecisionResult;
import dev.pvpbot.bot.entity.BotHandle;
import dev.pvpbot.bot.movement.MovementController;
import dev.pvpbot.bot.movement.MovementController.MovementPlan;
import dev.pvpbot.bot.movement.KnockbackSignalPolicy;
import dev.pvpbot.bot.movement.VerticalAction;
import dev.pvpbot.bot.movement.VerticalActionController;
import dev.pvpbot.bot.profile.BotProfile;
import dev.pvpbot.bot.trace.CombatTraceSink;
import dev.pvpbot.bot.trace.NoopCombatTraceSink;
import dev.pvpbot.bot.trace.TraceEvents;
import dev.pvpbot.bot.trace.TraceVector;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Optional;
import java.util.random.RandomGenerator;

public final class BotBrain {
    public interface Telemetry { void botAttackAttempt(); }

    private final BotHandle handle;
    private final Player target;
    private final Arena arena;
    private final BotProfile profile;
    private final Telemetry telemetry;
    private final LatencyBuffer<PerceptionSnapshot> latency = new LatencyBuffer<>();
    private final HitSelectController hitSelect = new HitSelectController();
    private final AimController aim;
    private final CriticalController critical;
    private final MovementController movement;
    private final VerticalActionController verticalActions;
    private final AttackIntentPlanner attackPlanner = new AttackIntentPlanner();
    private final AttackExecutor attackExecutor = new AttackExecutor();
    private final AttackTiming attackTiming = new AttackTiming();
    private final PhysicalAttackProbe physicalAttackProbe;
    private final AdaptationController adaptation = new AdaptationController();
    private final ReactionGate decisionGate = new ReactionGate();
    private final ReactionGate aimGate = new ReactionGate();
    private final ReactionGate movementGate = new ReactionGate();
    private final RandomGenerator decisionReactionRandom;
    private final RandomGenerator aimReactionRandom;
    private final RandomGenerator movementReactionRandom;
    private CombatTraceSink traceSink;

    private long tick;
    private long lastIncoming = -100;
    private double previousCapturedDistance = Double.NaN;
    private PerceptionSnapshot latestPerceived;
    private Decision decision = Decision.WAIT;
    private DecisionReason decisionReason = DecisionReason.COOLDOWN_DISCIPLINE_WAIT;
    private AimPlan aimPlan;
    private MovementPlan movementPlan;
    private boolean aimEligible;
    private AttackIntent lastIntent;
    private AttackIntent executingContactIntent;
    private AttackExecutionResult lastExecutionResult;
    private long lastTracedMaturedTick = Long.MIN_VALUE;
    private boolean lastAimEligibility;
    private boolean aimEligibilityTraced;
    private boolean lastKnockbackLocked;

    public BotBrain(BotHandle handle, Player target, Arena arena, BotProfile profile, MatchRandom random, Telemetry telemetry) {
        this(handle, target, arena, profile, random, telemetry, NoopCombatTraceSink.INSTANCE);
    }

    public BotBrain(BotHandle handle, Player target, Arena arena, BotProfile profile, MatchRandom random,
                    Telemetry telemetry, CombatTraceSink traceSink) {
        this.handle = handle;
        this.target = target;
        this.arena = arena;
        this.profile = profile;
        this.telemetry = telemetry;
        this.traceSink = traceSink == null ? NoopCombatTraceSink.INSTANCE : traceSink;
        decisionReactionRandom = random.stream(Subsystem.DECISION_REACTION);
        aimReactionRandom = random.stream(Subsystem.AIM_REACTION);
        movementReactionRandom = random.stream(Subsystem.MOVEMENT_REACTION);
        aim = new AimController(random.stream(Subsystem.AIM));
        critical = new CriticalController(random.stream(Subsystem.CRITICAL));
        movement = new MovementController(random.stream(Subsystem.MOVEMENT), random.stream(Subsystem.TECHNIQUE));
        verticalActions = movement.verticalActions();
        physicalAttackProbe = new PaperPhysicalAttackProbe();
    }

    public void tick(ComboTracker combo) {
        tick++;
        combo.expire(org.bukkit.Bukkit.getCurrentTick(), 25);
        Player bot = handle.entity();
        if (bot == null || !bot.isValid() || !bot.isInWorld() || bot.isDead()) return;
        verticalActions.beginTick(tick);
        boolean knockbackLocked = verticalActions.knockbackLocked(tick);
        if (lastKnockbackLocked && !knockbackLocked && traceSink.enabled()) {
            traceSink.emit(new TraceEvents.KnockbackEnded(tick));
        }
        lastKnockbackLocked = knockbackLocked;
        verticalActions.observeGrounded(bot.isOnGround(), tick);
        long jumpResetExecutions = verticalActions.jumpResetExecutions();
        verticalActions.tryJumpReset(bot, tick);
        if (verticalActions.jumpResetExecutions() != jumpResetExecutions) {
            emit(new TraceEvents.VerticalActionEvent(tick, VerticalAction.JUMP_RESET, lastIncoming,
                    verticalActions.knockbackLockTicksRemaining(tick)));
        }

        long now = System.currentTimeMillis();
        PerceptionSnapshot captured = captureObservation(bot, combo);
        if (traceSink.enabled()) emit(perceptionCaptured(captured));
        latency.offer(now, profile.millis("simulatedPingMs"), captured);
        Optional<PerceptionSnapshot> matured = latency.poll(now);
        if (matured.isEmpty()) return;

        PerceptionSnapshot perceived = matured.get();
        latestPerceived = perceived;
        adaptation.observe(perceived);
        if (perceived.tick() != lastTracedMaturedTick) {
            if (traceSink.enabled()) traceSink.emit(new TraceEvents.PerceptionMatured(tick,
                    perceived.tick(), tick - perceived.tick(), profile.millis("simulatedPingMs")));
            lastTracedMaturedTick = perceived.tick();
        }

        if (decisionGate.ready(tick)) {
            double cooldown = attackTiming.cooldown(tick);
            DecisionResult selected = hitSelect.decide(perceived, profile, cooldown, adaptation.aggression(profile));
            decision = selected.decision();
            decisionReason = selected.reason();
            decisionGate.scheduleNext(tick, profile.millis("reaction.decisionMs"),
                    profile.millis("reaction.decisionJitterMs"), decisionReactionRandom);
            var model = adaptation.model();
            if (traceSink.enabled()) traceSink.emit(new TraceEvents.DecisionUpdated(tick,
                    perceived.tick(), tick - perceived.tick(), decision, decisionReason,
                    selected.inputs(), decisionGate.ageTicks(tick), aimGate.ageTicks(tick),
                    movementGate.ageTicks(tick), decisionGate.ticksUntilReady(tick),
                    aimGate.ticksUntilReady(tick), movementGate.ticksUntilReady(tick),
                    model.confidence(), model.aggression(), model.lateralBias(), model.jumpRate()));
        }

        boolean aimUpdated = false;
        if (aimGate.ready(tick)) {
            aimPlan = aim.plan(perceived, profile, adaptation.aimLateralBias(profile));
            aimGate.scheduleNext(tick, profile.millis("reaction.aimMs"),
                    profile.millis("reaction.aimJitterMs"), aimReactionRandom);
            aimUpdated = true;
            if (traceSink.enabled()) traceSink.emit(new TraceEvents.AimPlanUpdated(tick,
                    perceived.tick(), tick - perceived.tick(), TraceVector.of(aimPlan.targetPoint()),
                    aimPlan.errorYaw(), aimPlan.errorPitch(), aimPlan.accuracy(),
                    aimGate.ticksUntilReady(tick)));
        }
        aimEligible = aim.execute(bot, aimPlan, profile);
        if (aimUpdated || !aimEligibilityTraced || aimEligible != lastAimEligibility) {
            if (traceSink.enabled()) traceSink.emit(new TraceEvents.AimExecution(
                    tick, aimEligible, aimGate.ageTicks(tick)));
            lastAimEligibility = aimEligible;
            aimEligibilityTraced = true;
        }

        if (movementGate.ready(tick)) {
            movementPlan = movement.plan(perceived, profile, decision);
            movementGate.scheduleNext(tick, profile.millis("reaction.movementMs"),
                    profile.millis("reaction.movementJitterMs"), movementReactionRandom);
            if (traceSink.enabled()) traceSink.emit(new TraceEvents.MovementPlanUpdated(tick,
                    perceived.tick(), tick - perceived.tick(), movementPlan.forwardX(), movementPlan.forwardZ(),
                    movementPlan.rightX(), movementPlan.rightZ(), movementPlan.forwardSpeed(),
                    movementPlan.incomingCombo(), movementPlan.active(), movement.strafeDirection(),
                    movementGate.ticksUntilReady(tick)));
        }
        movement.execute(bot, movementPlan, arena, profile, tick);

        boolean intendedCriticalWindow = verticalActions.criticalSetupActive() && critical.criticalWindow(bot);
        if (decision == Decision.CRITICAL_ATTACK) {
            if (verticalActions.intentionalJumpStarted(tick)) return;
            if (bot.isOnGround() && critical.tryStart(bot, profile, verticalActions, tick)) {
                emit(new TraceEvents.VerticalActionEvent(tick, VerticalAction.CRITICAL_SETUP, -1,
                        verticalActions.knockbackLockTicksRemaining(tick)));
                return;
            }
            if (Math.abs(bot.getVelocity().getY()) > .02 && !critical.criticalWindow(bot)) return;
        }

        double reach = profile.enabled("reach") ? profile.value("reach.blocks") : 3.0;
        boolean attackDirectionValid = profile.enabled("aim")
                ? aimEligible
                : perceivedFacing(bot, perceived);
        attackPlanner.plan(
                tick,
                attackTiming.lastAttackAttemptTick(),
                perceived,
                decision,
                attackDirectionValid,
                reach,
                intendedCriticalWindow
        ).ifPresent(intent -> {
            emit(new TraceEvents.AttackIntentCreated(tick, intent.sequence(), intent.perceptionTick(),
                    intent.decision(), decisionReason, intent.source(), intent.perceivedDistance(), intent.reach(),
                    intent.perceivedLineOfSight(), intent.intendedCritical()));
            executeAttackIntent(bot, intent);
        });
    }

    /** Capture boundary: every combat-relevant target read enters latency through this snapshot. */
    private PerceptionSnapshot captureObservation(Player bot, ComboTracker combo) {
        Location botBody = bot.getLocation();
        Location targetBody = target.getLocation();
        Location targetEye = target.getEyeLocation();
        Vector botVelocity = bot.getVelocity();
        Vector targetVelocity = target.getVelocity();
        double distance = botBody.distance(targetBody);
        double closingSpeed = Double.isNaN(previousCapturedDistance) ? 0 : CombatFrame.closingSpeed(previousCapturedDistance, distance);
        previousCapturedDistance = distance;
        CombatFrame frame = CombatFrame.from(botBody.getX(), botBody.getZ(), targetBody.getX(), targetBody.getZ());
        RelativeMotion relative = frame.project(targetVelocity.getX(), targetVelocity.getZ());
        return new PerceptionSnapshot(
                tick,
                targetBody,
                targetEye,
                targetVelocity,
                frame,
                distance,
                closingSpeed,
                relative.forwardVelocity(),
                relative.lateralVelocity(),
                targetVelocity.getY(),
                botVelocity.getY(),
                bot.getHealth(),
                target.getHealth(),
                combo.playerCombo(),
                combo.botCombo(),
                tick - lastIncoming,
                attackTiming.ticksSinceSuccessfulOutgoingHit(tick),
                bot.hasLineOfSight(target),
                grounded(bot),
                target.isOnGround()
        );
    }

    /** Aim-disabled cognition compares current bot look only with delayed target position. */
    private boolean perceivedFacing(Player bot, PerceptionSnapshot perceived) {
        Location botEye = bot.getEyeLocation();
        return AimController.isFacing(
                botEye.getDirection(),
                perceived.targetEye().toVector().subtract(botEye.toVector()),
                25
        );
    }

    private void executeAttackIntent(Player bot, AttackIntent intent) {
        lastIntent = intent;
        AttackExecutor.Outcome outcome = attackExecutor.execute(intent, new AttackExecutor.Runtime() {
            @Override public void recordAttempt(AttackIntent consumed) {
                attackTiming.attempted(tick);
                telemetry.botAttackAttempt();
            }

            @Override public void playAttackAnimation() {
                handle.playAttackAnimation(target);
            }

            @Override public AttackExecutionResult probePhysicalContact() {
                return physicalAttackProbe.probe(bot, target, intent.reach());
            }

            @Override public void attackTarget(AttackIntent consumed) {
                executingContactIntent = consumed;
                try {
                    bot.attack(target);
                } finally {
                    executingContactIntent = null;
                }
            }
        });
        lastExecutionResult = outcome.result();
        emit(new TraceEvents.AttackExecuted(tick, intent.sequence(), outcome.result(),
                outcome.attempted(), outcome.attackInvoked()));
        if (outcome.attempted()) movement.afterAttack(bot, profile, tick);
    }

    private static boolean grounded(Player bot) {
        if (bot.isOnGround()) return true;
        if (Math.abs(bot.getVelocity().getY()) > .02) return false;
        return !bot.getLocation().clone().subtract(0, .15, 0).getBlock().isPassable();
    }

    public void incomingHit() {
        lastIncoming = tick;
        verticalActions.incomingHit(profile, tick);
        emit(new TraceEvents.ConfirmedHit(tick, "PLAYER", 0, false));
    }
    public void incomingKnockback(io.papermc.paper.event.entity.EntityKnockbackEvent.Cause cause, Vector externalVelocity) {
        if (KnockbackSignalPolicy.accepts(cause, lastIncoming == tick)) {
            verticalActions.incomingKnockback(tick);
            lastKnockbackLocked = true;
            emit(new TraceEvents.KnockbackStarted(tick, cause.name(),
                    verticalActions.knockbackLockTicksRemaining(tick), TraceVector.of(externalVelocity)));
        }
    }
    /** Called only from the confirmed outgoing EntityDamageByEntityEvent path. */
    public boolean outgoingHit() {
        attackTiming.successfulOutgoingHit(tick);
        boolean intendedCritical = executingContactIntent != null && executingContactIntent.intendedCritical();
        emit(new TraceEvents.ConfirmedHit(tick, "BOT",
                executingContactIntent == null ? 0 : executingContactIntent.sequence(), intendedCritical));
        return intendedCritical;
    }
    public Decision decision() { return decision; }
    public int perceptionAgeTicks() { return Math.max(0, profile.millis("simulatedPingMs") / 50); }
    public int strafeDirection() { return movement.strafeDirection(); }
    public double distance() { return latestPerceived == null ? 0 : latestPerceived.distance(); }
    public double cooldown() { return attackTiming.cooldown(tick); }
    public double adaptationConfidence() { return adaptation.model().confidence(); }
    public boolean sprinting() { Player bot = handle.entity(); return bot != null && bot.isSprinting(); }
    public long decisionPlanAgeTicks() { return decisionGate.ageTicks(tick); }
    public long aimPlanAgeTicks() { return aimGate.ageTicks(tick); }
    public long movementPlanAgeTicks() { return movementGate.ageTicks(tick); }
    public long decisionTicksUntilUpdate() { return decisionGate.ticksUntilReady(tick); }
    public long aimTicksUntilUpdate() { return aimGate.ticksUntilReady(tick); }
    public long movementTicksUntilUpdate() { return movementGate.ticksUntilReady(tick); }
    public long watchdogIntentCount() { return attackPlanner.watchdogIntentCount(); }
    public AttackIntent lastIntent() { return lastIntent; }
    public AttackExecutionResult lastExecutionResult() { return lastExecutionResult; }
    public long lastAttackAttemptTick() { return attackTiming.lastAttackAttemptTick(); }
    public long lastSuccessfulOutgoingHitTick() { return attackTiming.lastSuccessfulOutgoingHitTick(); }
    public VerticalAction verticalAction() { return verticalActions.verticalAction(); }
    public DecisionReason decisionReason() { return decisionReason; }
    public long jumpResetOpportunities() { return verticalActions.jumpResetOpportunities(); }
    public long jumpResetExecutions() { return verticalActions.jumpResetExecutions(); }
    public int knockbackLockTicksRemaining() { return verticalActions.knockbackLockTicksRemaining(tick); }
    public long lastIntentPerceptionAgeTicks() {
        return lastIntent == null ? 0 : Math.max(0, lastIntent.creationTick() - lastIntent.perceptionTick());
    }

    public void traceSink(CombatTraceSink sink) {
        traceSink = sink == null ? NoopCombatTraceSink.INSTANCE : sink;
    }

    private void emit(dev.pvpbot.bot.trace.CombatTraceEvent event) {
        if (traceSink.enabled()) traceSink.emit(event);
    }

    private static TraceEvents.PerceptionCaptured perceptionCaptured(PerceptionSnapshot snapshot) {
        return new TraceEvents.PerceptionCaptured(snapshot.tick(), snapshot.distance(), snapshot.closingSpeed(),
                snapshot.forwardVelocity(), snapshot.lateralVelocity(), snapshot.playerVerticalVelocity(),
                snapshot.botVerticalVelocity(), snapshot.botHealth(), snapshot.playerHealth(),
                snapshot.incomingCombo(), snapshot.outgoingCombo(), snapshot.ticksSinceIncomingHit(),
                snapshot.ticksSinceOutgoingHit(), snapshot.lineOfSight(), snapshot.botOnGround(),
                snapshot.playerOnGround());
    }
}
