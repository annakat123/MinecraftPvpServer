package dev.pvpbot.bot.trace;

import org.bukkit.util.Vector;

public record TraceVector(double x, double y, double z) {
    public static TraceVector of(Vector vector) {
        return new TraceVector(vector.getX(), vector.getY(), vector.getZ());
    }
}
