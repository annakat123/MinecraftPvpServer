package dev.pvpbot.duel.match;

import java.util.function.BiConsumer;

final class CleanupRunner {
    private final BiConsumer<String, RuntimeException> errorHandler;

    CleanupRunner(BiConsumer<String, RuntimeException> errorHandler) {
        this.errorHandler = errorHandler;
    }

    void run(String operation, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            errorHandler.accept(operation, error);
        }
    }
}
