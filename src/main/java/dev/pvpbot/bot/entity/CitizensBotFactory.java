package dev.pvpbot.bot.entity;

import dev.pvpbot.duel.KitDefinition;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import java.util.logging.Logger;

public final class CitizensBotFactory {
    private final SkinProvider skins; private final KitDefinition kit; private final Logger logger;
    public CitizensBotFactory(SkinProvider skins,KitDefinition kit,Logger logger){this.skins=skins;this.kit=kit;this.logger=logger;}
    public BotHandle spawn(Location location){ NPC npc=CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER,"PracticeBot"); npc.setProtected(false); npc.data().setPersistent(NPC.Metadata.DAMAGE_OTHERS,true); String skin=skins.randomSkin(); try{SkinTrait trait=npc.getOrAddTrait(SkinTrait.class);trait.setShouldUpdateSkins(false);trait.setSkinName(skin,false);}catch(RuntimeException e){logger.warning("Skin '"+skin+"' unavailable; fallback will be used: "+e.getMessage()); try{SkinTrait trait=npc.getOrAddTrait(SkinTrait.class);trait.setShouldUpdateSkins(false);trait.setSkinName(skins.fallbackSkin(),false);}catch(RuntimeException fallbackError){logger.warning("Fallback skin unavailable; duel continues with client default skin: "+fallbackError.getMessage());}} if(!npc.spawn(location)){npc.destroy();throw new IllegalStateException("Citizens could not spawn PracticeBot");} if(!(npc.getEntity() instanceof Player player)){npc.destroy();throw new IllegalStateException("Citizens NPC is not a player entity");} kit.apply(player);syncEquipment(npc,player);player.setSprinting(true); return new BotHandle(npc,player); }
    private static void syncEquipment(NPC npc,Player player){Equipment equipment=npc.getOrAddTrait(Equipment.class);equipment.set(Equipment.EquipmentSlot.HAND,player.getInventory().getItemInMainHand());equipment.set(Equipment.EquipmentSlot.HELMET,player.getInventory().getHelmet());equipment.set(Equipment.EquipmentSlot.CHESTPLATE,player.getInventory().getChestplate());equipment.set(Equipment.EquipmentSlot.LEGGINGS,player.getInventory().getLeggings());equipment.set(Equipment.EquipmentSlot.BOOTS,player.getInventory().getBoots());}
}
