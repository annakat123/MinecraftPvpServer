package dev.pvpbot.bot.ai.random;

import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;

import static dev.pvpbot.bot.ai.random.MatchRandom.Subsystem.AIM;
import static dev.pvpbot.bot.ai.random.MatchRandom.Subsystem.MOVEMENT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MatchRandomTest {
    @Test void sameSeedAndSubsystemProduceSameSequence() {
        assertArrayEquals(sequence(new MatchRandom(12345L).stream(AIM),20),sequence(new MatchRandom(12345L).stream(AIM),20));
    }

    @Test void differentSeedsProduceDifferentSequence() {
        long first=new MatchRandom(12345L).stream(AIM).nextLong();
        long second=new MatchRandom(54321L).stream(AIM).nextLong();
        assertNotEquals(first,second);
    }

    @Test void everyNamedSubsystemStreamIsDeterministic() {
        for(MatchRandom.Subsystem subsystem:MatchRandom.Subsystem.values()) {
            assertArrayEquals(sequence(new MatchRandom(-77L).stream(subsystem),12),sequence(new MatchRandom(-77L).stream(subsystem),12),subsystem.name());
        }
    }

    @Test void namedSubsystemsHaveDistinctSequences() {
        MatchRandom random=new MatchRandom(12345L);
        assertNotEquals(random.stream(AIM).nextLong(),random.stream(MOVEMENT).nextLong());
    }

    @Test void consumingAimDoesNotAlterMovement() {
        MatchRandom consumedAim=new MatchRandom(12345L);
        MatchRandom untouchedAim=new MatchRandom(12345L);
        for(int i=0;i<100;i++)consumedAim.stream(AIM).nextLong();
        assertArrayEquals(sequence(consumedAim.stream(MOVEMENT),20),sequence(untouchedAim.stream(MOVEMENT),20));
    }

    private static long[] sequence(RandomGenerator random,int length) {
        long[] values=new long[length];
        for(int i=0;i<length;i++)values[i]=random.nextLong();
        return values;
    }
}
