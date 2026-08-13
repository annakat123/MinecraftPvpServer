package dev.pvpbot;

import dev.pvpbot.arena.ArenaManager;
import dev.pvpbot.bot.entity.*;
import dev.pvpbot.bot.profile.ProfileRepository;
import dev.pvpbot.command.PvpBotCommand;
import dev.pvpbot.database.DatabaseService;
import dev.pvpbot.duel.SwordGameMode;
import dev.pvpbot.duel.kit.SwordKit;
import dev.pvpbot.duel.match.DuelManager;
import dev.pvpbot.gui.MenuService;
import dev.pvpbot.lobby.LobbyService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class PvPBotPlugin extends JavaPlugin implements Listener {
    private DatabaseService database; private DuelManager duels; private PvpBotCommand admin;
    @Override public void onEnable(){saveDefaultConfig();saveResourceIfMissing("arenas.yml");try{database=new DatabaseService(this);ProfileRepository profiles=new ProfileRepository(this);ArenaManager arenas=new ArenaManager(this);LobbyService lobby=new LobbyService(this);SwordKit kit=new SwordKit();CitizensBotFactory bots=new CitizensBotFactory(new ConfiguredSkinProvider(getConfig()),kit,getLogger());duels=new DuelManager(this,profiles,arenas,lobby,bots,new SwordGameMode(kit),database);MenuService menus=new MenuService(duels,database,lobby);Bukkit.getPluginManager().registerEvents(this,this);Bukkit.getPluginManager().registerEvents(duels,this);Bukkit.getPluginManager().registerEvents(menus,this);admin=new PvpBotCommand(this,duels,arenas);register("pvpbot",admin);PluginCommand stats=getCommand("stats");if(stats!=null)stats.setExecutor((sender,command,label,args)->{if(sender instanceof Player p)menus.openStats(p);else sender.sendMessage("Players only");return true;});PluginCommand botstats=getCommand("botstats");if(botstats!=null)botstats.setExecutor((sender,command,label,args)->{String profile=args.length==0?"NORMAL":args[0].toUpperCase(java.util.Locale.ROOT);var s=database.botStats(profile);sender.sendMessage("§b"+profile+" bot: wins="+s.wins()+", losses="+s.losses()+", hits="+s.hits()+", misses="+s.misses()+", damage="+String.format(java.util.Locale.ROOT,"%.1f",s.damage())+", crits="+s.crits()+", longest combo="+s.longestCombo());return true;});Bukkit.getScheduler().runTaskTimer(this,admin::debugTick,10,10);for(Player p:Bukkit.getOnlinePlayers())duels.joined(p);getLogger().info("PvPBot enabled: Paper 26.2 Sword MVP, commands registered, lobby and arenas ready");}catch(RuntimeException e){getLogger().log(java.util.logging.Level.SEVERE,"PvPBot startup failed",e);Bukkit.getPluginManager().disablePlugin(this);}}
    private void register(String name,PvpBotCommand executor){PluginCommand c=getCommand(name);if(c==null)throw new IllegalStateException("Command missing from plugin.yml: "+name);c.setExecutor(executor);c.setTabCompleter(executor);}
    private void saveResourceIfMissing(String name){if(!new java.io.File(getDataFolder(),name).exists())saveResource(name,false);}
    @EventHandler public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTask(this,()->duels.joined(e.getPlayer()));}
    @EventHandler public void respawn(PlayerRespawnEvent e){e.setRespawnLocation(getServer().getWorld(getConfig().getString("lobby.world","pvpbot_lobby")).getSpawnLocation());Bukkit.getScheduler().runTask(this,()->duels.joined(e.getPlayer()));}
    @EventHandler public void hunger(org.bukkit.event.entity.FoodLevelChangeEvent e){if(e.getEntity() instanceof Player p){e.setFoodLevel(20);p.setSaturation(20);}}
    @EventHandler(priority=EventPriority.LOWEST) public void lobbyDamage(EntityDamageEvent e){if(e.getEntity() instanceof Player p&&p.getWorld().getName().equals(getConfig().getString("lobby.world","pvpbot_lobby")))e.setCancelled(true);}
    @Override public void onDisable(){if(duels!=null)duels.cleanupAll();Bukkit.getScheduler().cancelTasks(this);if(database!=null)database.close();getLogger().info("PvPBot disabled; matches cleaned and database closed");}
}
