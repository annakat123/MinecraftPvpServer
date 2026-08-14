package dev.pvpbot.bot.entity;

import dev.pvpbot.duel.KitDefinition;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.npc.skin.Skin;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import java.util.logging.Logger;

public final class CitizensBotFactory {
    private final SkinProvider skins;
    private final KitDefinition kit;
    private final Logger logger;
    private final BotAnimation animation = new CitizensBotAnimation();
    private final SkinCacheWarmer skinCache = new SkinCacheWarmer(new CitizensSkinProfileFetcher());

    public CitizensBotFactory(SkinProvider skins, KitDefinition kit, Logger logger) {
        this.skins = skins;
        this.kit = kit;
        this.logger = logger;
    }

    public BotHandle spawn(Location location) {
        String requestedSkin = skins.randomSkin();
        StableSkinSelection.Result skinSelection = StableSkinSelection.select(
                requestedSkin,
                skins.fallbackSkin(),
                this::isReady
        );
        if (!isReady(requestedSkin)) skinCache.warm(requestedSkin);

        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "PracticeBot");
        npc.setProtected(false);
        npc.data().setPersistent(NPC.Metadata.DAMAGE_OTHERS, true);
        configureStableSkinBeforeSpawn(npc, skinSelection);

        if (!npc.spawn(location)) {
            npc.destroy();
            throw new IllegalStateException("Citizens could not spawn PracticeBot");
        }
        if (!(npc.getEntity() instanceof Player player)) {
            npc.destroy();
            throw new IllegalStateException("Citizens NPC is not a player entity");
        }
        kit.apply(player);
        syncEquipment(npc, player);
        player.setSprinting(true);
        return new BotHandle(npc, player, animation);
    }

    private void configureStableSkinBeforeSpawn(NPC npc, StableSkinSelection.Result selection) {
        SkinTrait trait = npc.getOrAddTrait(SkinTrait.class);
        trait.setShouldUpdateSkins(false);
        trait.setFetchDefaultSkin(false);
        selection.skinName().ifPresent(name -> skinCache.ready(name).ifPresentOrElse(
                skin -> trait.setSkinPersistent(skin.skinName(), skin.signature(), skin.texture()),
                () -> trait.setSkinName(name, false)
        ));
        if (selection.source() == StableSkinSelection.Source.CLIENT_DEFAULT) {
            logger.fine("Requested Citizens skin is not already resolved; using stable client default for this duel");
        }
    }

    private boolean isReady(String name) {
        return skinCache.isReady(name) || isCachedAndReady(name);
    }

    private static boolean isCachedAndReady(String name) {
        try {
            Skin skin = Skin.get(name, false);
            return skin.hasSkinData() && skin.isValid();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void syncEquipment(NPC npc,Player player){Equipment equipment=npc.getOrAddTrait(Equipment.class);equipment.set(Equipment.EquipmentSlot.HAND,player.getInventory().getItemInMainHand());equipment.set(Equipment.EquipmentSlot.HELMET,player.getInventory().getHelmet());equipment.set(Equipment.EquipmentSlot.CHESTPLATE,player.getInventory().getChestplate());equipment.set(Equipment.EquipmentSlot.LEGGINGS,player.getInventory().getLeggings());equipment.set(Equipment.EquipmentSlot.BOOTS,player.getInventory().getBoots());}
}
