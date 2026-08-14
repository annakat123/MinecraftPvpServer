package dev.pvpbot.qa;

import dev.pvpbot.bot.combat.hitselect.HitSelectController.Decision;
import dev.pvpbot.bot.trace.InMemoryCombatTraceSink;
import dev.pvpbot.bot.trace.NoopCombatTraceSink;
import dev.pvpbot.bot.trace.TraceJson;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatQaHarnessTest {
    @Test void allScriptedScenariosSatisfyReusableInvariantsAndExpectations() {
        CombatScenarioRunner runner = new CombatScenarioRunner();
        for (CombatScenario scenario : CombatScenarios.scripted()) {
            CombatScenarioRunner.Result result = runner.run(scenario, new InMemoryCombatTraceSink());
            assertTrue(result.failures().isEmpty(), scenario.name() + ": " + result.failures());
        }
    }

    @Test void negativeControlsProveEveryRequiredBugClassIsDetected() {
        Map<String, java.util.List<CombatInvariantEngine.Failure>> results = InvariantNegativeControls.run();
        assertEquals(5, results.size());
        results.forEach((name, failures) -> assertFalse(failures.isEmpty(), name));
    }

    @Test void traceSinkHasZeroObserverEffectForSameSeedProfileAndInputSequence() {
        CombatScenario scenario = CombatScenarios.fuzz(93847502938475L, 500);
        CombatScenarioRunner runner = new CombatScenarioRunner();

        CombatScenarioRunner.Result noop = runner.run(scenario, NoopCombatTraceSink.INSTANCE);
        CombatScenarioRunner.Result traced = runner.run(scenario, new InMemoryCombatTraceSink());

        assertEquals(noop.decisionTimeline(), traced.decisionTimeline());
        assertEquals(noop.frames(), traced.frames());
        assertEquals(noop.metrics().attempts, traced.metrics().attempts);
        assertEquals(noop.metrics().hits, traced.metrics().hits);
        assertTrue(noop.trace().isEmpty());
        assertFalse(traced.trace().isEmpty());
    }

    @Test void jsonlEnvelopeCarriesSchemaTickEventAndTypedData() {
        CombatScenario scenario = CombatScenarios.scripted().getFirst();
        InMemoryCombatTraceSink sink = new InMemoryCombatTraceSink();
        new CombatScenarioRunner().run(scenario, sink);

        String json = TraceJson.encode(sink.events().getFirst());

        assertTrue(json.contains("\"schema\":1"));
        assertTrue(json.contains("\"tick\":0"));
        assertTrue(json.contains("\"event\":\"MATCH_START\""));
        assertTrue(json.contains("\"matchSeed\":" + scenario.matchSeed()));
    }

    @Test void fuzzGeneratorIsDeterministicAndUsesOneQaSeed() {
        CombatScenario first = CombatScenarios.fuzz(123456789L, 250);
        CombatScenario second = CombatScenarios.fuzz(123456789L, 250);
        CombatScenario different = CombatScenarios.fuzz(123456790L, 250);

        assertEquals(first.name(), second.name());
        assertEquals(first.matchSeed(), second.matchSeed());
        assertEquals(first.profile().values(), second.profile().values());
        assertEquals(first.profile().toggles(), second.profile().toggles());
        assertEquals(first.ticks(), second.ticks());
        assertFalse(first.matchSeed() == different.matchSeed() && first.ticks().equals(different.ticks()));
    }

    @Test void watchdogScenarioUsesWaitDecisionAndOrdinaryIntentPipeline() {
        CombatScenario scenario = CombatScenarios.scripted().stream()
                .filter(candidate -> candidate.name().equals("WATCHDOG_ATTACK"))
                .findFirst().orElseThrow();
        CombatScenarioRunner.Result result = new CombatScenarioRunner().run(
                scenario, new InMemoryCombatTraceSink());

        assertTrue(result.decisionTimeline().contains(Decision.HOLD_DISTANCE));
        assertEquals(1, result.metrics().watchdogIntents);
        assertEquals(1, result.metrics().whiffs);
    }
}
