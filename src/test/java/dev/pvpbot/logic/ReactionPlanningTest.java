package dev.pvpbot.logic;

import dev.pvpbot.bot.ai.adaptation.AdaptationController;
import dev.pvpbot.bot.ai.perception.CombatFrame;
import dev.pvpbot.bot.ai.perception.PerceptionSnapshot;
import dev.pvpbot.bot.ai.reaction.ReactionGate;
import dev.pvpbot.bot.combat.AimController;
import dev.pvpbot.bot.combat.AimController.AimPlan;
import dev.pvpbot.bot.combat.AimController.Rotation;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.Decision;
import dev.pvpbot.bot.movement.MovementController;
import dev.pvpbot.bot.movement.MovementController.MovementPlan;
import dev.pvpbot.bot.profile.BotProfile;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

class ReactionPlanningTest {
    @Test void aimKeepsExecutingSnapshotAPlanUntilItsOwnGateOpens() {
        BotProfile profile = new BotProfile("test", Map.of(
                "aim.accuracy", 1d,
                "aim.predictionStrength", 1d
        ), Map.of());
        AimController aim = new AimController(new SplittableRandom(10));
        ReactionGate gate = new ReactionGate();
        AdaptationController adaptation = new AdaptationController();
        PerceptionSnapshot movingRight = snapshot(1, 0, 4, -.3, 0);
        PerceptionSnapshot movingLeft = snapshot(2, 0, 4, .3, 0);

        assertTrue(gate.ready(10));
        AimPlan held = aim.plan(movingRight, profile, 0);
        Vector targetHeldFromA = held.targetPoint();
        gate.scheduleNext(10, 150, 0, new SplittableRandom(20));
        assertTrue(held.targetPoint().getX() < 0);

        adaptation.observe(movingLeft);
        assertTrue(adaptation.model().confidence() > 0, "new matured perception is still learned");
        assertFalse(gate.ready(11));
        Rotation first = AimController.nextRotation(held, new Vector(0, 65.62, 0), 0, 0, 3, 3);
        Rotation second = AimController.nextRotation(held, new Vector(0, 65.62, 0), first.yaw(), first.pitch(), 3, 3);
        assertTrue(Math.abs(second.yaw()) > Math.abs(first.yaw()), "held aim plan continues smooth motor progress");
        assertEquals(targetHeldFromA, held.targetPoint(), "snapshot B did not replace the held plan");

        assertTrue(gate.ready(13));
        AimPlan replanned = aim.plan(movingLeft, profile, 0);
        assertTrue(replanned.targetPoint().getX() > 0);
    }

    @Test void movementKeepsExecutingSnapshotAIntentUntilItsOwnGateOpens() {
        BotProfile profile = BotProfile.defaults("test");
        MovementController movement = new MovementController(new SplittableRandom(1), new SplittableRandom(2));
        ReactionGate gate = new ReactionGate();
        PerceptionSnapshot south = snapshot(1, 0, 4, 0, 0);
        PerceptionSnapshot east = snapshot(2, 4, 0, 0, 0);

        MovementPlan held = movement.plan(south, profile, Decision.CLOSE_DISTANCE);
        gate.scheduleNext(30, 150, 0, new SplittableRandom(3));
        Vector firstTick = MovementController.plannedHorizontalVelocity(held, 1, false, .6);
        assertFalse(gate.ready(31));
        Vector secondTick = MovementController.plannedHorizontalVelocity(held, 1, false, .6);
        assertEquals(firstTick, secondTick, "held movement continues every tick");
        assertEquals(0, secondTick.getX(), 1e-12);
        assertTrue(secondTick.getZ() > 0, "newer eastward perception has not replanned movement");

        assertTrue(gate.ready(33));
        MovementPlan replanned = movement.plan(east, profile, Decision.CLOSE_DISTANCE);
        Vector afterReaction = MovementController.plannedHorizontalVelocity(replanned, 1, false, .6);
        assertTrue(afterReaction.getX() > 0);
        assertEquals(0, afterReaction.getZ(), 1e-12);
    }

    private static PerceptionSnapshot snapshot(long tick, double x, double z, double velocityX, double velocityZ) {
        Location body = new Location(null, x, 64, z);
        Vector velocity = new Vector(velocityX, 0, velocityZ);
        CombatFrame frame = CombatFrame.from(0, 0, x, z);
        var relative = frame.project(velocityX, velocityZ);
        return new PerceptionSnapshot(
                tick, body, body.clone().add(0, 1.62, 0), velocity, frame,
                Math.hypot(x, z), 0, relative.forwardVelocity(), relative.lateralVelocity(),
                0, 0, 20, 20, 0, 0, 20, 20, true, true, true
        );
    }
}
