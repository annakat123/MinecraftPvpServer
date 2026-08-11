package dev.pvpbot.logic;
import dev.pvpbot.bot.ai.perception.LatencyBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LatencyBufferTest { @Test void exposesOnlyMatureSnapshotsAndKeepsLatest(){LatencyBuffer<String>b=new LatencyBuffer<>();b.offer(100,50,"a");b.offer(110,60,"b");assertTrue(b.poll(149).isEmpty());assertEquals("a",b.poll(150).orElseThrow());assertEquals("a",b.poll(169).orElseThrow());assertEquals("b",b.poll(170).orElseThrow());} }
