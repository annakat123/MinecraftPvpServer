package dev.pvpbot.bot.entity;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
public record BotHandle(NPC npc, Player entity) { public boolean valid(){return npc.isSpawned()&&entity.isValid();} public void destroy(){if(npc.isSpawned())npc.despawn();npc.destroy();} }
