package dev.pvpbot.duel;
public record MatchRule(int deathsToFinish) { public MatchRule { if(deathsToFinish!=1)throw new IllegalArgumentException("Sword MVP supports exactly one lethal event"); } }
