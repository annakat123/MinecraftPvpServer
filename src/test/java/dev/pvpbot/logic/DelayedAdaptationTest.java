package dev.pvpbot.logic;

import dev.pvpbot.bot.ai.adaptation.AdaptationController;
import dev.pvpbot.bot.ai.perception.CombatFrame;
import dev.pvpbot.bot.ai.perception.LatencyBuffer;
import dev.pvpbot.bot.ai.perception.PerceptionSnapshot;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelayedAdaptationTest {
    @Test void adaptationLearnsOnlyAfterObservationMatures() {
        LatencyBuffer<PerceptionSnapshot> latency = new LatencyBuffer<>();
        AdaptationController adaptation = new AdaptationController();
        PerceptionSnapshot changedStrafe = snapshot(12, .25);
        latency.offer(1_000, 300, changedStrafe);

        latency.poll(1_299).ifPresent(adaptation::observe);
        assertEquals(0, adaptation.model().confidence(), 1e-12);
        assertEquals(0, adaptation.model().lateralBias(), 1e-12);

        latency.poll(1_300).ifPresent(adaptation::observe);
        assertTrue(adaptation.model().confidence() > 0);
        assertEquals(1, adaptation.model().lateralBias(), 1e-12);

        latency.poll(1_301).ifPresent(adaptation::observe);
        assertEquals(.05, adaptation.model().confidence(), 1e-12, "same matured tick is learned once");
    }

    @Test void snapshotDefensivelyOwnsMutableBukkitValues() {
        Location sourceBody = new Location(null, 0, 64, 4);
        Location sourceEye = sourceBody.clone().add(0, 1.62, 0);
        Vector sourceVelocity = new Vector(-.2, 0, 0);
        PerceptionSnapshot snapshot = snapshot(3, sourceBody, sourceEye, sourceVelocity, .2);
        sourceBody.setX(98);
        sourceEye.setY(98);
        sourceVelocity.setX(98);

        Location body = snapshot.targetBody();
        Location eye = snapshot.targetEye();
        Vector velocity = snapshot.targetVelocity();
        body.setX(99);
        eye.setY(99);
        velocity.setX(99);

        assertEquals(0, snapshot.targetBody().getX(), 1e-12);
        assertEquals(65.62, snapshot.targetEye().getY(), 1e-12);
        assertEquals(-.2, snapshot.targetVelocity().getX(), 1e-12);
    }

    private static PerceptionSnapshot snapshot(long tick, double lateralVelocity) {
        Location body = new Location(null, 0, 64, 4);
        return snapshot(tick, body, body.clone().add(0, 1.62, 0), new Vector(-lateralVelocity, 0, 0), lateralVelocity);
    }

    private static PerceptionSnapshot snapshot(long tick, Location body, Location eye, Vector velocity, double lateralVelocity) {
        return new PerceptionSnapshot(
                tick,
                body,
                eye,
                velocity,
                CombatFrame.from(0, 0, 0, 4),
                4,
                0,
                0,
                lateralVelocity,
                0,
                0,
                20,
                20,
                0,
                0,
                20,
                20,
                true,
                true,
                true
        );
    }
}
