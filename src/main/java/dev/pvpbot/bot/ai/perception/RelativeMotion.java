package dev.pvpbot.bot.ai.perception;

/** Horizontal target velocity resolved in the bot-to-target combat frame. */
public record RelativeMotion(double forwardVelocity, double lateralVelocity) {}
