package dev.pvpbot.qa;

import dev.pvpbot.bot.ai.adaptation.AdaptationController;
import dev.pvpbot.bot.ai.perception.CombatFrame;
import dev.pvpbot.bot.ai.perception.LatencyBuffer;
import dev.pvpbot.bot.ai.perception.PerceptionSnapshot;
import dev.pvpbot.bot.ai.random.MatchRandom;
import dev.pvpbot.bot.ai.random.MatchRandom.Subsystem;
import dev.pvpbot.bot.ai.reaction.ReactionGate;
import dev.pvpbot.bot.combat.AimController;
import dev.pvpbot.bot.combat.AimController.AimPlan;
import dev.pvpbot.bot.combat.AimController.Rotation;
import dev.pvpbot.bot.combat.attack.AttackExecutionResult;
import dev.pvpbot.bot.combat.attack.AttackExecutor;
import dev.pvpbot.bot.combat.attack.AttackIntent;
import dev.pvpbot.bot.combat.attack.AttackIntentPlanner;
import dev.pvpbot.bot.combat.attack.AttackTiming;
import dev.pvpbot.bot.combat.hitselect.HitSelectController;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.Decision;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.DecisionReason;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.DecisionResult;
import dev.pvpbot.bot.movement.MovementController;
import dev.pvpbot.bot.movement.MovementController.MovementPlan;
import dev.pvpbot.bot.movement.VerticalAction;
import dev.pvpbot.bot.movement.VerticalActionController;
import dev.pvpbot.bot.profile.BotProfile;
import dev.pvpbot.bot.trace.CombatTraceEvent;
import dev.pvpbot.bot.trace.CombatTraceSink;
import dev.pvpbot.bot.trace.InMemoryCombatTraceSink;
import dev.pvpbot.bot.trace.NoopCombatTraceSink;
import dev.pvpbot.bot.trace.TraceEvents;
import dev.pvpbot.bot.trace.TraceVector;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

public final class CombatScenarioRunner {
    public static final class Metrics {
        public long ticks;
        public long attempts;
        public long hits;
        public long contacts;
        public long whiffs;
        public long targetInvalid;
        public long watchdogIntents;
        public long jumpResetOpportunities;
        public long jumpResetExecutions;
        public long criticalSetups;
        public long knockbackLocks;
        public long perceptionAgeTotal;
        public long perceptionMaturations;
        public long decisionUpdates;
        public long aimUpdates;
        public long movementUpdates;
        public final Map<Decision, Long> decisions = new EnumMap<>(Decision.class);
        public final Map<DecisionReason, Long> reasons = new EnumMap<>(DecisionReason.class);

        public double averagePerceptionAge() {
            return perceptionMaturations == 0 ? 0 : (double) perceptionAgeTotal / perceptionMaturations;
        }
    }

    public record Result(String scenario, long matchSeed, List<QaFrame> frames,
                         List<CombatTraceEvent> trace, List<Decision> decisionTimeline,
                         Metrics metrics, List<CombatInvariantEngine.Failure> failures) {}

    public Result run(CombatScenario scenario, CombatTraceSink requestedSink) {
        CombatTraceSink sink = requestedSink == null ? NoopCombatTraceSink.INSTANCE : requestedSink;
        BotProfile profile = scenario.profile();
        MatchRandom matchRandom = new MatchRandom(scenario.matchSeed());
        RandomGenerator techniqueRandom = matchRandom.stream(Subsystem.TECHNIQUE);
        RandomGenerator criticalRandom = matchRandom.stream(Subsystem.CRITICAL);
        HitSelectController hitSelect = new HitSelectController();
        AdaptationController adaptation = new AdaptationController();
        LatencyBuffer<PerceptionSnapshot> latency = new LatencyBuffer<>();
        ReactionGate decisionGate = new ReactionGate();
        ReactionGate aimGate = new ReactionGate();
        ReactionGate movementGate = new ReactionGate();
        AimController aim = new AimController(matchRandom.stream(Subsystem.AIM));
        VerticalActionController vertical = new VerticalActionController(techniqueRandom);
        AttackIntentPlanner planner = new AttackIntentPlanner();
        AttackExecutor executor = new AttackExecutor();
        AttackTiming timing = new AttackTiming();
        RandomGenerator decisionReaction = matchRandom.stream(Subsystem.DECISION_REACTION);
        RandomGenerator aimReaction = matchRandom.stream(Subsystem.AIM_REACTION);
        RandomGenerator movementReaction = matchRandom.stream(Subsystem.MOVEMENT_REACTION);
        List<QaFrame> frames = new ArrayList<>();
        List<Decision> decisionTimeline = new ArrayList<>();
        Metrics metrics = new Metrics();
        Decision decision = Decision.WAIT;
        DecisionReason reason = DecisionReason.COOLDOWN_DISCIPLINE_WAIT;
        AimPlan aimPlan = null;
        MovementPlan movementPlan = null;
        long decisionPerceptionTick = -1;
        long aimPerceptionTick = -1;
        long movementPerceptionTick = -1;
        long lastIncoming = -100;
        long lastMatured = Long.MIN_VALUE;
        long criticalSetups = 0;
        float yaw = 0;
        float pitch = 0;
        int attempts = 0;
        int hits = 0;
        boolean previousKbLock = false;

        emit(sink, new TraceEvents.MatchStart(0, "1.0.11", scenario.name(), scenario.matchSeed(),
                profile.name(), profile.values(), profile.toggles()));

        for (int index = 0; index < scenario.ticks().size(); index++) {
            long tick = index + 1L;
            CombatScenario.TickInput input = scenario.ticks().get(index);
            metrics.ticks++;
            if (input.incomingHit()) {
                lastIncoming = tick;
                vertical.incomingHit(profile, tick);
                emit(sink, new TraceEvents.ConfirmedHit(tick, "PLAYER", 0, false));
            }
            if (input.knockback()) {
                vertical.incomingKnockback(tick);
                metrics.knockbackLocks++;
                emit(sink, new TraceEvents.KnockbackStarted(tick, "QA_MELEE", 4,
                        new TraceVector(.35, .12, -.2)));
            }
            vertical.beginTick(tick);
            boolean kbLocked = vertical.knockbackLocked(tick);
            if (previousKbLock && !kbLocked) emit(sink, new TraceEvents.KnockbackEnded(tick));
            previousKbLock = kbLocked;
            vertical.observeGrounded(input.botGrounded(), tick);
            long jumpBefore = vertical.jumpResetExecutions();
            vertical.tryJumpReset(input.botGrounded(), tick, () -> {});
            if (vertical.jumpResetExecutions() > jumpBefore) {
                emit(sink, new TraceEvents.VerticalActionEvent(tick, VerticalAction.JUMP_RESET,
                        lastIncoming, vertical.knockbackLockTicksRemaining(tick)));
            }

            PerceptionSnapshot captured = snapshot(tick, input, lastIncoming,
                    timing.ticksSinceSuccessfulOutgoingHit(tick));
            emit(sink, perceptionCaptured(captured));
            long nowMs = tick * ReactionGate.NOMINAL_TICK_MS;
            latency.offer(nowMs, profile.millis("simulatedPingMs"), captured);
            Optional<PerceptionSnapshot> matured = latency.poll(nowMs);
            boolean decisionUpdated = false;
            boolean aimUpdated = false;
            boolean movementUpdated = false;
            boolean movementWrite = false;
            boolean sTapWrite = false;
            boolean externalImpulse = kbLocked || input.knockback();
            double horizontalX = input.knockback() ? .35 : 0;
            double horizontalZ = input.knockback() ? -.2 : 0;
            long sequence = 0;
            boolean attemptedThisTick = false;
            int animationsThisTick = 0;
            boolean meleeThisTick = false;
            AttackExecutionResult executionResult = null;
            boolean confirmedThisTick = false;
            boolean intendedCritical = false;
            boolean watchdog = false;
            boolean cadenceAllowed = true;
            long latestPerceptionTick = matured.map(PerceptionSnapshot::tick).orElse(-1L);

            if (matured.isPresent()) {
                PerceptionSnapshot perceived = matured.get();
                adaptation.observe(perceived);
                if (perceived.tick() != lastMatured) {
                    long age = tick - perceived.tick();
                    metrics.perceptionMaturations++;
                    metrics.perceptionAgeTotal += age;
                    emit(sink, new TraceEvents.PerceptionMatured(tick, perceived.tick(), age,
                            profile.millis("simulatedPingMs")));
                    lastMatured = perceived.tick();
                }
                if (decisionGate.ready(tick)) {
                    DecisionResult selected = hitSelect.decide(perceived, profile,
                            timing.cooldown(tick), adaptation.aggression(profile));
                    decision = selected.decision();
                    reason = selected.reason();
                    decisionPerceptionTick = perceived.tick();
                    decisionTimeline.add(decision);
                    decisionUpdated = true;
                    metrics.decisionUpdates++;
                    metrics.decisions.merge(decision, 1L, Long::sum);
                    metrics.reasons.merge(reason, 1L, Long::sum);
                    decisionGate.scheduleNext(tick, profile.millis("reaction.decisionMs"),
                            profile.millis("reaction.decisionJitterMs"), decisionReaction);
                    emit(sink, new TraceEvents.DecisionUpdated(tick, perceived.tick(),
                            tick - perceived.tick(), decision, reason, selected.inputs(), 0,
                            aimGate.ageTicks(tick), movementGate.ageTicks(tick),
                            decisionGate.ticksUntilReady(tick), aimGate.ticksUntilReady(tick),
                            movementGate.ticksUntilReady(tick), adaptation.model().confidence(),
                            adaptation.model().aggression(), adaptation.model().lateralBias(),
                            adaptation.model().jumpRate()));
                }
                if (aimGate.ready(tick)) {
                    aimPlan = aim.plan(perceived, profile, adaptation.aimLateralBias(profile));
                    aimPerceptionTick = perceived.tick();
                    aimUpdated = true;
                    metrics.aimUpdates++;
                    aimGate.scheduleNext(tick, profile.millis("reaction.aimMs"),
                            profile.millis("reaction.aimJitterMs"), aimReaction);
                    emit(sink, new TraceEvents.AimPlanUpdated(tick, perceived.tick(),
                            tick - perceived.tick(), TraceVector.of(aimPlan.targetPoint()),
                            aimPlan.errorYaw(), aimPlan.errorPitch(), aimPlan.accuracy(),
                            aimGate.ticksUntilReady(tick)));
                }
                boolean aimEligible = input.forceAimEligible();
                if (aimPlan != null) {
                    Rotation rotation = AimController.nextRotation(aimPlan, new Vector(0, 65.62, 0),
                            yaw, pitch, profile.value("aim.maxYawSpeed"), profile.value("aim.maxPitchSpeed"));
                    yaw = rotation.yaw();
                    pitch = rotation.pitch();
                    aimEligible |= AimController.withinAimTolerance(rotation, aimPlan.accuracy());
                    if (aimUpdated) emit(sink, new TraceEvents.AimExecution(tick, aimEligible,
                            aimGate.ageTicks(tick)));
                }
                if (movementGate.ready(tick)) {
                    movementPlan = new MovementController(matchRandom.stream(Subsystem.MOVEMENT), techniqueRandom)
                            .plan(perceived, profile, decision);
                    movementPerceptionTick = perceived.tick();
                    movementUpdated = true;
                    metrics.movementUpdates++;
                    movementGate.scheduleNext(tick, profile.millis("reaction.movementMs"),
                            profile.millis("reaction.movementJitterMs"), movementReaction);
                    emit(sink, new TraceEvents.MovementPlanUpdated(tick, perceived.tick(),
                            tick - perceived.tick(), movementPlan.forwardX(), movementPlan.forwardZ(),
                            movementPlan.rightX(), movementPlan.rightZ(), movementPlan.forwardSpeed(),
                            movementPlan.incomingCombo(), movementPlan.active(), 1,
                            movementGate.ticksUntilReady(tick)));
                }
                if (movementPlan != null && movementPlan.active()) {
                    if (input.arenaEdgeRecovery()) {
                        Vector recovery = MovementController.clampHorizontal(new Vector(.32, 0, 0),
                                MovementController.MAX_HORIZONTAL_SPEED);
                        horizontalX = recovery.getX();
                        horizontalZ = recovery.getZ();
                        movementWrite = true;
                        externalImpulse = false;
                    } else if (!kbLocked) {
                        Vector move = MovementController.plannedHorizontalVelocity(movementPlan, 1, false,
                                profile.value("strafe.intensity"));
                        MovementController.clampHorizontal(move, MovementController.MAX_HORIZONTAL_SPEED);
                        horizontalX = move.getX();
                        horizontalZ = move.getZ();
                        movementWrite = true;
                        externalImpulse = false;
                    }
                }

                boolean fallingCriticalWindow = vertical.criticalSetupActive()
                        && !input.botGrounded() && input.botVerticalVelocity() < 0;
                if (decision == Decision.CRITICAL_ATTACK) {
                    if (vertical.intentionalJumpStarted(tick)) {
                        frames.add(frame(scenario.name(), tick, attempts, hits, sequence, false, 0,
                                false, null, false, timing.lastSuccessfulOutgoingHitTick(), vertical,
                                criticalSetups, false, kbLocked, movementWrite, sTapWrite,
                                input.arenaEdgeRecovery(), externalImpulse, horizontalX, horizontalZ,
                                decision, reason, latestPerceptionTick, decisionPerceptionTick,
                                aimPerceptionTick, movementPerceptionTick, decisionUpdated, aimUpdated,
                                movementUpdated, false, true));
                        continue;
                    }
                    if (input.botGrounded() && criticalRandom.nextDouble()
                            <= profile.value("criticals.chance") * profile.value("criticals.skill")) {
                        vertical.criticalSetup(tick, () -> {});
                        criticalSetups++;
                        metrics.criticalSetups++;
                        emit(sink, new TraceEvents.VerticalActionEvent(tick, VerticalAction.CRITICAL_SETUP,
                                -1, vertical.knockbackLockTicksRemaining(tick)));
                        frames.add(frame(scenario.name(), tick, attempts, hits, sequence, false, 0,
                                false, null, false, timing.lastSuccessfulOutgoingHitTick(), vertical,
                                criticalSetups, false, kbLocked, movementWrite, sTapWrite,
                                input.arenaEdgeRecovery(), externalImpulse, horizontalX, horizontalZ,
                                decision, reason, latestPerceptionTick, decisionPerceptionTick,
                                aimPerceptionTick, movementPerceptionTick, decisionUpdated, aimUpdated,
                                movementUpdated, false, true));
                        continue;
                    }
                    if (Math.abs(input.botVerticalVelocity()) > .02 && !fallingCriticalWindow) {
                        frames.add(frame(scenario.name(), tick, attempts, hits, sequence, false, 0,
                                false, null, false, timing.lastSuccessfulOutgoingHitTick(), vertical,
                                criticalSetups, false, kbLocked, movementWrite, sTapWrite,
                                input.arenaEdgeRecovery(), externalImpulse, horizontalX, horizontalZ,
                                decision, reason, latestPerceptionTick, decisionPerceptionTick,
                                aimPerceptionTick, movementPerceptionTick, decisionUpdated, aimUpdated,
                                movementUpdated, false, true));
                        continue;
                    }
                }
                intendedCritical = fallingCriticalWindow;
                long previousAttemptTick = timing.lastAttackAttemptTick();
                Optional<AttackIntent> planned = planner.plan(tick, previousAttemptTick, perceived,
                        decision, aimEligible, profile.enabled("reach") ? profile.value("reach.blocks") : 3.0,
                        intendedCritical);
                if (planned.isPresent()) {
                    AttackIntent intent = planned.get();
                    sequence = intent.sequence();
                    watchdog = intent.source() == AttackIntent.Source.WATCHDOG;
                    cadenceAllowed = tick - previousAttemptTick >= AttackIntentPlanner.ATTEMPT_CADENCE_TICKS;
                    emit(sink, new TraceEvents.AttackIntentCreated(tick, sequence, intent.perceptionTick(),
                            intent.decision(), reason, intent.source(), intent.perceivedDistance(), intent.reach(),
                            intent.perceivedLineOfSight(), intent.intendedCritical()));
                    int[] tickAttempts = {0};
                    int[] tickAnimations = {0};
                    boolean[] tickMelee = {false};
                    boolean[] tickConfirmed = {false};
                    int[] hitCounter = {hits};
                    AttackExecutor.Outcome outcome = executor.execute(intent, new AttackExecutor.Runtime() {
                        @Override public void recordAttempt(AttackIntent consumed) {
                            timing.attempted(tick);
                            tickAttempts[0]++;
                        }
                        @Override public void playAttackAnimation() { tickAnimations[0]++; }
                        @Override public AttackExecutionResult probePhysicalContact() { return input.executionResult(); }
                        @Override public void attackTarget(AttackIntent consumed) {
                            tickMelee[0] = true;
                            if (input.confirmDamage()) {
                                hitCounter[0]++;
                                tickConfirmed[0] = true;
                                timing.successfulOutgoingHit(tick);
                                emit(sink, new TraceEvents.ConfirmedHit(tick, "BOT", consumed.sequence(),
                                        consumed.intendedCritical()));
                            }
                        }
                    });
                    attempts += tickAttempts[0];
                    hits = hitCounter[0];
                    attemptedThisTick = outcome.attempted();
                    animationsThisTick = tickAnimations[0];
                    meleeThisTick = tickMelee[0];
                    executionResult = outcome.result();
                    confirmedThisTick = tickConfirmed[0];
                    emit(sink, new TraceEvents.AttackExecuted(tick, sequence, outcome.result(),
                            outcome.attempted(), outcome.attackInvoked()));
                    metrics.attempts += tickAttempts[0];
                    metrics.hits += tickConfirmed[0] ? 1 : 0;
                    if (outcome.result() == AttackExecutionResult.CONTACT) metrics.contacts++;
                    if (outcome.result() == AttackExecutionResult.WHIFF) metrics.whiffs++;
                    if (outcome.result() == AttackExecutionResult.TARGET_INVALID) metrics.targetInvalid++;
                    if (watchdog) metrics.watchdogIntents++;
                }
            }

            frames.add(frame(scenario.name(), tick, attempts, hits, sequence, attemptedThisTick,
                    animationsThisTick, meleeThisTick, executionResult, confirmedThisTick,
                    timing.lastSuccessfulOutgoingHitTick(), vertical, criticalSetups, intendedCritical,
                    kbLocked, movementWrite, sTapWrite, input.arenaEdgeRecovery(), externalImpulse,
                    horizontalX, horizontalZ, decision, reason, latestPerceptionTick,
                    decisionPerceptionTick, aimPerceptionTick, movementPerceptionTick,
                    decisionUpdated, aimUpdated, movementUpdated, watchdog, cadenceAllowed));
        }

        metrics.jumpResetOpportunities = vertical.jumpResetOpportunities();
        metrics.jumpResetExecutions = vertical.jumpResetExecutions();
        emit(sink, new TraceEvents.MatchEnd(scenario.ticks().size(), "QA_COMPLETE", attempts, hits,
                Math.max(0, attempts - hits), planner.watchdogIntentCount(),
                vertical.jumpResetOpportunities(), vertical.jumpResetExecutions(), sink.droppedEvents()));
        List<CombatInvariantEngine.Failure> failures = new ArrayList<>(new CombatInvariantEngine().validate(frames));
        Result provisional = new Result(scenario.name(), scenario.matchSeed(), List.copyOf(frames),
                traceOf(sink), List.copyOf(decisionTimeline), metrics, List.copyOf(failures));
        for (String expectationFailure : scenario.expectation().validate(provisional)) {
            failures.add(new CombatInvariantEngine.Failure("scenario expectation: " + expectationFailure,
                    scenario.ticks().size(), "scenario=" + scenario.name()));
        }
        return new Result(scenario.name(), scenario.matchSeed(), List.copyOf(frames), traceOf(sink),
                List.copyOf(decisionTimeline), metrics, List.copyOf(failures));
    }

    private static QaFrame frame(String scenario, long tick, int attempts, int hits, long sequence,
                                 boolean attempted, int animations, boolean melee,
                                 AttackExecutionResult result, boolean confirmed, long lastHitTick,
                                 VerticalActionController vertical, long criticalSetups,
                                 boolean intendedCritical, boolean kbLocked, boolean movementWrite,
                                 boolean sTapWrite, boolean recovery, boolean impulse,
                                 double horizontalX, double horizontalZ, Decision decision,
                                 DecisionReason reason, long latestPerceptionTick,
                                 long decisionPerceptionTick, long aimPerceptionTick,
                                 long movementPerceptionTick, boolean decisionUpdated,
                                 boolean aimUpdated, boolean movementUpdated, boolean watchdog,
                                 boolean cadenceAllowed) {
        return new QaFrame(scenario, tick, attempts, hits, Math.max(0, attempts - hits), sequence,
                attempted, animations, melee, result, confirmed, lastHitTick,
                vertical.jumpResetOpportunities(), vertical.jumpResetChanceSamples(),
                vertical.jumpResetExecutions(), criticalSetups, intendedCritical,
                vertical.verticalAction(), kbLocked, movementWrite, sTapWrite, recovery, impulse,
                horizontalX, horizontalZ, decision, reason, latestPerceptionTick,
                decisionPerceptionTick, aimPerceptionTick, movementPerceptionTick,
                decisionUpdated, aimUpdated, movementUpdated, watchdog, cadenceAllowed);
    }

    private static PerceptionSnapshot snapshot(long tick, CombatScenario.TickInput input,
                                               long lastIncoming, long ticksSinceOutgoing) {
        Location body = new Location(null, 0, 64, input.distance());
        Location eye = body.clone().add(0, 1.62, 0);
        Vector velocity = new Vector(input.lateralVelocity(), input.targetVerticalVelocity(),
                input.forwardVelocity());
        CombatFrame frame = CombatFrame.from(0, 0, 0, input.distance());
        return new PerceptionSnapshot(tick, body, eye, velocity, frame, input.distance(),
                input.closingSpeed(), input.forwardVelocity(), input.lateralVelocity(),
                input.targetVerticalVelocity(), input.botVerticalVelocity(), 20, 20,
                input.incomingCombo(), input.outgoingCombo(), tick - lastIncoming,
                ticksSinceOutgoing, input.lineOfSight(), input.botGrounded(), input.targetGrounded());
    }

    private static TraceEvents.PerceptionCaptured perceptionCaptured(PerceptionSnapshot snapshot) {
        return new TraceEvents.PerceptionCaptured(snapshot.tick(), snapshot.distance(), snapshot.closingSpeed(),
                snapshot.forwardVelocity(), snapshot.lateralVelocity(), snapshot.playerVerticalVelocity(),
                snapshot.botVerticalVelocity(), snapshot.botHealth(), snapshot.playerHealth(),
                snapshot.incomingCombo(), snapshot.outgoingCombo(), snapshot.ticksSinceIncomingHit(),
                snapshot.ticksSinceOutgoingHit(), snapshot.lineOfSight(), snapshot.botOnGround(),
                snapshot.playerOnGround());
    }

    private static void emit(CombatTraceSink sink, CombatTraceEvent event) {
        if (sink.enabled()) sink.emit(event);
    }

    private static List<CombatTraceEvent> traceOf(CombatTraceSink sink) {
        return sink instanceof InMemoryCombatTraceSink memory ? memory.events() : List.of();
    }
}
