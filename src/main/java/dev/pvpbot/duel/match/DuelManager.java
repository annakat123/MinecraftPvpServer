package dev.pvpbot.duel.match;

import dev.pvpbot.arena.Arena;
import dev.pvpbot.arena.ArenaManager;
import dev.pvpbot.bot.entity.CitizensBotFactory;
import dev.pvpbot.bot.profile.BotProfile;
import dev.pvpbot.bot.profile.ProfileRepository;
import dev.pvpbot.bot.profile.ProfileRepository.Difficulty;
import dev.pvpbot.bot.trace.CombatTraceService;
import dev.pvpbot.database.DatabaseService;
import dev.pvpbot.duel.GameModeDefinition;
import dev.pvpbot.lobby.LobbyService;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DuelManager implements Listener {
    private final JavaPlugin plugin;
    private final ProfileRepository profiles;
    private final ArenaManager arenas;
    private final LobbyService lobby;
    private final CitizensBotFactory bots;
    private final GameModeDefinition mode;
    private final DatabaseService database;
    private final CombatTraceService traces;
    private final Map<UUID, DuelMatch> matches = new HashMap<>();
    private final Map<UUID, Difficulty> selected = new HashMap<>();
    private final Map<UUID, BotProfile> custom = new HashMap<>();
    private final NextDuelSeedStore nextSeeds = new NextDuelSeedStore();
    private final SecureRandom seedGenerator = new SecureRandom();

    public DuelManager(JavaPlugin plugin, ProfileRepository profiles, ArenaManager arenas,
                       LobbyService lobby, CitizensBotFactory bots, GameModeDefinition mode,
                       DatabaseService database, CombatTraceService traces) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.arenas = arenas;
        this.lobby = lobby;
        this.bots = bots;
        this.mode = mode;
        this.database = database;
        this.traces = traces;
    }

    public void joined(Player player) {
        selected.putIfAbsent(player.getUniqueId(), Difficulty.NORMAL);
        custom.put(player.getUniqueId(), database.loadProfile(player.getUniqueId()).orElseGet(this::customDefault));
        lobby.send(player);
    }

    public void select(Player player, Difficulty difficulty) {
        selected.put(player.getUniqueId(), difficulty);
        player.sendMessage("§aBot difficulty: §f" + difficulty);
    }

    public Difficulty selected(Player player) {
        return selected.getOrDefault(player.getUniqueId(), Difficulty.NORMAL);
    }

    public BotProfile selectedProfile(Player player) {
        Difficulty difficulty = selected(player);
        return difficulty == Difficulty.CUSTOM
                ? custom.getOrDefault(player.getUniqueId(), customDefault())
                : profiles.get(difficulty);
    }

    public BotProfile custom(Player player) {
        return custom.getOrDefault(player.getUniqueId(), customDefault());
    }

    public void custom(Player player, BotProfile profile, boolean save) {
        custom.put(player.getUniqueId(), profile);
        selected.put(player.getUniqueId(), Difficulty.CUSTOM);
        if (save) database.saveProfile(player.getUniqueId(), profile);
    }

    public void resetCustom(Player player) { custom(player, customDefault(), true); }

    private BotProfile customDefault() {
        BotProfile normal = profiles.get(Difficulty.NORMAL);
        return new BotProfile("CUSTOM", normal.values(), normal.toggles());
    }

    public void start(Player player) {
        UUID playerId = player.getUniqueId();
        if (matches.containsKey(playerId)) {
            player.sendMessage("§cYou are already in a duel.");
            return;
        }
        Optional<Arena> allocated = arenas.allocate();
        if (allocated.isEmpty()) {
            player.sendMessage("§cAll arenas are busy.");
            return;
        }
        long seed = nextSeeds.consume(playerId).orElseGet(seedGenerator::nextLong);
        DuelMatch match = new DuelMatch(facade(), player, selectedProfile(player), allocated.get(),
                arenas, lobby, bots, mode.kit(), database, seed,
                closed -> matches.remove(playerId, closed));
        matches.put(playerId, match);
        try {
            match.start();
            if (traces.consume(playerId)) enableTrace(match);
        } catch (RuntimeException error) {
            matches.remove(playerId, match);
            player.sendMessage("§cDuel could not start. See server log.");
        }
    }

    public void setNextSeed(Player player, long seed) { nextSeeds.set(player.getUniqueId(), seed); }

    private DuelMatch.JavaPluginFacade facade() {
        return new DuelMatch.JavaPluginFacade() {
            public org.bukkit.scheduler.BukkitTask later(Runnable task, long delay) {
                return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
            }
            public org.bukkit.scheduler.BukkitTask timer(Runnable task, long delay, long period) {
                return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
            }
            public int countdownSeconds() { return plugin.getConfig().getInt("countdown-seconds", 3); }
            public int finishDelay() { return plugin.getConfig().getInt("finish-delay-ticks", 50); }
            public void info(String text) { plugin.getLogger().warning(text); }
            public void error(String text, RuntimeException error) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, text, error);
            }
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        DuelMatch match = findByEntity(event.getEntity().getUniqueId());
        if (match == null) match = findByEntity(event.getDamager().getUniqueId());
        if (match == null) return;
        if (match.state() != MatchState.ACTIVE) {
            event.setCancelled(true);
            return;
        }
        boolean playerAttacks = event.getDamager().getUniqueId().equals(match.player().getUniqueId())
                && match.bot() != null
                && event.getEntity().getUniqueId().equals(match.bot().entity().getUniqueId());
        boolean botAttacks = match.bot() != null
                && event.getDamager().getUniqueId().equals(match.bot().entity().getUniqueId())
                && event.getEntity().getUniqueId().equals(match.player().getUniqueId());
        if (!playerAttacks && !botAttacks) {
            event.setCancelled(true);
            return;
        }
        double damage = event.getFinalDamage();
        if (playerAttacks) {
            match.metrics().playerHits++;
            match.metrics().playerDamage += damage;
            match.metrics().combo.playerHit(Bukkit.getCurrentTick());
            if (match.brain() != null) match.brain().incomingHit();
            if (match.player().getFallDistance() > 0 && !match.player().isOnGround()) match.metrics().playerCrits++;
            if (match.health().damageBot(damage)) {
                event.setCancelled(true);
                match.finish(true);
            }
        } else {
            match.metrics().botHits++;
            match.metrics().botDamage += damage;
            match.metrics().combo.botHit(Bukkit.getCurrentTick());
            if (match.brain() != null && match.brain().outgoingHit()) match.metrics().botCrits++;
            if (match.health().damagePlayer(damage)) {
                event.setCancelled(true);
                match.player().setHealth(20);
                match.finish(false);
            }
        }
    }

    @EventHandler public void swing(PlayerAnimationEvent event) {
        DuelMatch match = matches.get(event.getPlayer().getUniqueId());
        if (match != null && match.state() == MatchState.ACTIVE
                && event.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            match.metrics().playerAttempts++;
        }
    }

    /** Paper fires confirmed damage before its melee knockback event in the same hurtServer call. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void knockback(EntityKnockbackEvent event) {
        DuelMatch match = findByEntity(event.getEntity().getUniqueId());
        if (match == null || match.state() != MatchState.ACTIVE || match.bot() == null || match.brain() == null) return;
        Player bot = match.bot().entity();
        if (bot == null || !event.getEntity().getUniqueId().equals(bot.getUniqueId())) return;
        org.bukkit.util.Vector impulse = event.getKnockback();
        if (Math.hypot(impulse.getX(), impulse.getZ()) > 1.0E-9) {
            match.brain().incomingKnockback(event.getCause(), impulse);
        }
    }

    @EventHandler public void regain(EntityRegainHealthEvent event) {
        DuelMatch match = findByEntity(event.getEntity().getUniqueId());
        if (match != null && match.state() == MatchState.ACTIVE) event.setCancelled(true);
    }

    @EventHandler public void move(PlayerMoveEvent event) {
        DuelMatch match = matches.get(event.getPlayer().getUniqueId());
        if (match != null && (match.state() == MatchState.COUNTDOWN || match.state() == MatchState.PREPARING)
                && event.hasChangedPosition()) event.setTo(event.getFrom());
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cleanup(player);
        traces.disarm(player.getUniqueId());
        nextSeeds.remove(player.getUniqueId());
        selected.remove(player.getUniqueId());
        custom.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        DuelMatch match = matches.get(event.getPlayer().getUniqueId());
        if (match != null && match.state() == MatchState.ACTIVE
                && event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN
                && event.getTo() != null && !match.arena().contains(event.getTo())) cleanup(event.getPlayer());
    }

    @EventHandler public void unload(WorldUnloadEvent event) {
        boolean used = matches.values().stream().anyMatch(match ->
                match.arena().center().getWorld() == event.getWorld() && match.state() != MatchState.FINISHED);
        if (used) event.setCancelled(true);
    }

    private DuelMatch findByEntity(UUID id) {
        for (DuelMatch match : matches.values()) {
            if (match.player().getUniqueId().equals(id)
                    || (match.bot() != null && match.bot().entity().getUniqueId().equals(id))) return match;
        }
        return null;
    }

    public void cleanup(Player player) {
        DuelMatch match = matches.get(player.getUniqueId());
        if (match != null) match.cleanup();
    }

    public void cleanupAll() {
        for (DuelMatch match : new ArrayList<>(matches.values())) match.cleanup();
        matches.clear();
        arenas.releaseAll();
    }

    public Optional<DuelMatch> match(Player player) { return Optional.ofNullable(matches.get(player.getUniqueId())); }

    public String traceOn(Player player) {
        DuelMatch match = matches.get(player.getUniqueId());
        if (match != null) {
            if (!match.tracing()) enableTrace(match);
            return "Tracing current duel: " + match.tracePath();
        }
        traces.arm(player.getUniqueId());
        return "Tracing armed for the next duel (one use).";
    }

    public String traceOff(Player player) {
        traces.disarm(player.getUniqueId());
        DuelMatch match = matches.get(player.getUniqueId());
        if (match != null && match.tracing()) {
            match.disableTrace();
            return "Current duel trace stopped.";
        }
        return "Pending trace disabled.";
    }

    public String traceStatus(Player player) {
        DuelMatch match = matches.get(player.getUniqueId());
        if (match != null && match.tracing()) {
            return "ON current duel: " + match.tracePath() + "; queue="
                    + traces.queuedEvents() + "/" + traces.queueCapacity();
        }
        return traces.armed(player.getUniqueId()) ? "ARMED for next duel (one use)." : "OFF";
    }

    private void enableTrace(DuelMatch match) {
        match.enableTrace(traces.open(match.id(), match.seed(), match.profile()),
                plugin.getDescription().getVersion());
    }
}
