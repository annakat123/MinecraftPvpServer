package dev.pvpbot.bot.ai.perception;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.Objects;

/** One immutable, temporally coherent world observation offered to simulated latency. */
public record PerceptionSnapshot(
        long tick,
        Location targetBody,
        Location targetEye,
        Vector targetVelocity,
        CombatFrame combatFrame,
        double distance,
        double closingSpeed,
        double forwardVelocity,
        double lateralVelocity,
        double playerVerticalVelocity,
        double botVerticalVelocity,
        double botHealth,
        double playerHealth,
        int incomingCombo,
        int outgoingCombo,
        long ticksSinceIncomingHit,
        long ticksSinceOutgoingHit,
        boolean lineOfSight,
        boolean botOnGround,
        boolean playerOnGround
) {
    public PerceptionSnapshot {
        targetBody = Objects.requireNonNull(targetBody, "targetBody").clone();
        targetEye = Objects.requireNonNull(targetEye, "targetEye").clone();
        targetVelocity = Objects.requireNonNull(targetVelocity, "targetVelocity").clone();
        combatFrame = Objects.requireNonNull(combatFrame, "combatFrame");
    }

    @Override public Location targetBody() { return targetBody.clone(); }
    @Override public Location targetEye() { return targetEye.clone(); }
    @Override public Vector targetVelocity() { return targetVelocity.clone(); }
}
