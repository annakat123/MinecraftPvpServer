package dev.pvpbot.logic;

import dev.pvpbot.bot.ai.BotBrain;
import dev.pvpbot.bot.trace.CombatTraceSink;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceArchitectureTest {
    @Test void botBrainHasNoFileOrDatabaseDependencies() {
        assertFalse(Arrays.stream(BotBrain.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .anyMatch(name -> name.startsWith("java.io.") || name.startsWith("java.nio.file.")
                        || name.startsWith("dev.pvpbot.database.")));
    }

    @Test void traceSinkIsOneWayAndCannotReturnCombatState() {
        for (Method method : CombatTraceSink.class.getDeclaredMethods()) {
            assertTrue(method.getReturnType() == void.class
                            || method.getReturnType() == boolean.class
                            || method.getReturnType() == long.class,
                    method.toString());
        }
    }
}
