package dev.pvpbot.bot.entity;

import net.citizensnpcs.npc.skin.profile.ProfileFetchResult;
import net.citizensnpcs.npc.skin.profile.ProfileFetcher;
import net.citizensnpcs.util.SkinProperty;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

/** Uses Citizens' asynchronous Mojang profile pipeline without attaching a pending skin to an NPC. */
final class CitizensSkinProfileFetcher implements SkinCacheWarmer.Fetcher {
    private static final Method GET_PROFILE = profileMethod();

    @Override
    public void fetch(String skinName,
                      java.util.function.Consumer<Optional<SkinCacheWarmer.ResolvedSkin>> completion) {
        ProfileFetcher.fetch(skinName, request -> {
            if (request.getResult() != ProfileFetchResult.SUCCESS) {
                completion.accept(Optional.empty());
                return;
            }
            Object profile;
            try {
                profile = GET_PROFILE.invoke(request);
            } catch (IllegalAccessException | InvocationTargetException unavailableProfile) {
                completion.accept(Optional.empty());
                return;
            }
            if (profile == null) {
                completion.accept(Optional.empty());
                return;
            }
            SkinProperty property = SkinProperty.fromMojangProfile(profile);
            if (property == null || property.signature == null || property.value == null) {
                completion.accept(Optional.empty());
                return;
            }
            try {
                completion.accept(Optional.of(new SkinCacheWarmer.ResolvedSkin(
                        skinName, property.signature, property.value
                )));
            } catch (IllegalArgumentException invalidProperty) {
                completion.accept(Optional.empty());
            }
        });
    }

    /** Keeps Mojang Authlib (a server runtime library) out of PvPBot's compile/runtime artifact. */
    private static Method profileMethod() {
        try {
            return Class.forName("net.citizensnpcs.npc.skin.profile.ProfileRequest")
                    .getMethod("getProfile");
        } catch (ClassNotFoundException | NoSuchMethodException incompatibleCitizens) {
            throw new ExceptionInInitializerError(incompatibleCitizens);
        }
    }
}
