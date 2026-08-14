package dev.pvpbot.qa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.Decision;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.DecisionReason;
import dev.pvpbot.bot.trace.CombatTraceEvent;
import dev.pvpbot.bot.trace.InMemoryCombatTraceSink;
import dev.pvpbot.bot.trace.TraceJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

public final class CombatQaMain {
    private static final long DEFAULT_QA_SEED = 472918234L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private record Config(String mode, long seed, String scenario, int generated, int fuzzTicks) {}
    private record FailureReport(String scenario, long qaSeed, long matchSeed, long tick,
                                 String invariant, String state, String reproduction) {}
    private record Report(int schema, String generatedAt, String mode, long qaSeed,
                          int scriptedScenarios, int generatedSeeds, long simulatedTicks,
                          int invariantFailures, int negativeControlsDetected,
                          long attackAttempts, long confirmedHits, long contacts, long whiffs,
                          long targetInvalid, long watchdogIntents, double watchdogIntentRate,
                          long jumpResetOpportunities, long jumpResetExecutions,
                          long criticalSetups, long knockbackLocks,
                          double averagePerceptionAgeTicks, long decisionUpdates,
                          long aimUpdates, long movementUpdates,
                          Map<Decision, Long> decisionDistribution,
                          Map<DecisionReason, Long> decisionReasonDistribution,
                          long runtimeMillis, List<FailureReport> failures,
                          String metricsBoundary) {}

    public static void main(String[] args) throws Exception {
        Config config = parse(args);
        long started = System.nanoTime();
        Path reportDirectory = Path.of("build", "reports", "combat-qa");
        Path failuresDirectory = reportDirectory.resolve("failures");
        Files.createDirectories(failuresDirectory);
        CombatScenarioRunner runner = new CombatScenarioRunner();
        Aggregate aggregate = new Aggregate();
        List<FailureReport> failures = new ArrayList<>();
        int scriptedCount = 0;
        int generatedCount = 0;

        for (CombatScenario scenario : selectedScripted(config.scenario())) {
            scriptedCount++;
            InMemoryCombatTraceSink trace = new InMemoryCombatTraceSink();
            CombatScenarioRunner.Result result = runner.run(scenario, trace);
            aggregate.add(result.metrics());
            collectFailures(result, config.seed(), failures, failuresDirectory);
        }

        if (config.scenario() == null) {
            SplittableRandom seeds = new SplittableRandom(config.seed());
            for (int index = 0; index < config.generated(); index++) {
                long qaScenarioSeed = seeds.nextLong();
                CombatScenario scenario = CombatScenarios.fuzz(qaScenarioSeed, config.fuzzTicks());
                InMemoryCombatTraceSink trace = new InMemoryCombatTraceSink();
                CombatScenarioRunner.Result result = runner.run(scenario, trace);
                generatedCount++;
                aggregate.add(result.metrics());
                collectFailures(result, qaScenarioSeed, failures, failuresDirectory);
            }
        }

        Map<String, List<CombatInvariantEngine.Failure>> controls = InvariantNegativeControls.run();
        int detectedControls = 0;
        for (Map.Entry<String, List<CombatInvariantEngine.Failure>> control : controls.entrySet()) {
            if (control.getValue().isEmpty()) {
                failures.add(new FailureReport("NEGATIVE_CONTROL", config.seed(), 0, 0,
                        "negative control was not detected", control.getKey(),
                        reproduction(config.seed(), control.getKey())));
            } else {
                detectedControls++;
            }
        }

        long runtimeMillis = (System.nanoTime() - started) / 1_000_000;
        Report report = new Report(1, Instant.now().toString(), config.mode(), config.seed(),
                scriptedCount, generatedCount, aggregate.ticks, failures.size(), detectedControls,
                aggregate.attempts, aggregate.hits, aggregate.contacts, aggregate.whiffs,
                aggregate.targetInvalid, aggregate.watchdogIntents,
                rate(aggregate.watchdogIntents, aggregate.attempts),
                aggregate.jumpResetOpportunities, aggregate.jumpResetExecutions,
                aggregate.criticalSetups, aggregate.knockbackLocks,
                aggregate.averagePerceptionAge(), aggregate.decisionUpdates,
                aggregate.aimUpdates, aggregate.movementUpdates,
                aggregate.decisions, aggregate.reasons, runtimeMillis, List.copyOf(failures),
                "Generated scenario metrics describe the harness inputs; they are not real-player game balance truth.");
        Files.writeString(reportDirectory.resolve("report.json"), GSON.toJson(report) + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(reportDirectory.resolve("report.txt"), human(report, controls),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.print(human(report, controls));
        if (!failures.isEmpty()) throw new IllegalStateException(
                failures.size() + " combat QA failure(s); see " + failuresDirectory.toAbsolutePath());
    }

    private static List<CombatScenario> selectedScripted(String selected) {
        if (selected == null) return CombatScenarios.scripted();
        return CombatScenarios.scripted().stream()
                .filter(scenario -> scenario.name().equalsIgnoreCase(selected))
                .toList();
    }

    private static void collectFailures(CombatScenarioRunner.Result result, long qaSeed,
                                        List<FailureReport> failures, Path directory) throws IOException {
        if (result.failures().isEmpty()) return;
        for (CombatInvariantEngine.Failure failure : result.failures()) {
            failures.add(new FailureReport(result.scenario(), qaSeed, result.matchSeed(), failure.tick(),
                    failure.invariant(), failure.state(), reproduction(qaSeed, result.scenario())));
        }
        String safeName = result.scenario().replaceAll("[^A-Za-z0-9_-]", "_");
        Path artifact = directory.resolve(safeName + "_seed-" + qaSeed + ".jsonl");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schema", 1);
        metadata.put("event", "QA_FAILURE_METADATA");
        metadata.put("scenario", result.scenario());
        metadata.put("qaSeed", qaSeed);
        metadata.put("matchSeed", result.matchSeed());
        metadata.put("failures", result.failures());
        metadata.put("reproduction", reproduction(qaSeed, result.scenario()));
        List<String> lines = new ArrayList<>();
        lines.add(new GsonBuilder().disableHtmlEscaping().create().toJson(metadata));
        List<CombatTraceEvent> trace = result.trace();
        int from = Math.max(0, trace.size() - 100);
        for (CombatTraceEvent event : trace.subList(from, trace.size())) lines.add(TraceJson.encode(event));
        Files.write(artifact, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static String reproduction(long seed, String scenario) {
        return ".\\gradlew.bat combatQa -PqaSeed=" + seed + " -PqaScenario=" + scenario;
    }

    private static Config parse(String[] args) {
        String mode = "quick";
        long seed = DEFAULT_QA_SEED;
        String scenario = null;
        Integer generated = null;
        for (String arg : args) {
            if (arg.startsWith("--mode=")) mode = arg.substring("--mode=".length());
            else if (arg.startsWith("--seed=")) seed = Long.parseLong(arg.substring("--seed=".length()));
            else if (arg.startsWith("--scenario=")) scenario = arg.substring("--scenario=".length());
            else if (arg.startsWith("--generated=")) generated = Integer.parseInt(arg.substring("--generated=".length()));
        }
        boolean extended = mode.equalsIgnoreCase("extended");
        return new Config(mode.toLowerCase(Locale.ROOT), seed, scenario,
                generated == null ? (extended ? 4000 : 128) : Math.max(0, generated),
                extended ? 300 : 180);
    }

    private static String human(Report report, Map<String, List<CombatInvariantEngine.Failure>> controls) {
        StringBuilder text = new StringBuilder();
        text.append("PvPBot combat QA\n");
        text.append("mode: ").append(report.mode()).append('\n');
        text.append("QA seed: ").append(report.qaSeed()).append('\n');
        text.append("scripted scenarios: ").append(report.scriptedScenarios()).append('\n');
        text.append("generated seeds: ").append(report.generatedSeeds()).append('\n');
        text.append("simulated ticks: ").append(report.simulatedTicks()).append('\n');
        text.append("invariant failures: ").append(report.invariantFailures()).append('\n');
        text.append("negative controls detected: ").append(report.negativeControlsDetected())
                .append('/').append(controls.size()).append('\n');
        text.append("attempts/hits/contact/whiff/invalid: ").append(report.attackAttempts()).append('/')
                .append(report.confirmedHits()).append('/').append(report.contacts()).append('/')
                .append(report.whiffs()).append('/').append(report.targetInvalid()).append('\n');
        text.append("watchdog intents/rate: ").append(report.watchdogIntents()).append('/')
                .append(String.format(Locale.ROOT, "%.4f", report.watchdogIntentRate())).append('\n');
        text.append("JRESET opportunities/executions: ").append(report.jumpResetOpportunities())
                .append('/').append(report.jumpResetExecutions()).append('\n');
        text.append("critical setups: ").append(report.criticalSetups()).append('\n');
        text.append("knockback locks: ").append(report.knockbackLocks()).append('\n');
        text.append("average perception age: ")
                .append(String.format(Locale.ROOT, "%.3f ticks", report.averagePerceptionAgeTicks())).append('\n');
        text.append("reaction updates D/A/M: ").append(report.decisionUpdates()).append('/')
                .append(report.aimUpdates()).append('/').append(report.movementUpdates()).append('\n');
        text.append("decision distribution: ").append(report.decisionDistribution()).append('\n');
        text.append("reason distribution: ").append(report.decisionReasonDistribution()).append('\n');
        text.append("runtime: ").append(report.runtimeMillis()).append(" ms\n");
        text.append(report.metricsBoundary()).append('\n');
        for (FailureReport failure : report.failures()) {
            text.append("FAIL ").append(failure.scenario()).append(" tick=").append(failure.tick())
                    .append(" invariant=").append(failure.invariant()).append('\n')
                    .append("  state: ").append(failure.state()).append('\n')
                    .append("  reproduce: ").append(failure.reproduction()).append('\n');
        }
        return text.toString();
    }

    private static double rate(long numerator, long denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }

    private static final class Aggregate {
        long ticks;
        long attempts;
        long hits;
        long contacts;
        long whiffs;
        long targetInvalid;
        long watchdogIntents;
        long jumpResetOpportunities;
        long jumpResetExecutions;
        long criticalSetups;
        long knockbackLocks;
        long perceptionAgeTotal;
        long perceptionMaturations;
        long decisionUpdates;
        long aimUpdates;
        long movementUpdates;
        final Map<Decision, Long> decisions = new EnumMap<>(Decision.class);
        final Map<DecisionReason, Long> reasons = new EnumMap<>(DecisionReason.class);

        void add(CombatScenarioRunner.Metrics metrics) {
            ticks += metrics.ticks;
            attempts += metrics.attempts;
            hits += metrics.hits;
            contacts += metrics.contacts;
            whiffs += metrics.whiffs;
            targetInvalid += metrics.targetInvalid;
            watchdogIntents += metrics.watchdogIntents;
            jumpResetOpportunities += metrics.jumpResetOpportunities;
            jumpResetExecutions += metrics.jumpResetExecutions;
            criticalSetups += metrics.criticalSetups;
            knockbackLocks += metrics.knockbackLocks;
            perceptionAgeTotal += metrics.perceptionAgeTotal;
            perceptionMaturations += metrics.perceptionMaturations;
            decisionUpdates += metrics.decisionUpdates;
            aimUpdates += metrics.aimUpdates;
            movementUpdates += metrics.movementUpdates;
            metrics.decisions.forEach((key, value) -> decisions.merge(key, value, Long::sum));
            metrics.reasons.forEach((key, value) -> reasons.merge(key, value, Long::sum));
        }

        double averagePerceptionAge() {
            return perceptionMaturations == 0 ? 0 : (double) perceptionAgeTotal / perceptionMaturations;
        }
    }
}
