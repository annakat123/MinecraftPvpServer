package dev.pvpbot.bot.profile;

import java.util.*;

public final class BotProfile {
    private final String name;
    private final Map<String, Double> values;
    private final Map<String, Boolean> enabled;
    public BotProfile(String name, Map<String, Double> raw, Map<String, Boolean> toggles) {
        this.name = name.toUpperCase(Locale.ROOT);
        Map<String, Double> safe = new LinkedHashMap<>();
        ProfileSchema.PARAMETERS.forEach((key, spec) -> safe.put(key, spec.clamp(raw.getOrDefault(key, spec.fallback()))));
        Map<String, Boolean> flags = new HashMap<>(); ProfileSchema.TOGGLES.forEach(k -> flags.put(k, toggles.getOrDefault(k, true)));
        values = Map.copyOf(safe); enabled = Map.copyOf(flags);
    }
    public static BotProfile defaults(String name) { return new BotProfile(name, Map.of(), Map.of()); }
    public String name() { return name; }
    public double value(String key) { var spec = ProfileSchema.PARAMETERS.get(key); return values.getOrDefault(key, spec == null ? 0 : spec.fallback()); }
    public int millis(String key) { return (int) Math.round(value(key)); }
    public boolean enabled(String system) { return enabled.getOrDefault(system, true); }
    public Map<String, Double> values() { return values; }
    public Map<String, Boolean> toggles() { return enabled; }
    public BotProfile withValue(String key, double value) {
        if (!ProfileSchema.PARAMETERS.containsKey(key)) throw new IllegalArgumentException("Unknown parameter: " + key);
        Map<String, Double> changed = new HashMap<>(values); changed.put(key, value); return new BotProfile(name, changed, enabled);
    }
    public BotProfile toggle(String system) {
        if (!ProfileSchema.TOGGLES.contains(system)) throw new IllegalArgumentException("Unknown toggle: " + system);
        Map<String, Boolean> changed = new HashMap<>(enabled); changed.put(system, !enabled(system)); return new BotProfile(name, values, changed);
    }
}
