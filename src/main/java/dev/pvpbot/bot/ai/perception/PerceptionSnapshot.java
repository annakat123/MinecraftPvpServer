package dev.pvpbot.bot.ai.perception;
public record PerceptionSnapshot(long tick, double distance, double closingSpeed, double playerVerticalVelocity, double botVerticalVelocity, double botHealth, double playerHealth, int incomingCombo, int outgoingCombo, long ticksSinceIncomingHit, long ticksSinceOutgoingHit, boolean lineOfSight, boolean botOnGround, boolean playerOnGround) {}
