package dev.pvpbot.bot.entity;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;

public final class BotHandle {
    private final NPC npc;
    private final BotAnimation animation;
    private Player lastEntity;
    private int entityRevision;

    public BotHandle(NPC npc, Player initialEntity, BotAnimation animation) {
        this.npc = npc;
        this.lastEntity = initialEntity;
        this.animation = animation;
    }

    public NPC npc() { return npc; }

    public Player entity() {
        if (npc != null && npc.getEntity() instanceof Player current && current != lastEntity) {
            lastEntity = current;
            entityRevision++;
        }
        return lastEntity;
    }

    public void playAttackAnimation(Player viewer) {
        Player current = entity();
        if (current != null) animation.playAttack(current, viewer);
    }

    public int entityRevision() { return entityRevision; }
    public boolean isSpawned() { return npc != null && npc.isSpawned(); }

    public void destroy() {
        if (npc == null) return;
        if (npc.isSpawned()) npc.despawn();
        npc.destroy();
    }
}
