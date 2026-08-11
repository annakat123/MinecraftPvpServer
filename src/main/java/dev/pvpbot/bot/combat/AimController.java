package dev.pvpbot.bot.combat;

import dev.pvpbot.bot.profile.BotProfile;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.util.Random;

public final class AimController {
    private final Random random=new Random(); private double errorYaw,errorPitch;
    public boolean aim(Player bot,Player target,BotProfile p,double lateralBias){
        if(!p.enabled("aim"))return true; double accuracy=p.value("aim.accuracy"), prediction=p.value("aim.predictionStrength");
        Vector predicted=target.getEyeLocation().toVector().add(target.getVelocity().multiply(prediction*2.2)); predicted.add(new Vector(lateralBias,0,0));
        Location from=bot.getEyeLocation(); Vector delta=predicted.subtract(from.toVector()); double flat=Math.sqrt(delta.getX()*delta.getX()+delta.getZ()*delta.getZ());
        float wantedYaw=(float)Math.toDegrees(Math.atan2(-delta.getX(),delta.getZ())); float wantedPitch=(float)-Math.toDegrees(Math.atan2(delta.getY(),flat));
        if(random.nextDouble()>.78){errorYaw=(random.nextGaussian()*(1-accuracy)*13);errorPitch=(random.nextGaussian()*(1-accuracy)*7);}
        float yaw=approach(bot.getYaw(),(float)(wantedYaw+errorYaw),p.value("aim.maxYawSpeed")); float pitch=approach(bot.getPitch(),(float)(wantedPitch+errorPitch),p.value("aim.maxPitchSpeed")); bot.setRotation(yaw,Math.max(-89,Math.min(89,pitch)));
        return Math.abs(wrap(wantedYaw-yaw)) < 10+18*(1-accuracy) && Math.abs(wantedPitch-pitch)<8+12*(1-accuracy);
    }
    private static float approach(float from,float to,double max){float d=wrap(to-from);return from+(float)Math.max(-max,Math.min(max,d));}
    private static float wrap(float a){while(a>180)a-=360;while(a<-180)a+=360;return a;}
}
