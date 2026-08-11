package dev.pvpbot.bot.combat.combo;
public final class ComboTracker {
    private int playerCombo, botCombo, longestPlayer, longestBot; private long lastHitTick = -1;
    public void playerHit(long tick) { playerCombo++; botCombo = 0; longestPlayer = Math.max(longestPlayer, playerCombo); lastHitTick = tick; }
    public void botHit(long tick) { botCombo++; playerCombo = 0; longestBot = Math.max(longestBot, botCombo); lastHitTick = tick; }
    public int playerCombo() { return playerCombo; } public int botCombo() { return botCombo; }
    public int longestPlayer() { return longestPlayer; } public int longestBot() { return longestBot; }
    public long ticksSinceHit(long tick) { return lastHitTick < 0 ? Long.MAX_VALUE : tick - lastHitTick; }
}
