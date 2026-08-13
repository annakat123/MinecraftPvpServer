package dev.pvpbot.logic;

import dev.pvpbot.bot.ai.reaction.ReactionGate;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class ReactionGateTest {
    @Test void sameSeedAndSettingsProduceSameSchedule() {
        ReactionGate first = new ReactionGate();
        ReactionGate second = new ReactionGate();
        RandomGenerator firstRandom = new SplittableRandom(42);
        RandomGenerator secondRandom = new SplittableRandom(42);
        long firstTick = 10;
        long secondTick = 10;
        for (int i = 0; i < 100; i++) {
            first.scheduleNext(firstTick, 130, 30, firstRandom);
            second.scheduleNext(secondTick, 130, 30, secondRandom);
            assertEquals(first.scheduledIntervalMs(), second.scheduledIntervalMs());
            assertEquals(first.scheduledIntervalTicks(), second.scheduledIntervalTicks());
            firstTick += first.scheduledIntervalTicks();
            secondTick += second.scheduledIntervalTicks();
        }
    }

    @Test void zeroJitterIsDeterministicAndRoundsUpToTicks() {
        ReactionGate gate = new ReactionGate();
        gate.scheduleNext(7, 51, 0, new SplittableRandom(1));
        assertEquals(51, gate.scheduledIntervalMs());
        assertEquals(2, gate.scheduledIntervalTicks());
        assertFalse(gate.ready(8));
        assertTrue(gate.ready(9));
    }

    @Test void symmetricJitterStaysWithinConfiguredBounds() {
        ReactionGate gate = new ReactionGate();
        RandomGenerator random = new SplittableRandom(91);
        for (int i = 0; i < 1_000; i++) {
            gate.scheduleNext(i, 100, 35, random);
            assertTrue(gate.scheduledIntervalMs() >= 65);
            assertTrue(gate.scheduledIntervalMs() <= 135);
        }
    }

    @Test void negativePostJitterIntervalClampsToNextTick() {
        ReactionGate gate = new ReactionGate();
        gate.scheduleNext(20, 0, 200, new MinimumIntRandom());
        assertEquals(0, gate.scheduledIntervalMs());
        assertEquals(1, gate.scheduledIntervalTicks());
        assertFalse(gate.ready(20));
        assertTrue(gate.ready(21));
    }

    @Test void initialReactionIsImmediatelyReady() {
        ReactionGate gate = new ReactionGate();
        assertTrue(gate.ready(500));
        gate.scheduleNext(500, 100, 0, new SplittableRandom(2));
        assertFalse(gate.ready(500));
    }

    @Test void waitingDoesNotResampleJitter() {
        CountingRandom random = new CountingRandom();
        ReactionGate gate = new ReactionGate();
        gate.scheduleNext(10, 500, 200, random);
        long callsAfterSchedule = random.calls;
        for (long tick = 10; tick < 20; tick++) gate.ready(tick);
        assertEquals(callsAfterSchedule, random.calls);
        assertEquals(1, callsAfterSchedule);
    }

    @Test void zeroThroughFiftyMillisecondsResolveAtNextTick() {
        assertEquals(1, ReactionGate.millisecondsToTicks(0));
        assertEquals(1, ReactionGate.millisecondsToTicks(1));
        assertEquals(1, ReactionGate.millisecondsToTicks(50));
        assertEquals(2, ReactionGate.millisecondsToTicks(51));
    }

    private static class CountingRandom implements RandomGenerator {
        long calls;
        @Override public long nextLong() { calls++; return 0; }
    }

    private static final class MinimumIntRandom extends CountingRandom {
        @Override public int nextInt(int bound) { calls++; return 0; }
    }
}
