package dev.pvpbot.bot.movement;

import dev.pvpbot.arena.Arena;
import dev.pvpbot.bot.ai.perception.CombatFrame;
import dev.pvpbot.bot.ai.perception.PerceptionSnapshot;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.Decision;
import dev.pvpbot.bot.profile.BotProfile;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.random.RandomGenerator;

public final class MovementController {
    public static final double MAX_HORIZONTAL_SPEED = .275;

    public record MovementPlan(
            double forwardX,
            double forwardZ,
            double rightX,
            double rightZ,
            double forwardSpeed,
            int incomingCombo,
            boolean active
    ) {}

    private final RandomGenerator movementRandom;
    private final RandomGenerator techniqueRandom;
    private int strafe = 1;
    private boolean strafeActive;
    private long nextSwitch;
    private int sprintPauseTicks;

    public MovementController(RandomGenerator movementRandom, RandomGenerator techniqueRandom) {
        this.movementRandom = movementRandom;
        this.techniqueRandom = techniqueRandom;
    }

    /** Creates a held strategy exclusively from matured perception and held decision. */
    public MovementPlan plan(PerceptionSnapshot observation, BotProfile profile, Decision decision) {
        CombatFrame frame = observation.combatFrame();
        double distance = observation.distance();
        double preferred = profile.enabled("spacing") ? profile.value("spacing.preferredDistance") : 2.8;
        double forward = 0;
        if (decision == Decision.CLOSE_DISTANCE || decision == Decision.COMBO_CHASE || distance > preferred + .25) {
            forward = (.24 + .08 * profile.value("spacing.forwardPressure"))
                    * (decision == Decision.COMBO_CHASE ? .65 + .35 * profile.value("combo.chaseSkill") : 1);
        } else if (decision == Decision.ESCAPE_COMBO || decision == Decision.BAIT_ATTACK || distance < preferred - .25) {
            forward = (-.15 - .07 * profile.value("spacing.skill"))
                    * (decision == Decision.ESCAPE_COMBO ? .65 + .35 * profile.value("combo.escapeSkill") : 1);
        }
        return new MovementPlan(
                frame.forwardX(), frame.forwardZ(), frame.rightX(), frame.rightZ(), forward,
                observation.incomingCombo(), observation.botOnGround() && observation.ticksSinceIncomingHit() > 6
        );
    }

    /** Executes held movement every tick; strafe switching remains an internal motor cadence. */
    public void execute(Player bot, MovementPlan plan, Arena arena, BotProfile profile, long tick) {
        if (!plan.active()) return;
        updateStrafe(profile, tick);
        Vector move = plannedHorizontalVelocity(plan, strafe, strafeActive, profile.value("strafe.intensity"));
        Vector toCenter = arena.center().toVector().subtract(bot.getLocation().toVector()).setY(0);
        if (!arena.contains(bot.getLocation()) || toCenter.length() > arena.halfSize() - 2) {
            move = toCenter.normalize().multiply(.32);
        }
        clampHorizontal(move, MAX_HORIZONTAL_SPEED);
        move.setY(bot.getVelocity().getY());
        bot.setVelocity(move);
        if (sprintPauseTicks > 0) {
            bot.setSprinting(false);
            sprintPauseTicks--;
        } else {
            bot.setSprinting(true);
        }
        if (plan.incomingCombo() > 0 && profile.enabled("jumpReset") && bot.isOnGround()
                && techniqueRandom.nextDouble() < profile.value("jumpReset.chance") * profile.value("jumpReset.skill") * .14) {
            bot.setVelocity(bot.getVelocity().setY(.42));
        }
    }

    private void updateStrafe(BotProfile profile, long tick) {
        if (tick < nextSwitch) return;
        strafe = movementRandom.nextBoolean() ? 1 : -1;
        strafeActive = profile.enabled("strafe") && movementRandom.nextDouble() < profile.value("strafe.chance");
        double skill = profile.value("strafe.skill");
        nextSwitch = tick + 6 + movementRandom.nextInt(Math.max(1, (int) (18 - 8 * skill)));
    }

    public static Vector plannedHorizontalVelocity(MovementPlan plan, int strafeDirection,
                                                   boolean strafeActive, double strafeIntensity) {
        Vector toward = new Vector(plan.forwardX(), 0, plan.forwardZ()).multiply(plan.forwardSpeed());
        double side = strafeActive ? .08 + .09 * strafeIntensity : 0;
        Vector lateral = new Vector(plan.rightX(), 0, plan.rightZ()).multiply(strafeDirection * side);
        return toward.add(lateral);
    }

    public void afterAttack(Player bot, BotProfile profile) {
        double reset = profile.enabled("sprintReset") ? profile.value("sprintReset.skill") : 0;
        if (profile.enabled("wTap") && techniqueRandom.nextDouble() < profile.value("wTap.chance") * profile.value("wTap.skill") * reset) sprintPauseTicks = 1;
        if (profile.enabled("sTap") && techniqueRandom.nextDouble() < profile.value("sTap.chance") * profile.value("sTap.skill") * reset) {
            Vector back = bot.getLocation().getDirection().setY(0).normalize().multiply(-.16);
            back.setY(bot.getVelocity().getY());
            bot.setVelocity(back);
        }
    }

    public static Vector clampHorizontal(Vector vector, double maximum) {
        double speed = Math.hypot(vector.getX(), vector.getZ());
        if (speed > maximum && speed > 0) {
            double scale = maximum / speed;
            vector.setX(vector.getX() * scale);
            vector.setZ(vector.getZ() * scale);
        }
        return vector;
    }

    public int strafeDirection() { return strafe; }
}
