package dev.pvpbot.duel.match;

import dev.pvpbot.arena.*;
import dev.pvpbot.bot.ai.BotBrain;
import dev.pvpbot.bot.entity.*;
import dev.pvpbot.bot.profile.BotProfile;
import dev.pvpbot.database.DatabaseService;
import dev.pvpbot.duel.KitDefinition;
import dev.pvpbot.lobby.LobbyService;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DuelMatch {
    private final UUID id=UUID.randomUUID(); private final JavaPluginFacade plugin; private final Player player; private final BotProfile profile; private final Arena arena; private final ArenaManager arenas; private final LobbyService lobby; private final CitizensBotFactory bots; private final KitDefinition kit; private final DatabaseService database; private final MatchStateMachine state=new MatchStateMachine(); private final MatchMetrics metrics=new MatchMetrics(); private final DuelHealth health=new DuelHealth(); private final AtomicBoolean cleaned=new AtomicBoolean();
    private BotHandle bot; private BotBrain brain; private BukkitTask tickTask; private long startedAt; private int activeTicks;
    public interface JavaPluginFacade { BukkitTask later(Runnable task,long delay); BukkitTask timer(Runnable task,long delay,long period); int countdownSeconds(); int finishDelay(); void info(String text); }
    public DuelMatch(JavaPluginFacade plugin,Player player,BotProfile profile,Arena arena,ArenaManager arenas,LobbyService lobby,CitizensBotFactory bots,KitDefinition kit,DatabaseService database){this.plugin=plugin;this.player=player;this.profile=profile;this.arena=arena;this.arenas=arenas;this.lobby=lobby;this.bots=bots;this.kit=kit;this.database=database;}
    public void start(){try{state.transition(MatchState.PREPARING);player.teleport(arena.playerSpawn());kit.apply(player);bot=bots.spawn(arena.botSpawn());brain=new BotBrain(bot,player,arena,profile,(connected,critical)->{metrics.botAttempts++;if(critical)metrics.botCrits++;});state.transition(MatchState.COUNTDOWN);countdown(plugin.countdownSeconds());}catch(RuntimeException e){plugin.info("Match "+id+" initialization failed: "+e.getMessage());cleanup();throw e;}}
    private void countdown(int seconds){if(seconds<=0){activate();return;}player.showTitle(net.kyori.adventure.title.Title.title(Component.text(seconds),Component.text("Get ready")));plugin.later(()->{if(state.state()==MatchState.COUNTDOWN)countdown(seconds-1);},20);}
    private void activate(){state.transition(MatchState.ACTIVE);startedAt=System.currentTimeMillis();player.showTitle(net.kyori.adventure.title.Title.title(Component.text("FIGHT!"),Component.empty()));tickTask=plugin.timer(()->{if(state.state()!=MatchState.ACTIVE)return;activeTicks++;if(!player.isOnline()){abort("player went offline");return;}if(bot==null){abort("Citizens NPC handle is missing");return;}if(!arena.contains(player.getLocation())){abort("player left arena bounds at "+format(player.getLocation()));return;}player.setFoodLevel(20);player.setSaturation(0);brain.tick(metrics.combo);},1,1);}
    private void abort(String reason){plugin.info("Match "+id+" aborted: "+reason);cleanup();}
    private static String format(Location location){return location.getWorld().getName()+" "+String.format(java.util.Locale.ROOT,"%.1f %.1f %.1f",location.getX(),location.getY(),location.getZ());}
    public void finish(boolean playerWon){if(state.state()!=MatchState.ACTIVE)return;state.transition(MatchState.FINISHING);long duration=System.currentTimeMillis()-startedAt;player.showTitle(net.kyori.adventure.title.Title.title(Component.text(playerWon?"VICTORY":"DEFEAT"),Component.text("PracticeBot · "+profile.name())));database.record(id,player.getUniqueId(),profile.name(),playerWon,duration,metrics.playerHits,metrics.botHits,metrics.playerMisses(),metrics.botMisses(),metrics.playerDamage,metrics.botDamage,metrics.playerCrits,metrics.botCrits,metrics.combo.longestPlayer(),metrics.combo.longestBot());plugin.later(this::cleanup,plugin.finishDelay());}
    public void cleanup(){if(!cleaned.compareAndSet(false,true))return;try{if(state.state()!=MatchState.CLEANUP&&state.state()!=MatchState.FINISHED)state.transition(MatchState.CLEANUP);if(tickTask!=null)tickTask.cancel();if(bot!=null)bot.destroy();arenas.release(arena);if(player.isOnline())lobby.send(player);state.transition(MatchState.FINISHED);}catch(RuntimeException e){plugin.info("Cleanup error for "+id+": "+e.getMessage());arenas.release(arena);if(bot!=null)try{bot.destroy();}catch(RuntimeException nested){plugin.info("NPC destruction also failed for "+id+": "+nested.getMessage());}}}
    public MatchState state(){return state.state();} public Player player(){return player;} public BotHandle bot(){return bot;} public Arena arena(){return arena;} public BotProfile profile(){return profile;} public MatchMetrics metrics(){return metrics;} public DuelHealth health(){return health;} public BotBrain brain(){return brain;} public UUID id(){return id;}
}
