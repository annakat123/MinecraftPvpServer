package dev.pvpbot.bot.movement;

import dev.pvpbot.arena.Arena;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.Decision;
import dev.pvpbot.bot.profile.BotProfile;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.util.Random;

public final class MovementController {
    public static final double MAX_HORIZONTAL_SPEED=.275;
    private final Random random=new Random(); private int strafe=1; private boolean strafeActive; private long nextSwitch; private int sprintPauseTicks;
    public void tick(Player bot,Location targetLocation,Arena arena,BotProfile p,Decision decision,long tick,int incomingCombo){
        Vector toward=targetLocation.toVector().subtract(bot.getLocation().toVector()); toward.setY(0); double distance=toward.length(); if(distance>.001)toward.normalize();
        if(tick>=nextSwitch){strafe=random.nextBoolean()?1:-1;strafeActive=p.enabled("strafe")&&random.nextDouble()<p.value("strafe.chance");double skill=p.value("strafe.skill");nextSwitch=tick+6+random.nextInt(Math.max(1,(int)(18-8*skill)));}
        Vector lateral=new Vector(-toward.getZ(),0,toward.getX()).multiply(strafe); double preferred=p.enabled("spacing")?p.value("spacing.preferredDistance"):2.8, forward=0;
        if(decision==Decision.CLOSE_DISTANCE||decision==Decision.COMBO_CHASE||distance>preferred+.25)forward=(.24+.08*p.value("spacing.forwardPressure"))*(decision==Decision.COMBO_CHASE?.65+.35*p.value("combo.chaseSkill"):1);
        else if(decision==Decision.ESCAPE_COMBO||decision==Decision.BAIT_ATTACK||distance<preferred-.25)forward=(-.15-.07*p.value("spacing.skill"))*(decision==Decision.ESCAPE_COMBO?.65+.35*p.value("combo.escapeSkill"):1);
        double side=strafeActive ? .08+.09*p.value("strafe.intensity") : 0;
        Vector move=toward.multiply(forward).add(lateral.multiply(side)); Vector toCenter=arena.center().toVector().subtract(bot.getLocation().toVector()).setY(0); if(!arena.contains(bot.getLocation())||toCenter.length()>arena.halfSize()-2)move=toCenter.normalize().multiply(.32);
        move=clampHorizontal(move,MAX_HORIZONTAL_SPEED);move.setY(bot.getVelocity().getY()); bot.setVelocity(move); if(sprintPauseTicks>0){bot.setSprinting(false);sprintPauseTicks--;}else bot.setSprinting(true);
        if(incomingCombo>0&&p.enabled("jumpReset")&&bot.isOnGround()&&random.nextDouble()<p.value("jumpReset.chance")*p.value("jumpReset.skill")*.14)bot.setVelocity(bot.getVelocity().setY(.42));
    }
    public void afterAttack(Player bot,BotProfile p){double reset=p.enabled("sprintReset")?p.value("sprintReset.skill"):0;if(p.enabled("wTap")&&random.nextDouble()<p.value("wTap.chance")*p.value("wTap.skill")*reset)sprintPauseTicks=1;if(p.enabled("sTap")&&random.nextDouble()<p.value("sTap.chance")*p.value("sTap.skill")*reset){Vector back=bot.getLocation().getDirection().setY(0).normalize().multiply(-.16);back.setY(bot.getVelocity().getY());bot.setVelocity(back);}}
    public static Vector clampHorizontal(Vector vector,double maximum){double speed=Math.hypot(vector.getX(),vector.getZ());if(speed>maximum&&speed>0){double scale=maximum/speed;vector.setX(vector.getX()*scale);vector.setZ(vector.getZ()*scale);}return vector;}
    public int strafeDirection(){return strafe;}
}
