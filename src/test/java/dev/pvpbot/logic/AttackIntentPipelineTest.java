package dev.pvpbot.logic;

import dev.pvpbot.bot.ai.BotBrain;
import dev.pvpbot.bot.ai.perception.CombatFrame;
import dev.pvpbot.bot.ai.perception.PerceptionSnapshot;
import dev.pvpbot.bot.ai.random.MatchRandom;
import dev.pvpbot.bot.combat.attack.AttackExecutionResult;
import dev.pvpbot.bot.combat.attack.AttackExecutor;
import dev.pvpbot.bot.combat.attack.AttackIntent;
import dev.pvpbot.bot.combat.attack.AttackIntentPlanner;
import dev.pvpbot.bot.combat.attack.AttackTiming;
import dev.pvpbot.bot.combat.attack.PaperPhysicalAttackProbe;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.Decision;
import dev.pvpbot.bot.entity.BotHandle;
import dev.pvpbot.bot.profile.BotProfile;
import dev.pvpbot.duel.match.MatchMetrics;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackIntentPipelineTest {
    @Test void delayedInReachPerceptionCreatesIntentWithoutLiveTargetDistance() {
        AttackIntentPlanner planner = new AttackIntentPlanner();
        PerceptionSnapshot delayed = snapshot(10, 2.7, true);

        AttackIntent intent = planner.plan(50, -100, delayed, Decision.ATTACK_NOW, true, 2.9, false)
                .orElseThrow();

        assertEquals(2.7, intent.perceivedDistance());
        assertEquals(10, intent.perceptionTick());
        assertEquals(AttackIntent.Source.DECISION, intent.source());
    }

    @Test void delayedOutOfReachPerceptionRejectsIntentEvenIfPhysicalProbeWouldContact() {
        AttackIntentPlanner planner = new AttackIntentPlanner();
        PerceptionSnapshot delayed = snapshot(10, 3.1, true);
        FakeRuntime hypotheticalLiveWorld = new FakeRuntime(50, AttackExecutionResult.CONTACT);

        assertTrue(planner.plan(50, -100, delayed, Decision.ATTACK_NOW, true, 2.9, false).isEmpty());
        assertEquals(0, hypotheticalLiveWorld.probes, "physical current world is not consulted by planning");
    }

    @Test void stalePerceptionCanCreateOneRealWhiffAfterCurrentSideStep() {
        AttackIntentPlanner planner = new AttackIntentPlanner();
        AttackTiming timing = new AttackTiming();
        MatchMetrics metrics = new MatchMetrics();
        AttackIntent intent = planner.plan(50, timing.lastAttackAttemptTick(), snapshot(10, 2.7, true),
                Decision.ATTACK_NOW, true, 2.9, false).orElseThrow();
        double currentSidewaysOffset = 2.0;
        AttackExecutionResult currentRay = currentRayResult(currentSidewaysOffset, 2.7, 2.9, .3);
        FakeRuntime currentWorldAfterSideStep = new FakeRuntime(50, currentRay, timing, metrics);
        AttackExecutor executor = new AttackExecutor();

        AttackExecutor.Outcome first = executor.execute(intent, currentWorldAfterSideStep);
        AttackExecutor.Outcome second = executor.execute(intent, currentWorldAfterSideStep);

        assertEquals(AttackExecutionResult.WHIFF, first.result());
        assertEquals(2.0, currentSidewaysOffset);
        assertTrue(first.attempted());
        assertFalse(first.attackInvoked());
        assertEquals(List.of("attempt", "swing", "probe"), currentWorldAfterSideStep.order);
        assertEquals(1, metrics.botAttempts);
        assertEquals(0, metrics.botHits);
        assertEquals(1, metrics.botMisses());
        assertEquals(50, timing.lastAttackAttemptTick());
        assertEquals(-100, timing.lastSuccessfulOutgoingHitTick());
        assertEquals(AttackExecutionResult.ALREADY_CONSUMED, second.result());
        assertEquals(1, currentWorldAfterSideStep.swings);
        assertEquals(0, currentWorldAfterSideStep.attacks);
    }

    @Test void physicalContactInvokesAttackOnceButHitWaitsForDamageConfirmation() {
        AttackTiming timing = new AttackTiming();
        MatchMetrics metrics = new MatchMetrics();
        AttackIntent intent = intent(false);
        FakeRuntime runtime = new FakeRuntime(50, AttackExecutionResult.CONTACT, timing, metrics);

        AttackExecutor.Outcome outcome = new AttackExecutor().execute(intent, runtime);

        assertEquals(List.of("attempt", "swing", "probe", "attack"), runtime.order);
        assertTrue(outcome.attackInvoked());
        assertEquals(1, runtime.attacks);
        assertEquals(1, metrics.botAttempts);
        assertEquals(0, metrics.botHits, "ray contact is not a confirmed damage hit");
        assertEquals(-100, timing.lastSuccessfulOutgoingHitTick());

        metrics.botHits++;
        timing.successfulOutgoingHit(51);
        assertEquals(1, metrics.botHits);
        assertEquals(51, timing.lastSuccessfulOutgoingHitTick());
    }

    @Test void missResetsCadenceAndCannotSpamOnNextTick() {
        AttackIntentPlanner planner = new AttackIntentPlanner();
        AttackTiming timing = new AttackTiming();
        AttackIntent first = planner.plan(50, timing.lastAttackAttemptTick(), snapshot(10, 2.7, true),
                Decision.ATTACK_NOW, true, 2.9, false).orElseThrow();
        new AttackExecutor().execute(first, new FakeRuntime(50, AttackExecutionResult.WHIFF, timing, new MatchMetrics()));

        assertTrue(planner.plan(51, timing.lastAttackAttemptTick(), snapshot(11, 2.7, true),
                Decision.ATTACK_NOW, true, 2.9, false).isEmpty());
        assertTrue(planner.plan(59, timing.lastAttackAttemptTick(), snapshot(19, 2.7, true),
                Decision.ATTACK_NOW, true, 2.9, false).isEmpty());
        assertTrue(planner.plan(60, timing.lastAttackAttemptTick(), snapshot(20, 2.7, true),
                Decision.ATTACK_NOW, true, 2.9, false).isPresent());
    }

    @Test void watchdogCreatesNormalIntentCanWhiffAndCountsOnlyWatchdogSource() {
        AttackIntentPlanner planner = new AttackIntentPlanner();
        AttackIntent decision = planner.plan(10, -100, snapshot(8, 2.7, true),
                Decision.ATTACK_NOW, true, 2.9, false).orElseThrow();
        AttackIntent watchdog = planner.plan(30, 0, snapshot(9, 2.7, true),
                Decision.WAIT, true, 2.9, false).orElseThrow();
        FakeRuntime runtime = new FakeRuntime(30, AttackExecutionResult.WHIFF);

        AttackExecutor.Outcome outcome = new AttackExecutor().execute(watchdog, runtime);

        assertEquals(AttackIntent.Source.DECISION, decision.source());
        assertEquals(AttackIntent.Source.WATCHDOG, watchdog.source());
        assertEquals(1, planner.watchdogIntentCount());
        assertEquals(AttackExecutionResult.WHIFF, outcome.result());
        assertEquals(1, runtime.swings);
        assertEquals(0, runtime.attacks);
    }

    @Test void watchdogCannotBypassAttemptCadenceAndCountsOnlyCreatedIntents() {
        AttackIntentPlanner planner = new AttackIntentPlanner();
        PerceptionSnapshot perceived = snapshot(10, 2.7, true);

        assertTrue(planner.plan(31, 30, perceived, Decision.WAIT, true, 2.9, false).isEmpty());
        assertTrue(planner.plan(39, 30, perceived, Decision.WAIT, true, 2.9, false).isEmpty());
        assertTrue(planner.plan(59, 30, perceived, Decision.WAIT, true, 2.9, false).isEmpty());
        assertEquals(0, planner.watchdogIntentCount());

        assertTrue(planner.plan(60, 30, perceived, Decision.WAIT, true, 2.9, false).isPresent());
        assertEquals(1, planner.watchdogIntentCount());
    }

    @Test void actualAimEligibilityIsRequiredBeforeIntentPlanning() {
        AttackIntentPlanner planner = new AttackIntentPlanner();

        assertTrue(planner.plan(50, -100, snapshot(10, 2.7, true),
                Decision.ATTACK_NOW, false, 2.9, false).isEmpty());
    }

    @Test void aGoodStaleAimPlanCannotForcePhysicalContact() {
        AttackIntent intent = new AttackIntentPlanner().plan(50, -100, snapshot(10, 2.7, true),
                Decision.ATTACK_NOW, true, 2.9, false).orElseThrow();
        FakeRuntime currentRayMisses = new FakeRuntime(50, AttackExecutionResult.WHIFF);

        AttackExecutor.Outcome outcome = new AttackExecutor().execute(intent, currentRayMisses);

        assertEquals(AttackExecutionResult.WHIFF, outcome.result());
        assertEquals(1, currentRayMisses.swings);
        assertEquals(0, currentRayMisses.attacks);
    }

    @Test void missedCriticalIntentDoesNotIncreaseSuccessfulCriticalStatistic() {
        MatchMetrics metrics = new MatchMetrics();
        FakeRuntime runtime = new FakeRuntime(50, AttackExecutionResult.WHIFF, new AttackTiming(), metrics);

        new AttackExecutor().execute(intent(true), runtime);

        assertEquals(1, metrics.botAttempts);
        assertEquals(0, metrics.botHits);
        assertEquals(0, metrics.botCrits);
        assertEquals(1, metrics.botMisses());
    }

    @Test void exceptionDuringExecutionStillConsumesIntentAndPreventsRetry() {
        AttackExecutor executor = new AttackExecutor();
        AttackIntent intent = intent(false);
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger swings = new AtomicInteger();
        AttackExecutor.Runtime throwing = new AttackExecutor.Runtime() {
            @Override public void recordAttempt(AttackIntent consumed) { attempts.incrementAndGet(); }
            @Override public void playAttackAnimation() { swings.incrementAndGet(); throw new IllegalStateException("animation failed"); }
            @Override public AttackExecutionResult probePhysicalContact() { return AttackExecutionResult.CONTACT; }
            @Override public void attackTarget(AttackIntent consumed) { throw new AssertionError("must not attack"); }
        };

        assertThrows(IllegalStateException.class, () -> executor.execute(intent, throwing));
        AttackExecutor.Outcome retry = executor.execute(intent, throwing);

        assertEquals(AttackExecutionResult.ALREADY_CONSUMED, retry.result());
        assertEquals(1, attempts.get());
        assertEquals(1, swings.get());
    }

    @Test void targetInvalidStillConsumesOneAttemptAndSwingWithoutAttackOrRetry() {
        AttackExecutor executor = new AttackExecutor();
        FakeRuntime invalid = new FakeRuntime(50, AttackExecutionResult.TARGET_INVALID);
        AttackIntent intent = intent(false);

        AttackExecutor.Outcome first = executor.execute(intent, invalid);
        AttackExecutor.Outcome retry = executor.execute(intent, invalid);

        assertEquals(AttackExecutionResult.TARGET_INVALID, first.result());
        assertEquals(List.of("attempt", "swing", "probe"), invalid.order);
        assertEquals(1, invalid.swings);
        assertEquals(0, invalid.attacks);
        assertEquals(AttackExecutionResult.ALREADY_CONSUMED, retry.result());
    }

    @Test void everyResultRequestsExactlyOneAnimationBeforePhysicalProbe() {
        for (AttackExecutionResult result : List.of(
                AttackExecutionResult.WHIFF,
                AttackExecutionResult.CONTACT,
                AttackExecutionResult.TARGET_INVALID
        )) {
            FakeRuntime runtime = new FakeRuntime(50, result);
            AttackExecutor executor = new AttackExecutor();
            AttackIntent intent = intent(false);

            AttackExecutor.Outcome first = executor.execute(intent, runtime);
            AttackExecutor.Outcome repeated = executor.execute(intent, runtime);

            assertEquals(1, runtime.swings, result.name());
            assertEquals(result == AttackExecutionResult.CONTACT ? 1 : 0, runtime.attacks, result.name());
            assertTrue(runtime.order.indexOf("swing") < runtime.order.indexOf("probe"), result.name());
            assertEquals(AttackExecutionResult.ALREADY_CONSUMED, repeated.result(), result.name());
            assertEquals(result, first.result(), result.name());
        }
    }

    @Test void criticalContextIsVisibleOnlyDuringOneAttackInvocationAndCannotLeakLater() throws Exception {
        BrainFixture fixture = new BrainFixture(AttackExecutionResult.CONTACT, false);
        AtomicBoolean synchronousCritical = new AtomicBoolean();
        fixture.onAttack.set(() -> synchronousCritical.set(fixture.brain.outgoingHit()));

        fixture.execute(intent(true));

        assertTrue(synchronousCritical.get());
        assertFalse(fixture.brain.outgoingHit(), "a later damage event must not inherit the old critical intent");
        assertEquals(1, fixture.attempts.get());
        assertEquals(1, fixture.swings.get());
        assertEquals(1, fixture.attacks.get());
    }

    @Test void contactWithoutDamageClearsCriticalContextAndCannotContaminateLaterEvent() throws Exception {
        BrainFixture fixture = new BrainFixture(AttackExecutionResult.CONTACT, false);

        fixture.execute(intent(true));

        assertFalse(fixture.brain.outgoingHit());
        assertEquals(1, fixture.attempts.get());
        assertEquals(1, fixture.swings.get());
        assertEquals(1, fixture.attacks.get());
    }

    @Test void throwingAttackClearsCriticalContextAndSameIntentRemainsConsumed() throws Exception {
        BrainFixture fixture = new BrainFixture(AttackExecutionResult.CONTACT, false);
        fixture.onAttack.set(() -> { throw new IllegalStateException("attack failed"); });
        AttackIntent critical = intent(true);

        InvocationTargetException failure = assertThrows(InvocationTargetException.class, () -> fixture.execute(critical));
        assertTrue(failure.getCause() instanceof IllegalStateException);
        assertFalse(fixture.brain.outgoingHit(), "finally must clear the critical context");

        fixture.onAttack.set(null);
        fixture.execute(critical);
        assertEquals(1, fixture.attempts.get());
        assertEquals(1, fixture.swings.get());
        assertEquals(1, fixture.attacks.get());
    }

    @Test void movementAfterAttackRunsOnceForWhiffContactWithoutDamageAndConfirmedContact() throws Exception {
        List<AttackExecutionResult> results = List.of(
                AttackExecutionResult.WHIFF,
                AttackExecutionResult.CONTACT,
                AttackExecutionResult.CONTACT
        );
        for (int index = 0; index < results.size(); index++) {
            AttackExecutionResult result = results.get(index);
            BrainFixture fixture = new BrainFixture(result, true);
            if (index == 2) {
                fixture.onAttack.set(fixture.brain::outgoingHit);
            }

            fixture.execute(intent(false));

            String scenario = index == 0 ? "WHIFF" : index == 1 ? "CONTACT_NO_DAMAGE" : "CONTACT_CONFIRMED";
            assertEquals(1, fixture.velocityWrites.get(), scenario);
            assertEquals(1, fixture.swings.get(), scenario);
            assertEquals(result == AttackExecutionResult.CONTACT ? 1 : 0, fixture.attacks.get(), scenario);
        }
    }

    @Test void plannerRequiresDelayedLineOfSight() {
        assertTrue(new AttackIntentPlanner().plan(50, -100, snapshot(10, 2.7, false),
                Decision.ATTACK_NOW, true, 2.9, false).isEmpty());
    }

    @Test void paperProbeUsesCurrentEyeDirectionConfiguredReachAndNeutralEntityBoxes() {
        ProbeFixture fixture = new ProbeFixture();
        Location currentEye = new Location(fixture.world, 1, 65.62, 2, 90, -10);
        fixture.bot = fixture.player(fixture.world, fixture.botId, currentEye, true, true, true, false);
        fixture.target = fixture.player(fixture.world, fixture.targetId, currentEye, true, true, true, false);
        fixture.hit.set(new RayTraceResult(new Vector(), fixture.target));

        AttackExecutionResult result = new PaperPhysicalAttackProbe().probe(fixture.bot, fixture.target, 6.0);

        assertEquals(AttackExecutionResult.CONTACT, result);
        Object[] args = fixture.rayArguments.get();
        assertEquals(currentEye, args[0]);
        assertEquals(currentEye.getDirection().normalize(), args[1]);
        assertEquals(6.0, (double) args[2]);
        assertEquals(FluidCollisionMode.NEVER, args[3]);
        assertEquals(true, args[4]);
        assertEquals(0.0, (double) args[5]);
        @SuppressWarnings("unchecked") Predicate<Entity> filter = (Predicate<Entity>) args[6];
        assertFalse(filter.test(fixture.bot));
        assertTrue(filter.test(fixture.target));
    }

    @Test void paperProbeRequiresNearestHitToBeTargetAndRespectsBlockObstruction() {
        ProbeFixture fixture = new ProbeFixture();
        fixture.prepareValidPlayers();
        Player other = fixture.player(fixture.world, UUID.fromString("00000000-0000-0000-0000-000000000111"),
                fixture.eye(), true, true, true, false);
        fixture.hit.set(new RayTraceResult(new Vector(), other));
        assertEquals(AttackExecutionResult.WHIFF,
                new PaperPhysicalAttackProbe().probe(fixture.bot, fixture.target, 2.9));

        Block block = (Block) Proxy.newProxyInstance(
                Block.class.getClassLoader(), new Class<?>[]{Block.class},
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
        fixture.hit.set(new RayTraceResult(new Vector(), block, BlockFace.NORTH));
        assertEquals(AttackExecutionResult.WHIFF,
                new PaperPhysicalAttackProbe().probe(fixture.bot, fixture.target, 2.9));
    }

    @Test void paperProbeRejectsOfflineDeadInvalidAndCrossWorldTargetsWithoutTracing() {
        PaperPhysicalAttackProbe probe = new PaperPhysicalAttackProbe();
        ProbeFixture fixture = new ProbeFixture();
        fixture.bot = fixture.player(fixture.world, fixture.botId, fixture.eye(), true, true, true, false);

        List<Player> invalidTargets = List.of(
                fixture.player(fixture.world, fixture.targetId, fixture.eye(), true, true, false, false),
                fixture.player(fixture.world, fixture.targetId, fixture.eye(), true, true, true, true),
                fixture.player(fixture.world, fixture.targetId, fixture.eye(), false, true, true, false),
                fixture.player(fixture.world, fixture.targetId, fixture.eye(), true, false, true, false),
                fixture.player(fixture.otherWorld, fixture.targetId,
                        new Location(fixture.otherWorld, 0, 65.62, 2.7), true, true, true, false)
        );

        for (Player invalid : invalidTargets) {
            assertEquals(AttackExecutionResult.TARGET_INVALID, probe.probe(fixture.bot, invalid, 2.9));
        }
        assertEquals(0, fixture.rayCalls.get());
    }

    @Test void paperProbeRejectsZeroAndNonFiniteCurrentLookWithoutTracing() {
        PaperPhysicalAttackProbe probe = new PaperPhysicalAttackProbe();
        ProbeFixture fixture = new ProbeFixture();
        fixture.target = fixture.player(fixture.world, fixture.targetId, fixture.eye(), true, true, true, false);
        Location zeroLook = new Location(fixture.world, 0, 65.62, 0) {
            @Override public Vector getDirection() { return new Vector(); }
        };
        fixture.bot = fixture.player(fixture.world, fixture.botId, zeroLook, true, true, true, false);
        assertEquals(AttackExecutionResult.WHIFF, probe.probe(fixture.bot, fixture.target, 2.9));

        Location invalidLook = new Location(fixture.world, 0, 65.62, 0) {
            @Override public Vector getDirection() { return new Vector(Double.NaN, 0, 1); }
        };
        fixture.bot = fixture.player(fixture.world, fixture.botId, invalidLook, true, true, true, false);
        assertEquals(AttackExecutionResult.WHIFF, probe.probe(fixture.bot, fixture.target, 2.9));
        assertEquals(0, fixture.rayCalls.get());
    }

    private static AttackIntent intent(boolean critical) {
        return new AttackIntent(1, 50, 10, Decision.ATTACK_NOW, AttackIntent.Source.DECISION,
                2.7, 2.9, true, critical);
    }

    /** Minimal scenario geometry; production contact uses Paper's precise entity bounding box ray trace. */
    private static AttackExecutionResult currentRayResult(double targetX, double targetZ,
                                                          double reach, double targetHalfWidth) {
        return targetZ >= 0 && targetZ <= reach && Math.abs(targetX) <= targetHalfWidth
                ? AttackExecutionResult.CONTACT
                : AttackExecutionResult.WHIFF;
    }

    private static PerceptionSnapshot snapshot(long tick, double distance, boolean lineOfSight) {
        Location body = new Location(null, 0, 64, distance);
        return new PerceptionSnapshot(
                tick,
                body,
                body.clone().add(0, 1.62, 0),
                new Vector(),
                CombatFrame.from(0, 0, 0, distance),
                distance,
                0,
                0,
                0,
                0,
                0,
                20,
                20,
                0,
                0,
                20,
                20,
                lineOfSight,
                true,
                true
        );
    }

    private static final class FakeRuntime implements AttackExecutor.Runtime {
        private final long tick;
        private final AttackExecutionResult probeResult;
        private final AttackTiming timing;
        private final MatchMetrics metrics;
        private final List<String> order = new ArrayList<>();
        private int probes;
        private int swings;
        private int attacks;

        private FakeRuntime(long tick, AttackExecutionResult probeResult) {
            this(tick, probeResult, new AttackTiming(), new MatchMetrics());
        }

        private FakeRuntime(long tick, AttackExecutionResult probeResult, AttackTiming timing, MatchMetrics metrics) {
            this.tick = tick;
            this.probeResult = probeResult;
            this.timing = timing;
            this.metrics = metrics;
        }

        @Override public void recordAttempt(AttackIntent intent) {
            order.add("attempt");
            timing.attempted(tick);
            metrics.botAttempts++;
        }

        @Override public void playAttackAnimation() {
            order.add("swing");
            swings++;
        }

        @Override public AttackExecutionResult probePhysicalContact() {
            order.add("probe");
            probes++;
            return probeResult;
        }

        @Override public void attackTarget(AttackIntent intent) {
            order.add("attack");
            attacks++;
        }
    }

    /** Exercises BotBrain's real private execution path without a Bukkit server. */
    private static final class BrainFixture {
        private static final Method EXECUTE;

        static {
            try {
                EXECUTE = BotBrain.class.getDeclaredMethod("executeAttackIntent", Player.class, AttackIntent.class);
                EXECUTE.setAccessible(true);
            } catch (ReflectiveOperationException error) {
                throw new ExceptionInInitializerError(error);
            }
        }

        private final AtomicInteger attempts = new AtomicInteger();
        private final AtomicInteger swings = new AtomicInteger();
        private final AtomicInteger attacks = new AtomicInteger();
        private final AtomicInteger velocityWrites = new AtomicInteger();
        private final AtomicReference<Runnable> onAttack = new AtomicReference<>();
        private final Player bot;
        private final BotBrain brain;

        private BrainFixture(AttackExecutionResult result, boolean exerciseMovement) {
            AtomicReference<Player> targetReference = new AtomicReference<>();
            World world = (World) Proxy.newProxyInstance(
                    World.class.getClassLoader(),
                    new Class<?>[]{World.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("equals")) return proxy == args[0];
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName().equals("rayTrace")) {
                            return result == AttackExecutionResult.CONTACT
                                    ? new RayTraceResult(new Vector(0, 65.62, 2.7), targetReference.get())
                                    : null;
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
            UUID botId = UUID.fromString("00000000-0000-0000-0000-000000000109");
            UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000110");
            Player target = playerProxy(world, targetId, true, null);
            targetReference.set(target);
            bot = playerProxy(world, botId, true, (method, args) -> {
                return switch (method.getName()) {
                    case "swingMainHand" -> { swings.incrementAndGet(); yield null; }
                    case "attack" -> {
                        attacks.incrementAndGet();
                        Runnable callback = onAttack.get();
                        if (callback != null) callback.run();
                        yield null;
                    }
                    case "setVelocity" -> { velocityWrites.incrementAndGet(); yield null; }
                    default -> UNHANDLED;
                };
            });
            Map<String, Double> values = exerciseMovement ? Map.of(
                    "sprintReset.skill", 1d,
                    "sTap.skill", 1d,
                    "sTap.chance", 1d
            ) : Map.of();
            Map<String, Boolean> toggles = exerciseMovement ? Map.of(
                    "wTap", false,
                    "sTap", true,
                    "sprintReset", true
            ) : Map.of("wTap", false, "sTap", false);
            brain = new BotBrain(
                    new BotHandle(null, bot, (from, viewer) -> swings.incrementAndGet()),
                    target,
                    null,
                    new BotProfile("audit", values, toggles),
                    new MatchRandom(109L),
                    attempts::incrementAndGet
            );
        }

        private void execute(AttackIntent intent) throws InvocationTargetException, IllegalAccessException {
            EXECUTE.invoke(brain, bot, intent);
        }

        private static final Object UNHANDLED = new Object();

        @FunctionalInterface
        private interface PlayerOverride {
            Object invoke(Method method, Object[] args) throws Throwable;
        }

        private static Player playerProxy(World world, UUID id, boolean valid, PlayerOverride override) {
            return (Player) Proxy.newProxyInstance(
                    Player.class.getClassLoader(),
                    new Class<?>[]{Player.class},
                    (proxy, method, args) -> {
                        if (override != null) {
                            Object overridden = override.invoke(method, args);
                            if (overridden != UNHANDLED) return overridden;
                        }
                        return switch (method.getName()) {
                            case "getUniqueId" -> id;
                            case "getWorld" -> world;
                            case "getEyeLocation" -> new Location(world, 0, 65.62, 0, 0, 0);
                            case "getLocation" -> new Location(world, 0, 64, 0, 0, 0);
                            case "getVelocity" -> new Vector();
                            case "isValid", "isInWorld", "isOnline" -> valid;
                            case "isDead" -> false;
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            default -> defaultValue(method.getReturnType());
                        };
                    }
            );
        }

        private static Object defaultValue(Class<?> type) {
            return AttackIntentPipelineTest.defaultValue(type);
        }
    }

    private static final class ProbeFixture {
        private final UUID botId = UUID.fromString("00000000-0000-0000-0000-000000000112");
        private final UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000113");
        private final AtomicReference<RayTraceResult> hit = new AtomicReference<>();
        private final AtomicReference<Object[]> rayArguments = new AtomicReference<>();
        private final AtomicInteger rayCalls = new AtomicInteger();
        private final World world = world();
        private final World otherWorld = world();
        private Player bot;
        private Player target;

        private void prepareValidPlayers() {
            bot = player(world, botId, eye(), true, true, true, false);
            target = player(world, targetId, eye(), true, true, true, false);
        }

        private Location eye() {
            return new Location(world, 0, 65.62, 0, 0, 0);
        }

        private World world() {
            return (World) Proxy.newProxyInstance(
                    World.class.getClassLoader(), new Class<?>[]{World.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("equals")) return proxy == args[0];
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName().equals("rayTrace")) {
                            rayCalls.incrementAndGet();
                            rayArguments.set(args.clone());
                            return hit.get();
                        }
                        return defaultValue(method.getReturnType());
                    }
            );
        }

        private Player player(World playerWorld, UUID id, Location eye,
                              boolean valid, boolean inWorld, boolean online, boolean dead) {
            return (Player) Proxy.newProxyInstance(
                    Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getWorld" -> playerWorld;
                        case "getEyeLocation" -> eye;
                        case "isValid" -> valid;
                        case "isInWorld" -> inWorld;
                        case "isOnline" -> online;
                        case "isDead" -> dead;
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
