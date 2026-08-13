package dev.pvpbot.bot.ai;

import dev.pvpbot.arena.Arena;
import dev.pvpbot.bot.ai.adaptation.AdaptationController;
import dev.pvpbot.bot.ai.perception.*;
import dev.pvpbot.bot.ai.random.MatchRandom;
import dev.pvpbot.bot.ai.random.MatchRandom.Subsystem;
import dev.pvpbot.bot.combat.*;
import dev.pvpbot.bot.combat.combo.ComboTracker;
import dev.pvpbot.bot.combat.hitselect.HitSelectController;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.Decision;
import dev.pvpbot.bot.entity.BotHandle;
import dev.pvpbot.bot.movement.MovementController;
import dev.pvpbot.bot.profile.BotProfile;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.util.random.RandomGenerator;

public final class BotBrain {
    public interface Telemetry { void botAttackAttempt(boolean connected,boolean critical); }
    private record TargetObservation(Location body,Location eye,Vector velocity) {}
    private final BotHandle handle; private final Player target; private final Arena arena; private final BotProfile profile; private final Telemetry telemetry;
    private final LatencyBuffer<PerceptionSnapshot> latency=new LatencyBuffer<>(); private final LatencyBuffer<TargetObservation> targetLatency=new LatencyBuffer<>(); private final HitSelectController hitSelect=new HitSelectController(); private final AimController aim; private final CriticalController critical; private final MovementController movement; private final AdaptationController adaptation=new AdaptationController(); private final RandomGenerator decisionRandom;
    private long tick,lastAttack=-100,lastDecision=-100,lastIncoming=-100,lastOutgoing=-100; private double previousDistance; private Decision decision=Decision.WAIT; private boolean aimEligible;
    public BotBrain(BotHandle handle,Player target,Arena arena,BotProfile profile,MatchRandom random,Telemetry telemetry){this.handle=handle;this.target=target;this.arena=arena;this.profile=profile;this.telemetry=telemetry;decisionRandom=random.stream(Subsystem.DECISION);aim=new AimController(random.stream(Subsystem.AIM));critical=new CriticalController(random.stream(Subsystem.CRITICAL));movement=new MovementController(random.stream(Subsystem.MOVEMENT),random.stream(Subsystem.TECHNIQUE));previousDistance=handle.entity().getLocation().distance(target.getLocation());}
    public void tick(ComboTracker combo){tick++;combo.expire(org.bukkit.Bukkit.getCurrentTick(),25);Player bot=handle.entity();if(bot==null||!target.isOnline())return;double distance=bot.getLocation().distance(target.getLocation()),closing=previousDistance-distance;previousDistance=distance; Vector rel=target.getVelocity();adaptation.model().observe(closing,rel.getX()+rel.getZ(),!target.isOnGround());
        boolean grounded=grounded(bot);PerceptionSnapshot raw=new PerceptionSnapshot(tick,distance,closing,target.getVelocity().getY(),bot.getVelocity().getY(),bot.getHealth(),target.getHealth(),combo.playerCombo(),combo.botCombo(),tick-lastIncoming,tick-lastOutgoing,bot.hasLineOfSight(target),grounded,target.isOnGround());long now=System.currentTimeMillis();int ping=profile.millis("simulatedPingMs");latency.offer(now,ping,raw);targetLatency.offer(now,ping,new TargetObservation(target.getLocation().clone(),target.getEyeLocation().clone(),target.getVelocity().clone()));var perceived=latency.poll(now);var observed=targetLatency.poll(now);if(perceived.isEmpty()||observed.isEmpty())return;
        int reactionTicks=Math.max(1,(profile.millis("baseReactionMs")+decisionRandom.nextInt(profile.millis("reactionJitterMs")*2+1)-profile.millis("reactionJitterMs"))/50);if(tick-lastDecision>=reactionTicks){double cooldown=Math.min(1,(tick-lastAttack)/12.5);decision=hitSelect.decide(perceived.get(),profile,cooldown,adaptation.aggression(profile));lastDecision=tick;}
        TargetObservation view=observed.get();aimEligible=aim.aim(bot,view.eye(),view.velocity(),profile,adaptation.aimLateralBias(profile));if(grounded&&tick-lastIncoming>6)movement.tick(bot,view.body(),arena,profile,decision,tick,combo.playerCombo());
        if(decision==Decision.CRITICAL_ATTACK){if(bot.isOnGround()&&critical.tryStart(bot,profile)){return;}if(Math.abs(bot.getVelocity().getY())>.02&&!critical.criticalWindow(bot))return;} double reach=profile.enabled("reach")?profile.value("reach.blocks"):3.0;boolean attackWatchdog=tick-lastAttack>=30&&perceived.get().distance()<=reach&&perceived.get().lineOfSight();boolean liveFacing=AimController.isFacing(bot.getEyeLocation().getDirection(),target.getEyeLocation().toVector().subtract(bot.getEyeLocation().toVector()),25);boolean attackDirectionValid=profile.enabled("aim")?aimEligible:liveFacing;boolean liveAttackValid=bot.getLocation().distance(target.getLocation())<=reach+.1&&bot.hasLineOfSight(target);if((isAttack(decision)||attackWatchdog)&&tick-lastAttack>=10&&perceived.get().distance()<=reach&&perceived.get().lineOfSight()&&attackDirectionValid&&liveAttackValid){boolean crit=critical.criticalWindow(bot);bot.attack(target);bot.swingMainHand();lastAttack=tick;lastOutgoing=tick;movement.afterAttack(bot,profile);telemetry.botAttackAttempt(true,crit);}
    }
    private static boolean isAttack(Decision d){return d==Decision.ATTACK_NOW||d==Decision.COUNTER_HIT||d==Decision.CRITICAL_ATTACK;}
    private static boolean grounded(Player bot){if(bot.isOnGround())return true;if(Math.abs(bot.getVelocity().getY())>.02)return false;return !bot.getLocation().clone().subtract(0,.15,0).getBlock().isPassable();}
    public void incomingHit(){lastIncoming=tick;} public void outgoingHit(){lastOutgoing=tick;} public Decision decision(){return decision;} public int perceptionAgeTicks(){return Math.max(0,profile.millis("simulatedPingMs")/50);} public int strafeDirection(){return movement.strafeDirection();} public double distance(){return previousDistance;} public double cooldown(){return Math.min(1,(tick-lastAttack)/12.5);} public double adaptationConfidence(){return adaptation.model().confidence();} public boolean sprinting(){Player bot=handle.entity();return bot!=null&&bot.isSprinting();}
}
