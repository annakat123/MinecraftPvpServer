package dev.pvpbot.bot.ai;

import dev.pvpbot.arena.Arena;
import dev.pvpbot.bot.ai.adaptation.AdaptationController;
import dev.pvpbot.bot.ai.perception.*;
import dev.pvpbot.bot.combat.*;
import dev.pvpbot.bot.combat.combo.ComboTracker;
import dev.pvpbot.bot.combat.hitselect.HitSelectController;
import dev.pvpbot.bot.combat.hitselect.HitSelectController.Decision;
import dev.pvpbot.bot.movement.MovementController;
import dev.pvpbot.bot.profile.BotProfile;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.util.Random;

public final class BotBrain {
    public interface Telemetry { void botAttackAttempt(boolean connected,boolean critical); }
    private final Player bot,target; private final Arena arena; private final BotProfile profile; private final Telemetry telemetry;
    private final LatencyBuffer<PerceptionSnapshot> latency=new LatencyBuffer<>(); private final HitSelectController hitSelect=new HitSelectController(); private final AimController aim=new AimController(); private final CriticalController critical=new CriticalController(); private final MovementController movement=new MovementController(); private final AdaptationController adaptation=new AdaptationController(); private final Random random=new Random();
    private long tick,lastAttack=-100,lastDecision=-100,lastIncoming=-100,lastOutgoing=-100; private double previousDistance; private Decision decision=Decision.WAIT; private boolean aimEligible;
    public BotBrain(Player bot,Player target,Arena arena,BotProfile profile,Telemetry telemetry){this.bot=bot;this.target=target;this.arena=arena;this.profile=profile;this.telemetry=telemetry;previousDistance=bot.getLocation().distance(target.getLocation());}
    public void tick(ComboTracker combo){tick++;if(bot.isDead()||!target.isOnline())return;double distance=bot.getLocation().distance(target.getLocation()),closing=previousDistance-distance;previousDistance=distance; Vector rel=target.getVelocity();adaptation.model().observe(closing,rel.getX()+rel.getZ(),!target.isOnGround());
        PerceptionSnapshot raw=new PerceptionSnapshot(tick,distance,closing,target.getVelocity().getY(),bot.getVelocity().getY(),bot.getHealth(),target.getHealth(),combo.playerCombo(),combo.botCombo(),tick-lastIncoming,tick-lastOutgoing,bot.hasLineOfSight(target),bot.isOnGround(),target.isOnGround());long now=System.currentTimeMillis();latency.offer(now,profile.millis("simulatedPingMs"),raw);var perceived=latency.poll(now);if(perceived.isEmpty())return;
        int reactionTicks=Math.max(1,(profile.millis("baseReactionMs")+random.nextInt(profile.millis("reactionJitterMs")*2+1)-profile.millis("reactionJitterMs"))/50);if(tick-lastDecision>=reactionTicks){double cooldown=Math.min(1,(tick-lastAttack)/12.5);decision=hitSelect.decide(perceived.get(),profile,cooldown,adaptation.aggression(profile));lastDecision=tick;}
        aimEligible=aim.aim(bot,target,profile,adaptation.aimLateralBias(profile));if(tick-lastIncoming>6)movement.tick(bot,target,arena,profile,decision,tick,combo.playerCombo());
        if(decision==Decision.CRITICAL_ATTACK){if(bot.isOnGround()&&critical.tryStart(bot,profile)){return;}if(!critical.criticalWindow(bot))return;} double reach=profile.enabled("reach")?profile.value("reach.blocks"):3.0;if(isAttack(decision)&&tick-lastAttack>=10&&perceived.get().distance()<=reach&&perceived.get().lineOfSight()&&aimEligible){boolean crit=critical.criticalWindow(bot);bot.attack(target);bot.swingMainHand();lastAttack=tick;lastOutgoing=tick;movement.afterAttack(bot,profile);telemetry.botAttackAttempt(true,crit);}
    }
    private static boolean isAttack(Decision d){return d==Decision.ATTACK_NOW||d==Decision.COUNTER_HIT||d==Decision.CRITICAL_ATTACK;}
    public void incomingHit(){lastIncoming=tick;} public void outgoingHit(){lastOutgoing=tick;} public Decision decision(){return decision;} public int perceptionAgeTicks(){return Math.max(0,profile.millis("simulatedPingMs")/50);} public int strafeDirection(){return movement.strafeDirection();} public double distance(){return previousDistance;} public double cooldown(){return Math.min(1,(tick-lastAttack)/12.5);} public double adaptationConfidence(){return adaptation.model().confidence();} public boolean sprinting(){return bot.isSprinting();}
}
