package dev.pvpbot.bot.ai.perception;

/**
 * Immutable horizontal coordinate frame captured with one perception snapshot.
 * Forward points from the bot to the target; right is forward cross world-up.
 */
public record CombatFrame(double forwardX, double forwardZ, double rightX, double rightZ) {
    private static final double MIN_LENGTH_SQUARED = 1.0e-12;
    private static final CombatFrame DEGENERATE = new CombatFrame(0, 0, 0, 0);

    public static CombatFrame from(double botX, double botZ, double targetX, double targetZ) {
        double x = targetX - botX;
        double z = targetZ - botZ;
        double lengthSquared = x * x + z * z;
        if (lengthSquared <= MIN_LENGTH_SQUARED) return DEGENERATE;
        double inverseLength = 1.0 / Math.sqrt(lengthSquared);
        double forwardX = x * inverseLength;
        double forwardZ = z * inverseLength;
        return new CombatFrame(forwardX, forwardZ, -forwardZ, forwardX);
    }

    public RelativeMotion project(double velocityX, double velocityZ) {
        return new RelativeMotion(
                velocityX * forwardX + velocityZ * forwardZ,
                velocityX * rightX + velocityZ * rightZ
        );
    }

    /** Per captured tick: positive means distance decreased; negative means it increased. */
    public static double closingSpeed(double previousDistance, double currentDistance) {
        return previousDistance - currentDistance;
    }

    public boolean degenerate() {
        return forwardX == 0 && forwardZ == 0;
    }
}
