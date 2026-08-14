package dev.pvpbot.logic;

import dev.pvpbot.arena.Arena;
import dev.pvpbot.bot.combat.CriticalController;
import dev.pvpbot.bot.movement.MovementController;
import dev.pvpbot.bot.movement.MovementController.MovementPlan;
import dev.pvpbot.bot.movement.VerticalAction;
import dev.pvpbot.bot.movement.VerticalActionController;
import dev.pvpbot.bot.profile.BotProfile;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerticalActionControllerTest {
    @Test void noIncomingHitCanNeverCreateOrExecuteJumpReset() {
        FixedRandom random = new FixedRandom(0);
        VerticalActionController actions = new VerticalActionController(random);
        PlayerFixture bot = new PlayerFixture(true, new Vector(.2, 0, .1));

        for (long tick = 0; tick < 20; tick++) {
            actions.beginTick(tick);
            assertFalse(actions.tryJumpReset(bot.player, tick));
        }

        assertEquals(0, actions.jumpResetOpportunities());
        assertEquals(0, actions.jumpResetChanceSamples());
        assertEquals(0, actions.jumpResetExecutions());
        assertEquals(0, bot.velocityWrites.get());
    }

    @Test void oneConfirmedHitSamplesOnceAndExecutesAtMostOnce() {
        FixedRandom random = new FixedRandom(0);
        VerticalActionController actions = new VerticalActionController(random);
        PlayerFixture bot = new PlayerFixture(true, new Vector(.24, .1, -.18));

        actions.incomingHit(jumpResetProfile(), 10);
        assertEquals(1, actions.jumpResetOpportunities());
        assertEquals(1, actions.jumpResetChanceSamples());
        assertEquals(1, random.doubleCalls.get());
        assertFalse(actions.tryJumpReset(bot.player, 10), "execution is event-relative, not inside damage handling");
        assertTrue(actions.tryJumpReset(bot.player, 11));
        assertFalse(actions.tryJumpReset(bot.player, 12));
        assertFalse(actions.tryJumpReset(bot.player, 13));

        assertEquals(1, actions.jumpResetExecutions());
        assertEquals(1, bot.velocityWrites.get());
        assertEquals(.24, bot.velocity.get().getX(), 1e-12, "horizontal knockback is preserved");
        assertEquals(-.18, bot.velocity.get().getZ(), 1e-12, "horizontal knockback is preserved");
        assertEquals(.42, bot.velocity.get().getY(), 1e-12, "normal player jump strength is used");
    }

    @Test void failedChanceIsNotRetriedOnFollowingTicks() {
        FixedRandom random = new FixedRandom(.5);
        VerticalActionController actions = new VerticalActionController(random);
        PlayerFixture bot = new PlayerFixture(true, new Vector());

        actions.incomingHit(jumpResetProfile(), 5);
        for (long tick = 6; tick < 20; tick++) assertFalse(actions.tryJumpReset(bot.player, tick));

        assertEquals(1, actions.jumpResetOpportunities());
        assertEquals(1, actions.jumpResetChanceSamples());
        assertEquals(1, random.doubleCalls.get());
        assertEquals(0, actions.jumpResetExecutions());
    }

    @Test void selectedOpportunityExpiresWhenBotNeverBecomesGroundedInWindow() {
        VerticalActionController actions = new VerticalActionController(new FixedRandom(0));
        PlayerFixture bot = new PlayerFixture(false, new Vector());

        actions.incomingHit(jumpResetProfile(), 20);
        actions.beginTick(21);
        assertFalse(actions.tryJumpReset(bot.player, 21));
        actions.beginTick(22);
        assertFalse(actions.tryJumpReset(bot.player, 22));
        bot.onGround.set(true);
        actions.beginTick(23);

        assertFalse(actions.tryJumpReset(bot.player, 23));
        assertEquals(0, actions.pendingJumpResets());
        assertEquals(0, actions.jumpResetExecutions());
    }

    @Test void staleIncomingComboAloneCannotCreateJumpReset() {
        MovementController movement = new MovementController(new Random(1), new FixedRandom(0));
        PlayerFixture bot = new PlayerFixture(true, new Vector());
        MovementPlan staleCombo = new MovementPlan(1, 0, 0, 1, .2, 7, false);

        for (long tick = 1; tick < 10; tick++) {
            movement.verticalActions().beginTick(tick);
            movement.execute(bot.player, staleCombo, null, jumpResetProfile(), tick);
        }

        assertEquals(0, movement.verticalActions().jumpResetOpportunities());
        assertEquals(0, movement.verticalActions().jumpResetExecutions());
        assertEquals(0, bot.velocityWrites.get());
    }

    @Test void twoConfirmedHitsCreateTwoIndependentOneShotOpportunities() {
        VerticalActionController actions = new VerticalActionController(new FixedRandom(0, 0));

        actions.incomingHit(jumpResetProfile(), 1);
        actions.incomingHit(jumpResetProfile(), 2);

        assertEquals(2, actions.jumpResetOpportunities());
        assertEquals(2, actions.jumpResetChanceSamples());
        assertEquals(2, actions.pendingJumpResets());
    }

    @Test void criticalSetupIsDistinctAndNeverCreatesJumpResetOpportunity() {
        VerticalActionController actions = new VerticalActionController(new FixedRandom(0));
        CriticalController critical = new CriticalController(new FixedRandom(0));
        PlayerFixture bot = new PlayerFixture(true, new Vector(.1, 0, .2));
        BotProfile profile = new BotProfile("test", Map.of(
                "criticals.chance", 1d,
                "criticals.skill", 1d
        ), Map.of("criticals", true));

        assertTrue(critical.tryStart(bot.player, profile, actions, 4));

        assertEquals(VerticalAction.CRITICAL_SETUP, actions.verticalAction());
        assertEquals(0, actions.jumpResetOpportunities());
        assertEquals(.42, bot.velocity.get().getY(), 1e-12);
        assertEquals(.1, bot.velocity.get().getX(), 1e-12);
        assertEquals(.2, bot.velocity.get().getZ(), 1e-12);
    }

    @Test void criticalSetupDoesNotConsumeAnExistingJumpResetOpportunity() {
        VerticalActionController actions = new VerticalActionController(new FixedRandom(0));
        CriticalController critical = new CriticalController(new FixedRandom(0));
        PlayerFixture bot = new PlayerFixture(true, new Vector());
        BotProfile criticalProfile = new BotProfile("test", Map.of(
                "criticals.chance", 1d,
                "criticals.skill", 1d
        ), Map.of("criticals", true));

        actions.incomingHit(jumpResetProfile(), 3);
        assertTrue(critical.tryStart(bot.player, criticalProfile, actions, 3));

        assertEquals(1, actions.pendingJumpResets());
        assertEquals(1, actions.jumpResetOpportunities());
        assertEquals(0, actions.jumpResetExecutions());
    }

    @Test void jumpResetAirborneStateHasNoCriticalSetupProvenance() {
        VerticalActionController actions = new VerticalActionController(new FixedRandom(0));
        CriticalController critical = new CriticalController(new FixedRandom(0));
        PlayerFixture bot = new PlayerFixture(true, new Vector());

        actions.incomingHit(jumpResetProfile(), 8);
        assertTrue(actions.tryJumpReset(bot.player, 9));
        bot.onGround.set(false);
        bot.velocity.set(new Vector(0, -.1, 0));

        assertTrue(critical.criticalWindow(bot.player), "falling is physically critical-capable");
        assertFalse(actions.criticalSetupActive(), "JRESET must not mark an intent as CRITICAL_SETUP");
        assertEquals(VerticalAction.JUMP_RESET, actions.verticalAction());
    }

    @Test void actualKnockbackLocksFourTicksThenHeldMovementResumes() {
        MovementFixture fixture = new MovementFixture(new FixedRandom(.9));

        fixture.movement.execute(fixture.bot.player, fixture.plan, fixture.arena, fixture.profile, 0);
        assertEquals(1, fixture.bot.velocityWrites.get(), "held movement applies normally before knockback");

        fixture.movement.verticalActions().incomingKnockback(0);
        assertEquals(4, fixture.movement.verticalActions().knockbackLockTicksRemaining(0));
        for (long tick = 1; tick <= 4; tick++) {
            fixture.movement.verticalActions().beginTick(tick);
            fixture.movement.execute(fixture.bot.player, fixture.plan, fixture.arena, fixture.profile, tick);
        }
        assertEquals(1, fixture.bot.velocityWrites.get(), "strafe/approach must not replace X/Z during lock");

        fixture.movement.verticalActions().beginTick(5);
        fixture.movement.execute(fixture.bot.player, fixture.plan, fixture.arena, fixture.profile, 5);
        assertEquals(2, fixture.bot.velocityWrites.get(), "held movement resumes deterministically after lock");
        assertEquals(0, fixture.movement.verticalActions().knockbackLockTicksRemaining(5));
    }

    @Test void knockbackSignalIsImmediateAndIndependentOfSimulatedPing() {
        MovementController movement = new MovementController(new Random(2), new FixedRandom(.9));
        BotProfile highPing = new BotProfile("test", Map.of("simulatedPingMs", 500d), Map.of());

        movement.verticalActions().incomingKnockback(100);

        assertEquals(500, highPing.millis("simulatedPingMs"));
        assertEquals(4, movement.verticalActions().knockbackLockTicksRemaining(100));
        assertTrue(movement.verticalActions().knockbackLocked(101));
    }

    @Test void sTapCannotOverwriteIncomingKnockback() {
        MovementController movement = new MovementController(new Random(3), new FixedRandom(0));
        PlayerFixture bot = new PlayerFixture(true, new Vector(.35, .2, -.2));
        BotProfile sTap = new BotProfile("test", Map.of(
                "sprintReset.skill", 1d,
                "sTap.skill", 1d,
                "sTap.chance", 1d
        ), Map.of("wTap", false, "sTap", true, "sprintReset", true));

        movement.verticalActions().incomingKnockback(0);
        movement.afterAttack(bot.player, sTap, 1);

        assertEquals(0, bot.velocityWrites.get());
        assertEquals(new Vector(.35, .2, -.2), bot.velocity.get());
    }

    @Test void jumpResetDuringKnockbackPreservesHorizontalImpulseWithoutRepeatedWrites() {
        MovementFixture fixture = new MovementFixture(new FixedRandom(0));
        fixture.bot.velocity.set(new Vector(.35, .15, -.27));
        fixture.movement.verticalActions().incomingHit(jumpResetProfile(), 0);
        fixture.movement.verticalActions().incomingKnockback(0);

        fixture.movement.verticalActions().beginTick(1);
        fixture.movement.verticalActions().tryJumpReset(fixture.bot.player, 1);
        fixture.movement.execute(fixture.bot.player, fixture.plan, fixture.arena, fixture.profile, 1);
        fixture.bot.onGround.set(false);
        fixture.movement.verticalActions().beginTick(2);
        fixture.movement.execute(fixture.bot.player, fixture.plan, fixture.arena, fixture.profile, 2);

        assertEquals(1, fixture.bot.velocityWrites.get(), "one intentional Y write; no normal movement overwrite");
        assertEquals(1, fixture.movement.verticalActions().jumpResetExecutions());
        assertEquals(.35, fixture.bot.velocity.get().getX(), 1e-12);
        assertEquals(-.27, fixture.bot.velocity.get().getZ(), 1e-12);
        assertEquals(VerticalAction.JUMP_RESET, fixture.movement.verticalActions().verticalAction());
    }

    private static BotProfile jumpResetProfile() {
        return new BotProfile("test", Map.of(
                "jumpReset.chance", 1d,
                "jumpReset.skill", 1d
        ), Map.of("jumpReset", true));
    }

    private static final class MovementFixture {
        private final World world = proxy(World.class, (method, args) -> defaultValue(method.getReturnType()));
        private final PlayerFixture bot = new PlayerFixture(true, new Vector(.25, .1, .2), world);
        private final Arena arena = new Arena(1, new Location(world, 0, 64, 0), 17.5);
        private final MovementController movement;
        private final BotProfile profile = BotProfile.defaults("test");
        private final MovementPlan plan = new MovementPlan(1, 0, 0, 1, .2, 0, true);

        private MovementFixture(FixedRandom techniqueRandom) {
            movement = new MovementController(new Random(4), techniqueRandom);
        }
    }

    private static final class PlayerFixture {
        private final AtomicBoolean onGround;
        private final AtomicReference<Vector> velocity;
        private final AtomicInteger velocityWrites = new AtomicInteger();
        private final Player player;

        private PlayerFixture(boolean onGround, Vector velocity) {
            this(onGround, velocity, null);
        }

        private PlayerFixture(boolean onGround, Vector velocity, World world) {
            this.onGround = new AtomicBoolean(onGround);
            this.velocity = new AtomicReference<>(velocity.clone());
            player = proxy(Player.class, (method, args) -> switch (method.getName()) {
                case "isOnGround" -> this.onGround.get();
                case "getVelocity" -> this.velocity.get().clone();
                case "setVelocity" -> {
                    this.velocity.set(((Vector) args[0]).clone());
                    velocityWrites.incrementAndGet();
                    yield null;
                }
                case "getLocation" -> new Location(world, 0, 64, 0, 0, 0);
                case "setSprinting" -> null;
                default -> defaultValue(method.getReturnType());
            });
        }
    }

    private static final class FixedRandom extends Random {
        private final double[] values;
        private final AtomicInteger doubleCalls = new AtomicInteger();

        private FixedRandom(double... values) {
            this.values = values.length == 0 ? new double[]{0} : values.clone();
        }

        @Override
        public double nextDouble() {
            int index = doubleCalls.getAndIncrement();
            return values[Math.min(index, values.length - 1)];
        }
    }

    private interface Invocation {
        Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.invoke(method, args));
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
