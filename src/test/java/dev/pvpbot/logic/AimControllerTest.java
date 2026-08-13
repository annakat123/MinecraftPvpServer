package dev.pvpbot.logic;

import dev.pvpbot.bot.combat.AimController;
import dev.pvpbot.bot.combat.AimController.AimPlan;
import dev.pvpbot.bot.combat.AimController.Rotation;
import dev.pvpbot.bot.ai.perception.CombatFrame;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimControllerTest {
    @Test void permitsTargetsInsideAttackCone() {
        assertTrue(AimController.isFacing(new Vector(0,0,1),new Vector(.2,0,1),25));
    }

    @Test void rejectsTargetsBehindBot() {
        assertFalse(AimController.isFacing(new Vector(0,0,1),new Vector(0,0,-1),25));
    }

    @Test void adaptiveBiasUsesCombatRightInsteadOfWorldX() {
        Vector southFacing=AimController.applyLateralBias(new Vector(),CombatFrame.from(0,0,0,4),.2);
        Vector eastFacing=AimController.applyLateralBias(new Vector(),CombatFrame.from(0,0,4,0),.2);
        assertEquals(new Vector(-.2,0,0),southFacing);
        assertEquals(new Vector(0,0,.2),eastFacing);
    }

    @Test void knownAimPlanIsNotEligibleUntilExecutedRotationAligns() {
        AimPlan farRight = new AimPlan(new Vector(10, 65.62, 0), 0, 0, 1);
        Vector botEye = new Vector(0, 65.62, 0);

        Rotation partial = AimController.nextRotation(farRight, botEye, 0, 0, 3, 3);
        assertEquals(-3, partial.yaw(), 1e-12);
        assertFalse(AimController.withinAimTolerance(partial, farRight.accuracy()));

        Rotation aligned = AimController.nextRotation(farRight, botEye, -87, 0, 3, 3);
        assertEquals(-90, aligned.yaw(), 1e-12);
        assertTrue(AimController.withinAimTolerance(aligned, farRight.accuracy()));
    }
}
