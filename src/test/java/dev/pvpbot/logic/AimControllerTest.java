package dev.pvpbot.logic;

import dev.pvpbot.bot.combat.AimController;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimControllerTest {
    @Test void permitsTargetsInsideAttackCone() {
        assertTrue(AimController.isFacing(new Vector(0,0,1),new Vector(.2,0,1),25));
    }

    @Test void rejectsTargetsBehindBot() {
        assertFalse(AimController.isFacing(new Vector(0,0,1),new Vector(0,0,-1),25));
    }
}
