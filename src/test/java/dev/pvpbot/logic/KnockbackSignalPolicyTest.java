package dev.pvpbot.logic;

import dev.pvpbot.bot.movement.KnockbackSignalPolicy;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnockbackSignalPolicyTest {
    @Test void confirmedDirectMeleeKnockbackIsAccepted() {
        assertTrue(KnockbackSignalPolicy.accepts(EntityKnockbackEvent.Cause.ENTITY_ATTACK, true));
        assertTrue(KnockbackSignalPolicy.accepts(EntityKnockbackEvent.Cause.SWEEP_ATTACK, true));
    }

    @Test void meleeCauseWithoutSameTickDamageProvenanceIsRejected() {
        assertFalse(KnockbackSignalPolicy.accepts(EntityKnockbackEvent.Cause.ENTITY_ATTACK, false));
    }

    @Test void unrelatedKnockbackCausesNeverOpenTheLock() {
        assertFalse(KnockbackSignalPolicy.accepts(EntityKnockbackEvent.Cause.PUSH, true));
        assertFalse(KnockbackSignalPolicy.accepts(EntityKnockbackEvent.Cause.EXPLOSION, true));
        assertFalse(KnockbackSignalPolicy.accepts(EntityKnockbackEvent.Cause.DAMAGE, true));
        assertFalse(KnockbackSignalPolicy.accepts(EntityKnockbackEvent.Cause.UNKNOWN, true));
        assertFalse(KnockbackSignalPolicy.accepts(EntityKnockbackEvent.Cause.SHIELD_BLOCK, true));
    }
}
