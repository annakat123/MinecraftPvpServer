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
import dev.pvpbot.bot.entity.BotHandle;
import dev.pvpbot.bot.movement.MovementController;
import dev.pvpbot.bot.movement.MovementController.MovementPlan;
import dev.pvpbot.bot.profile.BotProfile;
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

    private long tick;
    private long lastIncoming = -100;
    private double previousCapturedDistance = Double.NaN;
    private PerceptionSnapshot latestPerceived;
    private Decision decision = Decision.WAIT;
    private AimPlan aimPlan;
    private MovementPlan movementPlan;
    private boolean aimEligible;
    private AttackIntent lastIntent;
    private AttackIntent executingContactIntent;
    private AttackExecutionResult lastExecutionResult;

    public BotBrain(BotHandle handle, Player target, Arena arena, BotProfile profile, MatchRandom random, Telemetry telemetry) {
        this.handle = handle;
        this.target = target;
        this.arena = arena;
        this.profile = profile;
        this.telemetry = telemetry;
        decisionReactionRandom = random.stream(Subsystem.DECISION_REACTION);
        aimReactionRandom = random.stream(Subsystem.AIM_REACTION);
        movementReactionRandom = random.stream(Subsystem.MOVEMENT_REACTION);
        aim = new AimController(random.stream(Subsystem.AIM));
        critical = new CriticalController(random.stream(Subsystem.CRITICAL));
        movement = new MovementController(random.stream(Subsystem.MOVEMENT), random.stream(Subsystem.TECHNIQUE));
        physicalAttackProbe = new PaperPhysicalAttackProbe();
    }

    public void tick(ComboTracker combo) {
        tick++;
        combo.expire(org.bukkit.Bukkit.getCurrentTick(), 25);
        Player bot = handle.entity();
        if (bot == null || !bot.isValid() || !bot.isInWorld() || bot.isDead()) return;

        long now = System.currentTimeMillis();
        PerceptionSnapshot captured = captureObservation(bot, combo);
        latency.offer(now, profile.millis("simulatedPingMs"), captured);
        Optional<PerceptionSnapshot> matured = latency.poll(now);
        if (matured.isEmpty()) return;

        PerceptionSnapshot perceived = matured.get();
        latestPerceived = perceived;
        adaptation.observe(perceived);

        if (decisionGate.ready(tick)) {
            double cooldown = attackTiming.cooldown(tick);
            decision = hitSelect.decide(perceived, profile, cooldown, adaptation.aggression(profile));
            decisionGate.scheduleNext(tick, profile.millis("reaction.decisionMs"),
                    profile.millis("reaction.decisionJitterMs"), decisionReactionRandom);
        }

        if (aimGate.ready(tick)) {
            aimPlan = aim.plan(perceived, profile, adaptation.aimLateralBias(profile));
            aimGate.scheduleNext(tick, profile.millis("reaction.aimMs"),
                    profile.millis("reaction.aimJitterMs"), aimReactionRandom);
        }
        aimEligible = aim.execute(bot, aimPlan, profile);

        if (movementGate.ready(tick)) {
            movementPlan = movement.plan(perceived, profile, decision);
            movementGate.scheduleNext(tick, profile.millis("reaction.movementMs"),
                    profile.millis("reaction.movementJitterMs"), movementReactionRandom);
        }
        movement.execute(bot, movementPlan, arena, profile, tick);

        if (decision == Decision.CRITICAL_ATTACK) {
            if (bot.isOnGround() && critical.tryStart(bot, profile)) return;
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
                critical.criticalWindow(bot)
        ).ifPresent(intent -> executeAttackIntent(bot, intent));
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

            @Override public void swingMainHand() {
                bot.swingMainHand();
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
        if (outcome.attempted()) movement.afterAttack(bot, profile);
    }

    private static boolean grounded(Player bot) {
        if (bot.isOnGround()) return true;
        if (Math.abs(bot.getVelocity().getY()) > .02) return false;
        return !bot.getLocation().clone().subtract(0, .15, 0).getBlock().isPassable();
    }

    public void incomingHit() { lastIncoming = tick; }
    /** Called only from the confirmed outgoing EntityDamageByEntityEvent path. */
    public boolean outgoingHit() {
        attackTiming.successfulOutgoingHit(tick);
        return executingContactIntent != null && executingContactIntent.intendedCritical();
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
    public long lastIntentPerceptionAgeTicks() {
        return lastIntent == null ? 0 : Math.max(0, lastIntent.creationTick() - lastIntent.perceptionTick());
    }
}
