package dev.pvpbot.bot.combat;

import dev.pvpbot.bot.ai.perception.CombatFrame;
import dev.pvpbot.bot.ai.perception.PerceptionSnapshot;
import dev.pvpbot.bot.profile.BotProfile;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.util.random.RandomGenerator;

public final class AimController {
    private final RandomGenerator random; private double errorYaw,errorPitch;
    public AimController(RandomGenerator random){this.random=random;}
    public boolean aim(Player bot,PerceptionSnapshot observation,BotProfile p,double lateralBias){
        Location targetEye=observation.targetEye(); Vector targetVelocity=observation.targetVelocity();
        if(!p.enabled("aim"))return isFacing(bot.getEyeLocation().getDirection(),targetEye.toVector().subtract(bot.getEyeLocation().toVector()),25); double accuracy=p.value("aim.accuracy"), prediction=p.value("aim.predictionStrength");
        CombatFrame frame=observation.combatFrame();
        Vector predicted=targetEye.toVector().add(targetVelocity.multiply(prediction*2.2));
        applyLateralBias(predicted,frame,lateralBias);
        Location from=bot.getEyeLocation(); Vector delta=predicted.subtract(from.toVector()); double flat=Math.sqrt(delta.getX()*delta.getX()+delta.getZ()*delta.getZ());
        float wantedYaw=(float)Math.toDegrees(Math.atan2(-delta.getX(),delta.getZ())); float wantedPitch=(float)-Math.toDegrees(Math.atan2(delta.getY(),flat));
        if(random.nextDouble()>.78){errorYaw=(random.nextGaussian()*(1-accuracy)*13);errorPitch=(random.nextGaussian()*(1-accuracy)*7);}
        float yaw=approach(bot.getYaw(),(float)(wantedYaw+errorYaw),p.value("aim.maxYawSpeed")); float pitch=approach(bot.getPitch(),(float)(wantedPitch+errorPitch),p.value("aim.maxPitchSpeed")); bot.setRotation(yaw,Math.max(-89,Math.min(89,pitch)));
        return Math.abs(wrap(wantedYaw-yaw)) < 10+18*(1-accuracy) && Math.abs(wantedPitch-pitch)<8+12*(1-accuracy);
    }
    private static float approach(float from,float to,double max){float d=wrap(to-from);return from+(float)Math.max(-max,Math.min(max,d));}
    private static float wrap(float a){while(a>180)a-=360;while(a<-180)a+=360;return a;}
    public static Vector applyLateralBias(Vector point,CombatFrame frame,double lateralBias){return point.add(new Vector(frame.rightX()*lateralBias,0,frame.rightZ()*lateralBias));}
    public static boolean isFacing(Vector look,Vector toward,double coneDegrees){if(look.lengthSquared()==0||toward.lengthSquared()==0)return false;return look.clone().normalize().dot(toward.clone().normalize())>=Math.cos(Math.toRadians(coneDegrees));}
}
