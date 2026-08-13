package dev.pvpbot.logic;

import dev.pvpbot.bot.combat.AimController;
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
}
