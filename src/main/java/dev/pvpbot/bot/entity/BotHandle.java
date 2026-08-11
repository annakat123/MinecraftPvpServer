package dev.pvpbot.bot.entity;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;

public final class BotHandle {
    private final NPC npc;
    private Player lastEntity;

    public BotHandle(NPC npc, Player initialEntity) {
        this.npc = npc;
        this.lastEntity = initialEntity;
    }

    public NPC npc() { return npc; }

    public Player entity() {
        if (npc.getEntity() instanceof Player current) lastEntity = current;
        return lastEntity;
    }

    public void destroy() {
        if (npc.isSpawned()) npc.despawn();
        npc.destroy();
    }
}
