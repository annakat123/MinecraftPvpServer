package dev.pvpbot.duel.kit;
import dev.pvpbot.duel.KitDefinition;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
public final class SwordKit implements KitDefinition {
    public String id(){return "SWORD";}
    public void apply(Player player) { player.getInventory().clear(); player.getInventory().setItem(0,new ItemStack(Material.DIAMOND_SWORD)); player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET)); player.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE)); player.getInventory().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS)); player.getInventory().setBoots(new ItemStack(Material.DIAMOND_BOOTS)); player.setHealth(20); player.setFoodLevel(20); player.setSaturation(0); }
    public void apply(LivingEntity bot) { var max=bot.getAttribute(Attribute.MAX_HEALTH); if(max!=null) max.setBaseValue(20); bot.setHealth(20); EntityEquipment e=bot.getEquipment(); if(e!=null){e.setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));e.setHelmet(new ItemStack(Material.DIAMOND_HELMET));e.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));e.setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));e.setBoots(new ItemStack(Material.DIAMOND_BOOTS));} }
}
