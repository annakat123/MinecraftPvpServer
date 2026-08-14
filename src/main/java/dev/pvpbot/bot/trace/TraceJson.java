package dev.pvpbot.bot.trace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public final class TraceJson {
    private record Envelope(int schema, long tick, String event, Object data) {}
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private TraceJson() {}

    public static String encode(CombatTraceEvent event) {
        JsonObject data = GSON.toJsonTree(event).getAsJsonObject();
        data.remove("tick");
        return GSON.toJson(new Envelope(
                CombatTraceEvent.SCHEMA_VERSION,
                event.tick(),
                event.event().name(),
                data
        ));
    }
}
