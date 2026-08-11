package dev.pvpbot.duel.match;
import dev.pvpbot.bot.combat.combo.ComboTracker;
public final class MatchMetrics { public int playerAttempts,playerHits,botAttempts,botHits,playerCrits,botCrits; public double playerDamage,botDamage; public final ComboTracker combo=new ComboTracker(); public int playerMisses(){return Math.max(0,playerAttempts-playerHits);} public int botMisses(){return Math.max(0,botAttempts-botHits);} }
