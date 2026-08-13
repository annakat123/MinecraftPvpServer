package dev.pvpbot.duel.match;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NextDuelSeedStoreTest {
    @Test void fixedSeedIsConsumedExactlyOnce() {
        NextDuelSeedStore seeds=new NextDuelSeedStore();
        UUID playerId=UUID.fromString("00000000-0000-0000-0000-000000000106");
        seeds.set(playerId,Long.MIN_VALUE);

        assertEquals(Long.MIN_VALUE,seeds.consume(playerId).orElseThrow());
        assertTrue(seeds.consume(playerId).isEmpty());
    }

    @Test void pendingSeedIsRemovedWhenPlayerQuits() {
        NextDuelSeedStore seeds=new NextDuelSeedStore();
        UUID playerId=UUID.fromString("00000000-0000-0000-0000-000000000107");
        seeds.set(playerId,12345L);

        seeds.remove(playerId);

        assertTrue(seeds.consume(playerId).isEmpty());
    }
}
