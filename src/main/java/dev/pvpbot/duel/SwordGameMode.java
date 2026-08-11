package dev.pvpbot.duel;
public record SwordGameMode(KitDefinition kit) implements GameModeDefinition { public String id(){return "SWORD";} public MatchRule rule(){return new MatchRule(1);} }
