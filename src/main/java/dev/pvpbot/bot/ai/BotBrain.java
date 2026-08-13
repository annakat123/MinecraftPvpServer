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
    public interface Telemetry { void botAttackAttempt(boolean connected, boolean critical); }

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
    private final AdaptationController adaptation = new AdaptationController();
    private final ReactionGate decisionGate = new ReactionGate();
    private final ReactionGate aimGate = new ReactionGate();
    private final ReactionGate movementGate = new ReactionGate();
    private final RandomGenerator decisionReactionRandom;
    private final RandomGenerator aimReactionRandom;
    private final RandomGenerator movementReactionRandom;

    private long tick;
    private long lastAttack = -100;
    private long lastIncoming = -100;
    private long lastOutgoing = -100;
    private double previousCapturedDistance = Double.NaN;
    private PerceptionSnapshot latestPerceived;
    private Decision decision = Decision.WAIT;
    private AimPlan aimPlan;
    private MovementPlan movementPlan;
    private boolean aimEligible;

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
    }

    public void tick(ComboTracker combo) {
        tick++;
        combo.expire(org.bukkit.Bukkit.getCurrentTick(), 25);
        Player bot = handle.entity();
        if (bot == null || !target.isOnline()) return;

        long now = System.currentTimeMillis();
        PerceptionSnapshot captured = captureObservation(bot, combo);
        latency.offer(now, profile.millis("simulatedPingMs"), captured);
        Optional<PerceptionSnapshot> matured = latency.poll(now);
        if (matured.isEmpty()) return;

        PerceptionSnapshot perceived = matured.get();
        latestPerceived = perceived;
        adaptation.observe(perceived);

        if (decisionGate.ready(tick)) {
            double cooldown = Math.min(1, (tick - lastAttack) / 12.5);
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
        boolean attackWatchdog = tick - lastAttack >= 30
                && perceived.distance() <= reach
                && perceived.lineOfSight();
        boolean attackDirectionValid = profile.enabled("aim")
                ? aimEligible
                : liveFacingForExecution(bot);
        boolean liveAttackValid = liveAttackValid(bot, reach);
        if ((isAttack(decision) || attackWatchdog)
                && tick - lastAttack >= 10
                && perceived.distance() <= reach
                && perceived.lineOfSight()
                && attackDirectionValid
                && liveAttackValid) {
            boolean crit = critical.criticalWindow(bot);
            bot.attack(target);
            bot.swingMainHand();
            lastAttack = tick;
            lastOutgoing = tick;
            movement.afterAttack(bot, profile);
            telemetry.botAttackAttempt(true, crit);
        }
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
                tick - lastOutgoing,
                bot.hasLineOfSight(target),
                grounded(bot),
                target.isOnGround()
        );
    }

    /** Live target direction is retained only as a final physical execution validation. */
    private boolean liveFacingForExecution(Player bot) {
        Location botEye = bot.getEyeLocation();
        return AimController.isFacing(
                botEye.getDirection(),
                target.getEyeLocation().toVector().subtract(botEye.toVector()),
                25
        );
    }

    /** Current reach/line-of-sight are server-side execution checks, not AI inputs. */
    private boolean liveAttackValid(Player bot, double reach) {
        return bot.getLocation().distance(target.getLocation()) <= reach + .1 && bot.hasLineOfSight(target);
    }

    private static boolean isAttack(Decision decision) {
        return decision == Decision.ATTACK_NOW
                || decision == Decision.COUNTER_HIT
                || decision == Decision.CRITICAL_ATTACK;
    }

    private static boolean grounded(Player bot) {
        if (bot.isOnGround()) return true;
        if (Math.abs(bot.getVelocity().getY()) > .02) return false;
        return !bot.getLocation().clone().subtract(0, .15, 0).getBlock().isPassable();
    }

    public void incomingHit() { lastIncoming = tick; }
    public void outgoingHit() { lastOutgoing = tick; }
    public Decision decision() { return decision; }
    public int perceptionAgeTicks() { return Math.max(0, profile.millis("simulatedPingMs") / 50); }
    public int strafeDirection() { return movement.strafeDirection(); }
    public double distance() { return latestPerceived == null ? 0 : latestPerceived.distance(); }
    public double cooldown() { return Math.min(1, (tick - lastAttack) / 12.5); }
    public double adaptationConfidence() { return adaptation.model().confidence(); }
    public boolean sprinting() { Player bot = handle.entity(); return bot != null && bot.isSprinting(); }
    public long decisionPlanAgeTicks() { return decisionGate.ageTicks(tick); }
    public long aimPlanAgeTicks() { return aimGate.ageTicks(tick); }
    public long movementPlanAgeTicks() { return movementGate.ageTicks(tick); }
    public long decisionTicksUntilUpdate() { return decisionGate.ticksUntilReady(tick); }
    public long aimTicksUntilUpdate() { return aimGate.ticksUntilReady(tick); }
    public long movementTicksUntilUpdate() { return movementGate.ticksUntilReady(tick); }
}
