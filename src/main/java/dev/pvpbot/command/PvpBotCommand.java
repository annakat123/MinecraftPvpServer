package dev.pvpbot.command;

import dev.pvpbot.arena.ArenaManager;
import dev.pvpbot.bot.profile.ProfileRepository.Difficulty;
import dev.pvpbot.duel.match.DuelManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class PvpBotCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final DuelManager duels;
    private final ArenaManager arenas;
    private final Set<UUID> debug = new HashSet<>();

    public PvpBotCommand(JavaPlugin plugin, DuelManager duels, ArenaManager arenas) {
        this.plugin = plugin;
        this.duels = duels;
        this.arenas = arenas;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("\u00A7b/pvpbot reload|debug [player]|seed <long>|profile <name>|arena info|capabilities");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage("\u00A7aCore config reloaded. Profile YAML reload requires restart.");
            }
            case "capabilities" -> {
                sender.sendMessage("\u00A7bSword 26.2 capabilities: \u00A7aAIM, REACH, CRITICALS, STRAFE, SPACING, W_TAP, S_TAP, SPRINT_RESET, JUMP_RESET, COMBO, ADAPTATION = SUPPORTED");
                sender.sendMessage("\u00A77BLOCK_HIT = UNSUPPORTED_BY_VERSION / UNSUPPORTED_BY_CURRENT_KIT");
            }
            case "arena" -> sender.sendMessage("\u00A7bArenas: " + arenas.reservedCount() + " reserved / " + arenas.totalCount() + " total");
            case "profile" -> {
                if (!(sender instanceof Player player) || args.length < 2) return false;
                try {
                    duels.select(player, Difficulty.valueOf(args[1].toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException error) {
                    sender.sendMessage("\u00A7cUse EASY, NORMAL, HARD, EXPERT or CUSTOM");
                }
            }
            case "seed" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("\u00A7cPlayers only");
                    return true;
                }
                if (args.length < 2) return false;
                try {
                    long seed = Long.parseLong(args[1]);
                    duels.setNextSeed(player, seed);
                    sender.sendMessage("\u00A7aNext duel seed: \u00A7f" + seed + "\u00A7a (one use)");
                } catch (NumberFormatException error) {
                    sender.sendMessage("\u00A7cSeed must be a signed 64-bit integer.");
                }
            }
            case "debug" -> {
                Player target = args.length > 1 ? plugin.getServer().getPlayer(args[1])
                        : sender instanceof Player player ? player : null;
                if (target == null) {
                    sender.sendMessage("\u00A7cPlayer not found");
                    return true;
                }
                if (!debug.add(target.getUniqueId())) debug.remove(target.getUniqueId());
                sender.sendMessage("\u00A7aDebug for " + target.getName() + ": " + debug.contains(target.getUniqueId()));
                duels.match(target).ifPresent(match -> sender.sendMessage("\u00A77Seed: \u00A7f" + match.seed()));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    public void debugTick() {
        for (UUID id : new HashSet<>(debug)) {
            Player player = plugin.getServer().getPlayer(id);
            if (player == null) continue;
            duels.match(player).ifPresent(match -> {
                if (match.brain() == null) {
                    player.sendActionBar("\u00A7e" + match.id() + " \u00A7f" + match.state()
                            + " \u00A77arena=" + match.arena().id() + " Seed: " + match.seed());
                    return;
                }
                var brain = match.brain();
                var profile = match.profile();
                var intent = brain.lastIntent();
                var bot = match.bot();
                var entity = bot == null ? null : bot.entity();
                String last = intent == null ? "-" : intent.source() + "/"
                        + (brain.lastExecutionResult() == null ? "-" : brain.lastExecutionResult().name())
                        + "@" + intent.perceptionTick() + "(" + brain.lastIntentPerceptionAgeTicks() + "t)";
                String npcEntity = entity == null ? "-"
                        : entity.getUniqueId().toString().substring(0, 8) + "#" + bot.entityRevision();
                player.sendActionBar(String.format(Locale.ROOT,
                        "\u00A7e%s %s \u00A7f%s \u00A77d=%.2f r=%.1f cd=%.2f ai=%s p=%dt "
                                + "D=%d±%d:%d/%d A=%d±%d:%d/%d M=%d±%d:%d/%d "
                                + "atk=%d/%d/%d wd=%d last=%s str=%d sp=%s adapt=%.2f combo=%d/%d "
                                + "vertical=%s kbLock=%d npcEntity=%s npcSpawned=%s arena=%d Seed:%d",
                        match.id().toString().substring(0, 8), match.state(), profile.name(), brain.distance(),
                        profile.value("reach.blocks"), brain.cooldown(), brain.decision(), brain.perceptionAgeTicks(),
                        profile.millis("reaction.decisionMs"), profile.millis("reaction.decisionJitterMs"),
                        brain.decisionPlanAgeTicks(), brain.decisionTicksUntilUpdate(),
                        profile.millis("reaction.aimMs"), profile.millis("reaction.aimJitterMs"),
                        brain.aimPlanAgeTicks(), brain.aimTicksUntilUpdate(),
                        profile.millis("reaction.movementMs"), profile.millis("reaction.movementJitterMs"),
                        brain.movementPlanAgeTicks(), brain.movementTicksUntilUpdate(),
                        match.metrics().botAttempts, match.metrics().botHits, match.metrics().botMisses(),
                        brain.watchdogIntentCount(), last, brain.strafeDirection(), brain.sprinting(),
                        brain.adaptationConfidence(), match.metrics().combo.playerCombo(), match.metrics().combo.botCombo(),
                        brain.verticalAction().debugName(), brain.knockbackLockTicksRemaining(), npcEntity,
                        bot != null && bot.isSpawned(), match.arena().id(), match.seed()));
            });
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("help", "reload", "debug", "seed", "profile", "arena", "capabilities");
        if (args.length == 2 && args[0].equalsIgnoreCase("profile")) {
            return Arrays.stream(Difficulty.values()).map(Enum::name).toList();
        }
        return List.of();
    }
}
