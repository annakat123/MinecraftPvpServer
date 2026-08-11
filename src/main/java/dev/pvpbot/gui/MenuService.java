package dev.pvpbot.gui;

import dev.pvpbot.bot.profile.BotProfile;
import dev.pvpbot.bot.profile.ProfileSchema;
import dev.pvpbot.bot.profile.ProfileRepository.Difficulty;
import dev.pvpbot.database.DatabaseService;
import dev.pvpbot.duel.match.DuelManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public final class MenuService implements Listener {
    private static final String DUEL="PvPBot · Sword", SETTINGS="PvPBot · Bot Settings", PREFIX="PvPBot · ", STATS="PvPBot · Statistics";
    private static final Map<String,List<String>> CATEGORIES=categories();
    private static final Map<String,List<String>> TOGGLES=Map.of(
            "Latency",List.of(), "Aim",List.of("aim"), "Combat",List.of("reach","hitSelect"),
            "Movement",List.of("strafe","spacing"), "Criticals",List.of("criticals"),
            "Sprint Reset",List.of("sprintReset","wTap","sTap","jumpReset"),
            "Combo",List.of("combo"), "Adaptation",List.of("adaptation"));
    private final DuelManager duels; private final DatabaseService database;
    public MenuService(DuelManager duels,DatabaseService database){this.duels=duels;this.database=database;}
    @EventHandler public void command(PlayerCommandPreprocessEvent e){if(e.getMessage().trim().equalsIgnoreCase("/pvpbot")){e.setCancelled(true);openDuel(e.getPlayer());}}
    private static Map<String,List<String>> categories(){Map<String,List<String>> m=new LinkedHashMap<>();m.put("Latency",List.of("simulatedPingMs","baseReactionMs","reactionJitterMs"));m.put("Aim",List.of("aim.accuracy","aim.predictionStrength","aim.maxYawSpeed","aim.maxPitchSpeed"));m.put("Combat",List.of("reach.blocks","hitSelect.skill","hitSelect.chance","hitSelect.patience","hitSelect.counterHitPreference","hitSelect.cooldownDiscipline","hitSelect.baitPreference"));m.put("Movement",List.of("strafe.skill","strafe.chance","strafe.intensity","spacing.skill","spacing.preferredDistance","spacing.forwardPressure"));m.put("Criticals",List.of("criticals.skill","criticals.chance"));m.put("Sprint Reset",List.of("sprintReset.skill","wTap.skill","wTap.chance","sTap.skill","sTap.chance","jumpReset.skill","jumpReset.chance"));m.put("Combo",List.of("combo.chaseSkill","combo.escapeSkill"));m.put("Adaptation",List.of("adaptation.strength"));return Collections.unmodifiableMap(m);}

    @EventHandler public void interact(PlayerInteractEvent e){if(e.getItem()==null||e.getItem().getItemMeta()==null)return;String n=e.getItem().getItemMeta().getDisplayName();if(n.equals("§bDuel Selector")){e.setCancelled(true);openDuel(e.getPlayer());}else if(n.equals("§eBot Settings")){e.setCancelled(true);openSettings(e.getPlayer(),0);}else if(n.equals("§aStatistics")){e.setCancelled(true);openStats(e.getPlayer());}}
    public void openDuel(Player p){Inventory i=Bukkit.createInventory(null,27,Component.text(DUEL));int slot=10;for(Difficulty d:Difficulty.values())i.setItem(slot++,item(d==duels.selected(p)?Material.LIME_WOOL:Material.GRAY_WOOL,"§f"+d,List.of("§7Click to select")));i.setItem(22,item(Material.DIAMOND_SWORD,"§bStart SWORD duel",List.of("§7One death · identical diamond kit")));p.openInventory(i);}
    public void openSettings(Player p,int ignoredPage){Inventory i=Bukkit.createInventory(null,45,Component.text(SETTINGS));int slot=10;for(var entry:CATEGORIES.entrySet()){i.setItem(slot++,item(Material.COMPARATOR,"§e"+entry.getKey(),List.of("§7"+entry.getValue().size()+" numeric controls","§7Click to configure")));if(slot==17)slot=19;}i.setItem(40,item(Material.EMERALD,"§aSave Custom",List.of("§7Persist all values by UUID")));i.setItem(44,item(Material.BARRIER,"§cReset",List.of("§7Reset to NORMAL defaults")));p.openInventory(i);}
    private void openCategory(Player p,String category){List<String> keys=CATEGORIES.get(category);if(keys==null)return;Inventory i=Bukkit.createInventory(null,54,Component.text(PREFIX+category));BotProfile profile=duels.custom(p);int slot=0;for(String key:keys){var s=ProfileSchema.PARAMETERS.get(key);i.setItem(slot++,item(Material.REPEATER,"§e"+key,List.of("§fCurrent: "+round(profile.value(key)),"§7Range: "+s.min()+" .. "+s.max(),"§aLeft +"+s.step()+" §cRight -"+s.step(),"§7Shift = x5")));}slot=36;for(String toggle:TOGGLES.getOrDefault(category,List.of()))i.setItem(slot++,item(profile.enabled(toggle)?Material.LIME_DYE:Material.GRAY_DYE,"§f"+toggle,List.of(profile.enabled(toggle)?"§aENABLED":"§cDISABLED","§7Click to toggle")));i.setItem(49,item(Material.ARROW,"§fBack",List.of("§7All categories")));p.openInventory(i);}
    public void openStats(Player p){DatabaseService.Stats s=database.stats(p.getUniqueId());Inventory i=Bukkit.createInventory(null,27,Component.text(STATS));i.setItem(13,item(Material.BOOK,"§aYour statistics",List.of("§fWins: "+s.wins(),"§fLosses: "+s.losses(),"§fHits / misses: "+s.hits()+" / "+s.misses(),"§fDamage: "+round(s.damage()),"§fCriticals: "+s.crits(),"§fLongest combo: "+s.longestCombo())));p.openInventory(i);}

    @EventHandler public void click(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p))return;String title=PlainTextComponentSerializer.plainText().serialize(e.getView().title());if(!title.startsWith("PvPBot"))return;e.setCancelled(true);int slot=e.getRawSlot();if(slot<0||slot>=e.getView().getTopInventory().getSize())return;if(title.equals(DUEL)){if(slot>=10&&slot<=14){duels.select(p,Difficulty.values()[slot-10]);openDuel(p);}else if(slot==22){p.closeInventory();duels.start(p);}return;}if(title.equals(SETTINGS)){if(slot==40){duels.custom(p,duels.custom(p),true);p.sendMessage("§aCustom profile saved.");return;}if(slot==44){duels.resetCustom(p);openSettings(p,0);return;}ItemStack clicked=e.getCurrentItem();if(clicked!=null&&clicked.getItemMeta()!=null){String category=ChatColor.stripColor(clicked.getItemMeta().getDisplayName());if(CATEGORIES.containsKey(category))openCategory(p,category);}return;}String category=title.substring(PREFIX.length());if(!CATEGORIES.containsKey(category))return;if(slot==49){openSettings(p,0);return;}List<String> keys=CATEGORIES.get(category);BotProfile profile=duels.custom(p);if(slot>=0&&slot<keys.size()){String key=keys.get(slot);var spec=ProfileSchema.PARAMETERS.get(key);double step=spec.step()*(e.isShiftClick()?5:1);duels.custom(p,profile.withValue(key,profile.value(key)+(e.isLeftClick()?step:-step)),false);openCategory(p,category);return;}List<String> toggles=TOGGLES.getOrDefault(category,List.of());if(slot>=36&&slot<36+toggles.size()){duels.custom(p,profile.toggle(toggles.get(slot-36)),false);openCategory(p,category);}}
    private ItemStack item(Material material,String name,List<String> lore){ItemStack i=new ItemStack(material);ItemMeta meta=i.getItemMeta();meta.setDisplayName(name);meta.setLore(lore);i.setItemMeta(meta);return i;}
    private static String round(double d){return String.format(Locale.ROOT,"%.2f",d);}
}
