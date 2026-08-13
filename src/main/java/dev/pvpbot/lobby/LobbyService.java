package dev.pvpbot.lobby;

import dev.pvpbot.gui.MenuService;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class LobbyService {
    private final Location spawn;
    private final NamespacedKey lobbyItemKey;
    public LobbyService(JavaPlugin plugin) {
        lobbyItemKey=new NamespacedKey(plugin,"lobby-control");
        String name=plugin.getConfig().getString("lobby.world","pvpbot_lobby"); int y=plugin.getConfig().getInt("lobby.y",100);
        World world=Bukkit.getWorld(name); if(world==null) world=new WorldCreator(name).type(WorldType.FLAT).generateStructures(false).createWorld();
        if(world==null) throw new IllegalStateException("Cannot create lobby world " + name); world.setGameRule(GameRules.SPAWN_MOBS,false); world.setGameRule(GameRules.ADVANCE_TIME,false); world.setTime(6000);
        spawn=new Location(world,.5,y+1,.5,0,0); generate(world,y); world.setSpawnLocation(spawn);
        plugin.getLogger().info("Lobby world ready: " + name);
    }
    private void generate(World w,int y) { for(int x=-14;x<=14;x++) for(int z=-14;z<=14;z++) { Material m=(Math.abs(x)%5==0||Math.abs(z)%5==0)?Material.LIGHT_GRAY_CONCRETE:Material.SMOOTH_QUARTZ; w.getBlockAt(x,y,z).setType(m,false); for(int dy=1;dy<=5;dy++) w.getBlockAt(x,y+dy,z).setType(Material.AIR,false); } }
    public Location spawn() { return spawn.clone(); }
    public void send(Player p) { p.teleport(spawn); p.setGameMode(GameMode.ADVENTURE); p.setHealth(20); p.setFoodLevel(20); p.setSaturation(20); p.setExp(0); p.setLevel(0); p.getInventory().clear(); p.getInventory().setArmorContents(null); p.getInventory().setItem(0,item(Material.DIAMOND_SWORD,"§bDuel Selector","duel")); p.getInventory().setItem(4,item(Material.COMPARATOR,"§eBot Settings","settings")); p.getInventory().setItem(8,item(Material.WRITTEN_BOOK,"§aStatistics","statistics")); }
    public boolean isInLobby(Player player) { return player.getWorld().equals(spawn.getWorld()); }
    public String control(ItemStack item) { if(item==null||!item.hasItemMeta())return null;return item.getItemMeta().getPersistentDataContainer().get(lobbyItemKey,PersistentDataType.STRING); }
    public boolean isLobbyItem(ItemStack item) { return control(item)!=null; }
    private ItemStack item(Material material,String name,String control) { ItemStack i=new ItemStack(material); ItemMeta m=i.getItemMeta(); m.setDisplayName(name); m.getPersistentDataContainer().set(lobbyItemKey,PersistentDataType.STRING,control); i.setItemMeta(m); return i; }
}
