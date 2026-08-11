package dev.pvpbot.logic;

import dev.pvpbot.duel.match.DuelHealth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DuelHealthTest {
    @Test void requiresTwentyPointsOfAccumulatedDamage() {
        DuelHealth health = new DuelHealth();
        assertFalse(health.damageBot(9.0));
        assertEquals(11.0, health.bot());
        assertFalse(health.damageBot(10.9));
        assertTrue(health.damageBot(0.1));
    }

    @Test void ignoresInvalidDamage() {
        DuelHealth health = new DuelHealth();
        assertFalse(health.damagePlayer(Double.NaN));
        assertFalse(health.damagePlayer(-5.0));
        assertEquals(20.0, health.player());
    }
}
