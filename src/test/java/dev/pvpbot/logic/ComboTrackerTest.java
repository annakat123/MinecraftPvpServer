package dev.pvpbot.logic;
import dev.pvpbot.bot.combat.combo.ComboTracker;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ComboTrackerTest { @Test void opponentHitResetsCombo(){ComboTracker c=new ComboTracker();c.playerHit(1);c.playerHit(2);assertEquals(2,c.playerCombo());c.botHit(3);assertEquals(0,c.playerCombo());assertEquals(1,c.botCombo());assertEquals(2,c.longestPlayer());} @Test void staleComboExpires(){ComboTracker c=new ComboTracker();c.playerHit(100);c.playerHit(101);c.expire(127,25);assertEquals(0,c.playerCombo());assertEquals(0,c.botCombo());assertEquals(2,c.longestPlayer());} }
