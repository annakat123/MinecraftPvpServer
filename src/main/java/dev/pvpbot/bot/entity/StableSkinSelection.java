package dev.pvpbot.bot.entity;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** Selects only already-resolved Citizens skin data; otherwise keeps a stable client default. */
public final class StableSkinSelection {
    public enum Source { REQUESTED_CACHE, FALLBACK_CACHE, CLIENT_DEFAULT }

    public record Result(Source source, Optional<String> skinName) {
        public Result {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(skinName, "skinName");
        }
    }

    private StableSkinSelection() {
    }

    public static Result select(String requested, String fallback, Predicate<String> ready) {
        Objects.requireNonNull(ready, "ready");
        if (usable(requested) && ready.test(requested)) {
            return new Result(Source.REQUESTED_CACHE, Optional.of(requested));
        }
        if (usable(fallback) && !fallback.equalsIgnoreCase(requested) && ready.test(fallback)) {
            return new Result(Source.FALLBACK_CACHE, Optional.of(fallback));
        }
        return new Result(Source.CLIENT_DEFAULT, Optional.empty());
    }

    private static boolean usable(String name) {
        return name != null && !name.isBlank();
    }
}
