package dev.pvpbot.bot.combat;

import dev.pvpbot.bot.ai.perception.CombatFrame;
import dev.pvpbot.bot.ai.perception.PerceptionSnapshot;
import dev.pvpbot.bot.profile.BotProfile;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.random.RandomGenerator;

public final class AimController {
    public record AimPlan(Vector targetPoint, double errorYaw, double errorPitch, double accuracy) {
        public AimPlan {
            targetPoint = Objects.requireNonNull(targetPoint, "targetPoint").clone();
        }

        @Override public Vector targetPoint() { return targetPoint.clone(); }
    }

    public record Rotation(float yaw, float pitch, float wantedYaw, float wantedPitch) {}

    private final RandomGenerator random;
    private double errorYaw;
    private double errorPitch;

    public AimController(RandomGenerator random) {
        this.random = random;
    }

    /** Creates a held target point exclusively from the matured observation. */
    public AimPlan plan(PerceptionSnapshot observation, BotProfile profile, double lateralBias) {
        double accuracy = profile.value("aim.accuracy");
        Vector predicted = observation.targetEye().toVector()
                .add(observation.targetVelocity().multiply(profile.value("aim.predictionStrength") * 2.2));
        applyLateralBias(predicted, observation.combatFrame(), lateralBias);
        // Preserve the persistent 1.0.7 error process, but sample it only on aim replans.
        if (random.nextDouble() > .78) {
            errorYaw = random.nextGaussian() * (1 - accuracy) * 13;
            errorPitch = random.nextGaussian() * (1 - accuracy) * 7;
        }
        return new AimPlan(predicted, errorYaw, errorPitch, accuracy);
    }

    /** Smooth motor execution runs every server tick toward the held plan. */
    public boolean execute(Player bot, AimPlan plan, BotProfile profile) {
        Location from = bot.getEyeLocation();
        Rotation rotation = nextRotation(
                plan,
                from.toVector(),
                bot.getYaw(),
                bot.getPitch(),
                profile.value("aim.maxYawSpeed"),
                profile.value("aim.maxPitchSpeed")
        );
        if (!profile.enabled("aim")) return withinAimTolerance(rotation, plan.accuracy());
        bot.setRotation(rotation.yaw(), rotation.pitch());
        Rotation executed = new Rotation(
                bot.getYaw(),
                bot.getPitch(),
                rotation.wantedYaw(),
                rotation.wantedPitch()
        );
        return withinAimTolerance(executed, plan.accuracy());
    }

    public static Rotation nextRotation(AimPlan plan, Vector from, float currentYaw, float currentPitch,
                                        double maxYawSpeed, double maxPitchSpeed) {
        Vector delta = plan.targetPoint().subtract(from);
        double flat = Math.hypot(delta.getX(), delta.getZ());
        float wantedYaw = (float) Math.toDegrees(Math.atan2(-delta.getX(), delta.getZ()));
        float wantedPitch = (float) -Math.toDegrees(Math.atan2(delta.getY(), flat));
        float yaw = approach(currentYaw, (float) (wantedYaw + plan.errorYaw()), maxYawSpeed);
        float pitch = approach(currentPitch, (float) (wantedPitch + plan.errorPitch()), maxPitchSpeed);
        return new Rotation(yaw, Math.max(-89, Math.min(89, pitch)), wantedYaw, wantedPitch);
    }

    public static boolean withinAimTolerance(Rotation rotation, double accuracy) {
        return Math.abs(wrap(rotation.wantedYaw() - rotation.yaw())) < 10 + 18 * (1 - accuracy)
                && Math.abs(rotation.wantedPitch() - rotation.pitch()) < 8 + 12 * (1 - accuracy);
    }

    private static float approach(float from, float to, double max) {
        float difference = wrap(to - from);
        return from + (float) Math.max(-max, Math.min(max, difference));
    }

    private static float wrap(float angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }

    public static Vector applyLateralBias(Vector point, CombatFrame frame, double lateralBias) {
        return point.add(new Vector(frame.rightX() * lateralBias, 0, frame.rightZ() * lateralBias));
    }

    public static boolean isFacing(Vector look, Vector toward, double coneDegrees) {
        if (look.lengthSquared() == 0 || toward.lengthSquared() == 0) return false;
        return look.clone().normalize().dot(toward.clone().normalize()) >= Math.cos(Math.toRadians(coneDegrees));
    }
}
