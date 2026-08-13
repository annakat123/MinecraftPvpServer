package dev.pvpbot.bot.combat;
import dev.pvpbot.bot.profile.BotProfile;
import org.bukkit.entity.Player;
import java.util.random.RandomGenerator;
public final class CriticalController { private final RandomGenerator random; public CriticalController(RandomGenerator random){this.random=random;} public boolean tryStart(Player bot,BotProfile p){if(!p.enabled("criticals")||!bot.isOnGround()||random.nextDouble()>p.value("criticals.chance")*p.value("criticals.skill"))return false;bot.setVelocity(bot.getVelocity().setY(.34));return true;} public boolean criticalWindow(Player bot){return !bot.isOnGround()&&bot.getVelocity().getY()<0&&!bot.isInWater();} }
