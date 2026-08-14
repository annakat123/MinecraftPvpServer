package dev.pvpbot.logic;
import dev.pvpbot.bot.ai.perception.PerceptionSnapshot;
import dev.pvpbot.bot.ai.perception.CombatFrame;
import dev.pvpbot.bot.combat.hitselect.HitSelectController;
import dev.pvpbot.bot.profile.BotProfile;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class HitSelectTest {
    private final HitSelectController c=new HitSelectController();
    private PerceptionSnapshot snapshot(double distance,int incoming,long since){
        Location body=new Location(null,0,64,distance);
        return new PerceptionSnapshot(10,body,body.clone().add(0,1.62,0),new Vector(),CombatFrame.from(0,0,0,distance),distance,.1,0,0,0,0,20,20,incoming,0,since,20,true,true,true);
    }
    @Test void closesWhenOutsideReach(){assertEquals(HitSelectController.Decision.CLOSE_DISTANCE,c.decide(snapshot(4,0,20),BotProfile.defaults("x"),1,.5).decision());}
    @Test void waitsForModernCooldown(){assertEquals(HitSelectController.Decision.WAIT,c.decide(snapshot(2.5,0,20),BotProfile.defaults("x"),.4,.5).decision());}
    @Test void escapesPressureBeforeAttacking(){assertEquals(HitSelectController.Decision.ESCAPE_COMBO,c.decide(snapshot(2.5,2,1),BotProfile.defaults("x"),1,.5).decision());}
    @Test void resumesAttackingAfterShortEscapeWindow(){assertEquals(HitSelectController.Decision.ATTACK_NOW,c.decide(snapshot(2.5,2,7),BotProfile.defaults("x"),1,.5).decision());}
    @Test void counterHitUsesRecentIncomingTiming(){BotProfile p=new BotProfile("x",Map.of("criticals.skill",0d),Map.of());assertEquals(HitSelectController.Decision.COUNTER_HIT,c.decide(snapshot(2.5,0,2),p,1,.5).decision());}
}
