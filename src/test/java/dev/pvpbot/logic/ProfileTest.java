package dev.pvpbot.logic;
import dev.pvpbot.bot.profile.BotProfile;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class ProfileTest {
    @Test void clampsAllPublicRanges(){BotProfile p=new BotProfile("custom",Map.of("reach.blocks",99d,"aim.accuracy",-2d,"adaptation.strength",4d),Map.of());assertEquals(6,p.value("reach.blocks"));assertEquals(0,p.value("aim.accuracy"));assertEquals(.75,p.value("adaptation.strength"));}
    @Test void toggleChangesRuntimeFlag(){BotProfile p=BotProfile.defaults("x");assertTrue(p.enabled("aim"));assertFalse(p.toggle("aim").enabled("aim"));}
}
