package dev.pvpbot.logic;

import dev.pvpbot.bot.ai.perception.CombatFrame;
import dev.pvpbot.bot.ai.perception.RelativeMotion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatFrameTest {
    @Test void sameStrafeProjectionSurvivesNinetyDegreeWorldRotation() {
        RelativeMotion southFacing = CombatFrame.from(0, 0, 0, 4).project(-.3, 0);
        RelativeMotion eastFacing = CombatFrame.from(0, 0, 4, 0).project(0, .3);

        assertEquals(southFacing.forwardVelocity(), eastFacing.forwardVelocity(), 1e-12);
        assertEquals(southFacing.lateralVelocity(), eastFacing.lateralVelocity(), 1e-12);
        assertEquals(.3, southFacing.lateralVelocity(), 1e-12);
    }

    @Test void rightAndLeftHaveOppositeLateralSigns() {
        CombatFrame frame = CombatFrame.from(2, 3, 2, 8);
        assertEquals(.25, frame.project(-.25, 0).lateralVelocity(), 1e-12);
        assertEquals(-.25, frame.project(.25, 0).lateralVelocity(), 1e-12);
    }

    @Test void forwardVelocityIsSeparatedFromLateralVelocity() {
        RelativeMotion motion = CombatFrame.from(0, 0, 5, 0).project(.4, 0);
        assertEquals(.4, motion.forwardVelocity(), 1e-12);
        assertEquals(0, motion.lateralVelocity(), 1e-12);
    }

    @Test void zeroDistanceProducesSafeZeroProjection() {
        CombatFrame frame = CombatFrame.from(7, -2, 7, -2);
        RelativeMotion motion = frame.project(10, -4);

        assertTrue(frame.degenerate());
        assertEquals(0, motion.forwardVelocity(), 1e-12);
        assertEquals(0, motion.lateralVelocity(), 1e-12);
    }

    @Test void closingSpeedIsPositiveWhenDistanceDecreases() {
        assertEquals(.4, CombatFrame.closingSpeed(3.2, 2.8), 1e-12);
        assertEquals(-.4, CombatFrame.closingSpeed(2.8, 3.2), 1e-12);
    }
}
