package dev.pvpbot.bot.ai.random;

import java.util.EnumMap;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Deterministic random streams for one duel. Each subsystem is isolated so
 * consuming values in one stream cannot advance another subsystem's stream.
 */
public final class MatchRandom {
    public enum Subsystem {
        DECISION(0x4445434953494f4eL),
        DECISION_REACTION(0x445f52454143544eL),
        AIM(0x41494d5f5354524dL),
        AIM_REACTION(0x415f52454143544eL),
        MOVEMENT(0x4d4f56454d454e54L),
        MOVEMENT_REACTION(0x4d5f52454143544eL),
        CRITICAL(0x435249544943414cL),
        TECHNIQUE(0x544543484e495155L);

        private final long salt;

        Subsystem(long salt) {
            this.salt = salt;
        }
    }

    private final long seed;
    private final EnumMap<Subsystem, RandomGenerator> streams = new EnumMap<>(Subsystem.class);

    public MatchRandom(long seed) {
        this.seed = seed;
        for (Subsystem subsystem : Subsystem.values()) {
            streams.put(subsystem, new SplittableRandom(deriveSeed(seed, subsystem)));
        }
    }

    public long seed() {
        return seed;
    }

    public RandomGenerator stream(Subsystem subsystem) {
        return streams.get(subsystem);
    }

    static long deriveSeed(long rootSeed, Subsystem subsystem) {
        return mix64(rootSeed ^ subsystem.salt);
    }

    /** SplitMix64 finalizer: fixed salts plus this bijective mix define stable child seeds. */
    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
