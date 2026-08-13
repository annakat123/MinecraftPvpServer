package dev.pvpbot.database;

import dev.pvpbot.bot.profile.BotProfile;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/** Pure codec for the data column of SQLite custom_profiles. */
public final class CustomProfileCodec {
    public static String encode(BotProfile profile) {
        StringJoiner serialized = new StringJoiner(";");
        profile.values().forEach((key, value) -> serialized.add("v:" + key + "=" + value));
        profile.toggles().forEach((key, value) -> serialized.add("t:" + key + "=" + value));
        return serialized.toString();
    }

    public static BotProfile decode(String data) {
        Map<String, Double> values = new HashMap<>();
        Map<String, Boolean> toggles = new HashMap<>();
        for (String part : data.split(";")) {
            int equals = part.indexOf('=');
            if (equals < 3) continue;
            if (part.startsWith("v:")) values.put(part.substring(2, equals), Double.parseDouble(part.substring(equals + 1)));
            if (part.startsWith("t:")) toggles.put(part.substring(2, equals), Boolean.parseBoolean(part.substring(equals + 1)));
        }
        return new BotProfile("CUSTOM", values, toggles);
    }

    private CustomProfileCodec() {}
}
