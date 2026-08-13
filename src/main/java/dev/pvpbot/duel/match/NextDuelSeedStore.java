package dev.pvpbot.duel.match;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

/** In-memory, one-shot development overrides for the next duel only. */
public final class NextDuelSeedStore {
    private final Map<UUID, Long> seeds = new HashMap<>();

    public void set(UUID playerId, long seed) {
        seeds.put(playerId, seed);
    }

    public OptionalLong consume(UUID playerId) {
        Long seed = seeds.remove(playerId);
        return seed == null ? OptionalLong.empty() : OptionalLong.of(seed);
    }

    public void remove(UUID playerId) {
        seeds.remove(playerId);
    }
}
