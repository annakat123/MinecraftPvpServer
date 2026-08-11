package dev.pvpbot.logic;

import dev.pvpbot.bot.movement.MovementController;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementControllerTest {
    @Test void clampsCombinedForwardAndStrafeSpeed() {
        Vector movement=new Vector(.32,.18,.21);
        MovementController.clampHorizontal(movement,MovementController.MAX_HORIZONTAL_SPEED);
        assertTrue(Math.hypot(movement.getX(),movement.getZ())<=MovementController.MAX_HORIZONTAL_SPEED+1e-9);
        assertEquals(.18,movement.getY(),1e-9);
    }
}
