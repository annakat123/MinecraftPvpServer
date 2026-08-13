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
    public static final double MAX_HORIZONTAL_SPEED=.275;
    private final RandomGenerator movementRandom; private final RandomGenerator techniqueRandom; private int strafe=1; private boolean strafeActive; private long nextSwitch; private int sprintPauseTicks;
    public MovementController(RandomGenerator movementRandom,RandomGenerator techniqueRandom){this.movementRandom=movementRandom;this.techniqueRandom=techniqueRandom;}
    public void tick(Player bot,PerceptionSnapshot observation,Arena arena,BotProfile p,Decision decision,long tick){
        CombatFrame frame=observation.combatFrame(); Vector toward=new Vector(frame.forwardX(),0,frame.forwardZ()); double distance=observation.distance();
        if(tick>=nextSwitch){strafe=movementRandom.nextBoolean()?1:-1;strafeActive=p.enabled("strafe")&&movementRandom.nextDouble()<p.value("strafe.chance");double skill=p.value("strafe.skill");nextSwitch=tick+6+movementRandom.nextInt(Math.max(1,(int)(18-8*skill)));}
        Vector lateral=new Vector(frame.rightX(),0,frame.rightZ()).multiply(strafe); double preferred=p.enabled("spacing")?p.value("spacing.preferredDistance"):2.8, forward=0;
        if(decision==Decision.CLOSE_DISTANCE||decision==Decision.COMBO_CHASE||distance>preferred+.25)forward=(.24+.08*p.value("spacing.forwardPressure"))*(decision==Decision.COMBO_CHASE?.65+.35*p.value("combo.chaseSkill"):1);
        else if(decision==Decision.ESCAPE_COMBO||decision==Decision.BAIT_ATTACK||distance<preferred-.25)forward=(-.15-.07*p.value("spacing.skill"))*(decision==Decision.ESCAPE_COMBO?.65+.35*p.value("combo.escapeSkill"):1);
        double side=strafeActive ? .08+.09*p.value("strafe.intensity") : 0;
        Vector move=toward.multiply(forward).add(lateral.multiply(side)); Vector toCenter=arena.center().toVector().subtract(bot.getLocation().toVector()).setY(0); if(!arena.contains(bot.getLocation())||toCenter.length()>arena.halfSize()-2)move=toCenter.normalize().multiply(.32);
        move=clampHorizontal(move,MAX_HORIZONTAL_SPEED);move.setY(bot.getVelocity().getY()); bot.setVelocity(move); if(sprintPauseTicks>0){bot.setSprinting(false);sprintPauseTicks--;}else bot.setSprinting(true);
        if(observation.incomingCombo()>0&&p.enabled("jumpReset")&&bot.isOnGround()&&techniqueRandom.nextDouble()<p.value("jumpReset.chance")*p.value("jumpReset.skill")*.14)bot.setVelocity(bot.getVelocity().setY(.42));
    }
    public void afterAttack(Player bot,BotProfile p){double reset=p.enabled("sprintReset")?p.value("sprintReset.skill"):0;if(p.enabled("wTap")&&techniqueRandom.nextDouble()<p.value("wTap.chance")*p.value("wTap.skill")*reset)sprintPauseTicks=1;if(p.enabled("sTap")&&techniqueRandom.nextDouble()<p.value("sTap.chance")*p.value("sTap.skill")*reset){Vector back=bot.getLocation().getDirection().setY(0).normalize().multiply(-.16);back.setY(bot.getVelocity().getY());bot.setVelocity(back);}}
    public static Vector clampHorizontal(Vector vector,double maximum){double speed=Math.hypot(vector.getX(),vector.getZ());if(speed>maximum&&speed>0){double scale=maximum/speed;vector.setX(vector.getX()*scale);vector.setZ(vector.getZ()*scale);}return vector;}
    public int strafeDirection(){return strafe;}
}
