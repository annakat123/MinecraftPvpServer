package dev.pvpbot.bot.ai.adaptation;
import dev.pvpbot.bot.ai.perception.PerceptionSnapshot;
import dev.pvpbot.bot.profile.BotProfile;
public final class AdaptationController {
    private final PlayerBehaviorModel model=new PlayerBehaviorModel();
    private long lastObservedTick=Long.MIN_VALUE;
    public PlayerBehaviorModel model(){return model;}
    public void observe(PerceptionSnapshot observation){
        if(observation.tick()==lastObservedTick)return;
        model.observe(observation.closingSpeed(),observation.lateralVelocity(),!observation.playerOnGround());
        lastObservedTick=observation.tick();
    }
    public double aggression(BotProfile p){if(!p.enabled("adaptation"))return .5; double strength=p.value("adaptation.strength")*model.confidence(); return .5+(model.aggression()-.5)*strength;}
    public double aimLateralBias(BotProfile p){if(!p.enabled("adaptation"))return 0; return model.lateralBias()*p.value("adaptation.strength")*model.confidence()*.18;}
}
