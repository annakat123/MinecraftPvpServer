package dev.pvpbot.duel.match;

public final class DuelHealth {
    private double player = 20.0;
    private double bot = 20.0;

    public boolean damagePlayer(double damage) {
        player = remaining(player, damage);
        return player <= 0.0;
    }

    public boolean damageBot(double damage) {
        bot = remaining(bot, damage);
        return bot <= 0.0;
    }

    public double player() { return player; }
    public double bot() { return bot; }

    private static double remaining(double health, double damage) {
        if (!Double.isFinite(damage) || damage <= 0.0) return health;
        return Math.max(0.0, health - damage);
    }
}
