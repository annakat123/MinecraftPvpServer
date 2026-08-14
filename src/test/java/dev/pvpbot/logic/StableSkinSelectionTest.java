package dev.pvpbot.logic;

import dev.pvpbot.bot.entity.BotHandle;
import dev.pvpbot.bot.entity.SkinCacheWarmer;
import dev.pvpbot.bot.entity.StableSkinSelection;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StableSkinSelectionTest {
    @Test void requestedReadySkinIsSelectedBeforeSpawn() {
        StableSkinSelection.Result result = StableSkinSelection.select("Alex", "Steve", "Alex"::equals);

        assertEquals(StableSkinSelection.Source.REQUESTED_CACHE, result.source());
        assertEquals("Alex", result.skinName().orElseThrow());
    }

    @Test void readyFallbackIsUsedWhenRequestedSkinIsNotReady() {
        StableSkinSelection.Result result = StableSkinSelection.select("Alex", "Steve", "Steve"::equals);

        assertEquals(StableSkinSelection.Source.FALLBACK_CACHE, result.source());
        assertEquals("Steve", result.skinName().orElseThrow());
    }

    @Test void unresolvedFirstUseStaysOnStableClientDefaultForThatDuel() {
        Set<String> ready = new HashSet<>();
        StableSkinSelection.Result result = StableSkinSelection.select("Alex", "Steve", ready::contains);
        ready.add("Alex"); // models delayed external cache completion after selection

        assertEquals(StableSkinSelection.Source.CLIENT_DEFAULT, result.source());
        assertTrue(result.skinName().isEmpty(), "delayed completion cannot mutate this duel's immutable selection");
    }

    @Test void firstUseFallbackWarmsCitizensDataForALaterDuel() {
        AtomicReference<Consumer<java.util.Optional<SkinCacheWarmer.ResolvedSkin>>> completion =
                new AtomicReference<>();
        SkinCacheWarmer warmer = new SkinCacheWarmer((name, callback) -> completion.set(callback));

        StableSkinSelection.Result first = StableSkinSelection.select("Alex", "Steve", warmer::isReady);
        warmer.warm("Alex");
        completion.get().accept(java.util.Optional.of(
                new SkinCacheWarmer.ResolvedSkin("Alex", "signature", "texture")
        ));
        StableSkinSelection.Result later = StableSkinSelection.select("Alex", "Steve", warmer::isReady);

        assertEquals(StableSkinSelection.Source.CLIENT_DEFAULT, first.source());
        assertEquals(StableSkinSelection.Source.REQUESTED_CACHE, later.source());
        assertEquals("Alex", warmer.ready("alex").orElseThrow().skinName());
    }

    @Test void failedWarmupRemainsStableAndCanRetryOnAnotherDuel() {
        AtomicInteger fetches = new AtomicInteger();
        AtomicReference<Consumer<java.util.Optional<SkinCacheWarmer.ResolvedSkin>>> completion =
                new AtomicReference<>();
        SkinCacheWarmer warmer = new SkinCacheWarmer((name, callback) -> {
            fetches.incrementAndGet();
            completion.set(callback);
        });

        warmer.warm("Alex");
        warmer.warm("Alex");
        assertEquals(1, fetches.get(), "one asynchronous request while the name is pending");
        completion.get().accept(java.util.Optional.empty());

        assertTrue(warmer.ready("Alex").isEmpty());
        warmer.warm("Alex");
        assertEquals(2, fetches.get(), "a later duel may retry a failed profile fetch");
    }

    @Test void botHandleRefreshesCitizensEntityReferenceSafely() {
        Player first = player("00000000-0000-0000-0000-000000000101");
        Player replacement = player("00000000-0000-0000-0000-000000000101");
        AtomicReference<Player> current = new AtomicReference<>(first);
        NPC npc = (NPC) Proxy.newProxyInstance(NPC.class.getClassLoader(), new Class<?>[]{NPC.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getEntity" -> current.get();
                    case "isSpawned" -> true;
                    default -> defaultValue(method.getReturnType());
                });
        AtomicInteger animations = new AtomicInteger();
        BotHandle handle = new BotHandle(npc, first, (from, viewer) -> animations.incrementAndGet());

        assertSame(first, handle.entity());
        assertEquals(0, handle.entityRevision());
        current.set(replacement);
        assertSame(replacement, handle.entity());
        assertEquals(1, handle.entityRevision());
        handle.playAttackAnimation(first);
        assertEquals(1, animations.get());
        assertTrue(handle.isSpawned());
    }

    private static Player player(String uuid) {
        UUID id = UUID.fromString(uuid);
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> defaultValue(method.getReturnType());
                });
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
