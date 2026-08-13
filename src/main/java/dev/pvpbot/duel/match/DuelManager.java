package dev.pvpbot.duel.match;

import dev.pvpbot.arena.*;
import dev.pvpbot.bot.entity.CitizensBotFactory;
import dev.pvpbot.bot.profile.*;
import dev.pvpbot.bot.profile.ProfileRepository.Difficulty;
import dev.pvpbot.database.DatabaseService;
import dev.pvpbot.duel.GameModeDefinition;
import dev.pvpbot.lobby.LobbyService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.security.SecureRandom;
import java.util.*;

public final class DuelManager implements Listener {
    private final JavaPlugin plugin; private final ProfileRepository profiles; private final ArenaManager arenas; private final LobbyService lobby; private final CitizensBotFactory bots; private final GameModeDefinition mode; private final DatabaseService database;
    private final Map<UUID,DuelMatch> matches=new HashMap<>(); private final Map<UUID,Difficulty> selected=new HashMap<>(); private final Map<UUID,BotProfile> custom=new HashMap<>(); private final NextDuelSeedStore nextSeeds=new NextDuelSeedStore(); private final SecureRandom seedGenerator=new SecureRandom();
    public DuelManager(JavaPlugin plugin,ProfileRepository profiles,ArenaManager arenas,LobbyService lobby,CitizensBotFactory bots,GameModeDefinition mode,DatabaseService database){this.plugin=plugin;this.profiles=profiles;this.arenas=arenas;this.lobby=lobby;this.bots=bots;this.mode=mode;this.database=database;}
    public void joined(Player p){selected.putIfAbsent(p.getUniqueId(),Difficulty.NORMAL);custom.put(p.getUniqueId(),database.loadProfile(p.getUniqueId()).orElseGet(this::customDefault));lobby.send(p);}
    public void select(Player p,Difficulty d){selected.put(p.getUniqueId(),d);p.sendMessage("§aBot difficulty: §f"+d);}
    public Difficulty selected(Player p){return selected.getOrDefault(p.getUniqueId(),Difficulty.NORMAL);}
    public BotProfile selectedProfile(Player p){Difficulty d=selected(p);return d==Difficulty.CUSTOM?custom.getOrDefault(p.getUniqueId(),customDefault()):profiles.get(d);}
    public BotProfile custom(Player p){return custom.getOrDefault(p.getUniqueId(),customDefault());}
    public void custom(Player p,BotProfile profile,boolean save){custom.put(p.getUniqueId(),profile);selected.put(p.getUniqueId(),Difficulty.CUSTOM);if(save)database.saveProfile(p.getUniqueId(),profile);}
    public void resetCustom(Player p){custom(p,customDefault(),true);}
    private BotProfile customDefault(){BotProfile normal=profiles.get(Difficulty.NORMAL);return new BotProfile("CUSTOM",normal.values(),normal.toggles());}
    public void start(Player p){UUID playerId=p.getUniqueId();if(matches.containsKey(playerId)){p.sendMessage("§cYou are already in a duel.");return;}Optional<Arena> allocated=arenas.allocate();if(allocated.isEmpty()){p.sendMessage("§cAll arenas are busy.");return;}long seed=nextSeeds.consume(playerId).orElseGet(seedGenerator::nextLong);DuelMatch match=new DuelMatch(facade(),p,selectedProfile(p),allocated.get(),arenas,lobby,bots,mode.kit(),database,seed,closed->matches.remove(playerId,closed));matches.put(playerId,match);try{match.start();}catch(RuntimeException e){matches.remove(playerId,match);p.sendMessage("§cDuel could not start. See server log.");}}
    public void setNextSeed(Player p,long seed){nextSeeds.set(p.getUniqueId(),seed);}
    private DuelMatch.JavaPluginFacade facade(){return new DuelMatch.JavaPluginFacade(){public org.bukkit.scheduler.BukkitTask later(Runnable r,long d){return Bukkit.getScheduler().runTaskLater(plugin,r,d);}public org.bukkit.scheduler.BukkitTask timer(Runnable r,long d,long p){return Bukkit.getScheduler().runTaskTimer(plugin,r,d,p);}public int countdownSeconds(){return plugin.getConfig().getInt("countdown-seconds",3);}public int finishDelay(){return plugin.getConfig().getInt("finish-delay-ticks",50);}public void info(String s){plugin.getLogger().warning(s);}public void error(String s,RuntimeException e){plugin.getLogger().log(java.util.logging.Level.SEVERE,s,e);}};}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageByEntityEvent e){DuelMatch m=findByEntity(e.getEntity().getUniqueId());if(m==null)m=findByEntity(e.getDamager().getUniqueId());if(m==null)return;if(m.state()!=MatchState.ACTIVE){e.setCancelled(true);return;}boolean playerAttacks=e.getDamager().getUniqueId().equals(m.player().getUniqueId())&&m.bot()!=null&&e.getEntity().getUniqueId().equals(m.bot().entity().getUniqueId());boolean botAttacks=m.bot()!=null&&e.getDamager().getUniqueId().equals(m.bot().entity().getUniqueId())&&e.getEntity().getUniqueId().equals(m.player().getUniqueId());if(!playerAttacks&&!botAttacks){e.setCancelled(true);return;}double damage=e.getFinalDamage();if(playerAttacks){m.metrics().playerHits++;m.metrics().playerDamage+=damage;m.metrics().combo.playerHit(Bukkit.getCurrentTick());if(m.brain()!=null)m.brain().incomingHit();if(m.player().getFallDistance()>0&&!m.player().isOnGround()){m.metrics().playerCrits++;}if(m.health().damageBot(damage)){e.setCancelled(true);m.finish(true);}}else{m.metrics().botHits++;m.metrics().botDamage+=damage;m.metrics().combo.botHit(Bukkit.getCurrentTick());if(m.brain()!=null&&m.brain().outgoingHit())m.metrics().botCrits++;if(m.health().damagePlayer(damage)){e.setCancelled(true);m.player().setHealth(20);m.finish(false);}}}
    @EventHandler public void swing(PlayerAnimationEvent e){DuelMatch m=matches.get(e.getPlayer().getUniqueId());if(m!=null&&m.state()==MatchState.ACTIVE&&e.getAnimationType()==PlayerAnimationType.ARM_SWING)m.metrics().playerAttempts++;}
    @EventHandler public void regain(EntityRegainHealthEvent e){DuelMatch m=findByEntity(e.getEntity().getUniqueId());if(m!=null&&m.state()==MatchState.ACTIVE)e.setCancelled(true);}
    @EventHandler public void move(PlayerMoveEvent e){DuelMatch m=matches.get(e.getPlayer().getUniqueId());if(m!=null&&(m.state()==MatchState.COUNTDOWN||m.state()==MatchState.PREPARING)&&e.hasChangedPosition()){e.setTo(e.getFrom());}}
    @EventHandler public void quit(PlayerQuitEvent e){Player p=e.getPlayer();cleanup(p);nextSeeds.remove(p.getUniqueId());selected.remove(p.getUniqueId());custom.remove(p.getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){DuelMatch m=matches.get(e.getPlayer().getUniqueId());if(m!=null&&m.state()==MatchState.ACTIVE&&e.getCause()!=PlayerTeleportEvent.TeleportCause.PLUGIN&&e.getTo()!=null&&!m.arena().contains(e.getTo()))cleanup(e.getPlayer());}
    @EventHandler public void unload(WorldUnloadEvent e){boolean used=matches.values().stream().anyMatch(m->m.arena().center().getWorld()==e.getWorld()&&m.state()!=MatchState.FINISHED);if(used)e.setCancelled(true);}
    private DuelMatch findByEntity(UUID id){for(DuelMatch m:matches.values())if(m.player().getUniqueId().equals(id)||(m.bot()!=null&&m.bot().entity().getUniqueId().equals(id)))return m;return null;}
    public void cleanup(Player p){DuelMatch m=matches.get(p.getUniqueId());if(m!=null)m.cleanup();}
    public void cleanupAll(){for(DuelMatch m:new ArrayList<>(matches.values()))m.cleanup();matches.clear();arenas.releaseAll();}
    public Optional<DuelMatch> match(Player p){return Optional.ofNullable(matches.get(p.getUniqueId()));}
}
