package dev.pvpbot;

import dev.pvpbot.arena.ArenaManager;
import dev.pvpbot.bot.entity.CitizensBotFactory;
import dev.pvpbot.bot.entity.ConfiguredSkinProvider;
import dev.pvpbot.bot.profile.ProfileRepository;
import dev.pvpbot.bot.trace.CombatTraceService;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PvPBotPlugin extends JavaPlugin implements Listener {
    private DatabaseService database;
    private DuelManager duels;
    private PvpBotCommand admin;
    private CombatTraceService traces;

    @Override public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("arenas.yml");
        try {
            database = new DatabaseService(this);
            traces = new CombatTraceService(getDataFolder().toPath(), getLogger());
            ProfileRepository profiles = new ProfileRepository(this);
            ArenaManager arenas = new ArenaManager(this);
            LobbyService lobby = new LobbyService(this);
            SwordKit kit = new SwordKit();
            CitizensBotFactory bots = new CitizensBotFactory(
                    new ConfiguredSkinProvider(getConfig()), kit, getLogger());
            duels = new DuelManager(this, profiles, arenas, lobby, bots,
                    new SwordGameMode(kit), database, traces);
            MenuService menus = new MenuService(duels, database, lobby);
            Bukkit.getPluginManager().registerEvents(this, this);
            Bukkit.getPluginManager().registerEvents(duels, this);
            Bukkit.getPluginManager().registerEvents(menus, this);
            admin = new PvpBotCommand(this, duels, arenas);
            register("pvpbot", admin);
            PluginCommand stats = getCommand("stats");
            if (stats != null) stats.setExecutor((sender, command, label, args) -> {
                if (sender instanceof Player player) menus.openStats(player);
                else sender.sendMessage("Players only");
                return true;
            });
            PluginCommand botstats = getCommand("botstats");
            if (botstats != null) botstats.setExecutor((sender, command, label, args) -> {
                String profile = args.length == 0 ? "NORMAL" : args[0].toUpperCase(java.util.Locale.ROOT);
                var summary = database.botStats(profile);
                sender.sendMessage("§b" + profile + " bot: wins=" + summary.wins()
                        + ", losses=" + summary.losses() + ", hits=" + summary.hits()
                        + ", misses=" + summary.misses() + ", damage="
                        + String.format(java.util.Locale.ROOT, "%.1f", summary.damage())
                        + ", crits=" + summary.crits() + ", longest combo=" + summary.longestCombo());
                return true;
            });
            Bukkit.getScheduler().runTaskTimer(this, admin::debugTick, 10, 10);
            for (Player player : Bukkit.getOnlinePlayers()) duels.joined(player);
            getLogger().info("PvPBot enabled: Paper 26.2 Sword MVP, commands registered, lobby and arenas ready");
        } catch (RuntimeException error) {
            getLogger().log(java.util.logging.Level.SEVERE, "PvPBot startup failed", error);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private void register(String name, PvpBotCommand executor) {
        PluginCommand command = getCommand(name);
        if (command == null) throw new IllegalStateException("Command missing from plugin.yml: " + name);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void saveResourceIfMissing(String name) {
        if (!new java.io.File(getDataFolder(), name).exists()) saveResource(name, false);
    }

    @EventHandler public void join(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(this, () -> duels.joined(event.getPlayer()));
    }

    @EventHandler public void respawn(PlayerRespawnEvent event) {
        event.setRespawnLocation(getServer().getWorld(
                getConfig().getString("lobby.world", "pvpbot_lobby")).getSpawnLocation());
        Bukkit.getScheduler().runTask(this, () -> duels.joined(event.getPlayer()));
    }

    @EventHandler public void hunger(org.bukkit.event.entity.FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            event.setFoodLevel(20);
            player.setSaturation(20);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void lobbyDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && player.getWorld().getName().equals(
                getConfig().getString("lobby.world", "pvpbot_lobby"))) event.setCancelled(true);
    }

    @Override public void onDisable() {
        if (duels != null) duels.cleanupAll();
        Bukkit.getScheduler().cancelTasks(this);
        if (traces != null) traces.close();
        if (database != null) database.close();
        getLogger().info("PvPBot disabled; matches cleaned, traces flushed and database closed");
    }
}
