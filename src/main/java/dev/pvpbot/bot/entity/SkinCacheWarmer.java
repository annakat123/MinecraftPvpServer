package dev.pvpbot.bot.entity;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/** Asynchronously warms immutable Citizens-resolved texture data for later NPC spawns. */
public final class SkinCacheWarmer {
    @FunctionalInterface
    public interface Fetcher {
        void fetch(String skinName, Consumer<Optional<ResolvedSkin>> completion);
    }

    public record ResolvedSkin(String skinName, String signature, String texture) {
        public ResolvedSkin {
            Objects.requireNonNull(skinName, "skinName");
            Objects.requireNonNull(signature, "signature");
            Objects.requireNonNull(texture, "texture");
            if (skinName.isBlank() || signature.isBlank() || texture.isBlank()) {
                throw new IllegalArgumentException("Resolved skin fields must not be blank");
            }
        }
    }

    private final Fetcher fetcher;
    private final ConcurrentMap<String, ResolvedSkin> ready = new ConcurrentHashMap<>();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    public SkinCacheWarmer(Fetcher fetcher) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
    }

    public void warm(String skinName) {
        if (!usable(skinName)) return;
        String key = key(skinName);
        if (ready.containsKey(key) || !pending.add(key)) return;
        try {
            fetcher.fetch(skinName, result -> {
                try {
                    result.filter(SkinCacheWarmer::usable).ifPresent(skin -> ready.put(key, skin));
                } finally {
                    pending.remove(key);
                }
            });
        } catch (RuntimeException failure) {
            pending.remove(key);
        }
    }

    public boolean isReady(String skinName) {
        return ready(skinName).isPresent();
    }

    public Optional<ResolvedSkin> ready(String skinName) {
        if (!usable(skinName)) return Optional.empty();
        return Optional.ofNullable(ready.get(key(skinName)));
    }

    private static boolean usable(ResolvedSkin skin) {
        return usable(skin.skinName()) && !skin.signature().isBlank() && !skin.texture().isBlank();
    }

    private static boolean usable(String skinName) {
        return skinName != null && !skinName.isBlank();
    }

    private static String key(String skinName) {
        return skinName.toLowerCase(Locale.ROOT);
    }
}
