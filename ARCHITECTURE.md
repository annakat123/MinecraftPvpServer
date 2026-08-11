# Architecture

`PvPBotPlugin` wires the services and owns their lifecycle. `LobbyService` and `ArenaManager` create dedicated flat worlds and four isolated 35×35 arenas. Reservations are memory-only and always released through `DuelMatch.cleanup()`.

## Match lifecycle

`CREATING → PREPARING → COUNTDOWN → ACTIVE → FINISHING → CLEANUP → FINISHED` is enforced by `MatchStateMachine`; invalid transitions throw. A player and arena have one match. Preparation teleports, applies equal Sword kits, spawns the NPC and freezes the player. A main-thread one-tick task runs active AI. Lethal final damage completes the duel; cleanup cancels the task, destroys the NPC, releases the arena, returns the player and restores lobby inventory. An atomic guard makes cleanup idempotent. Disconnect, unexpected teleport, missing NPC, arena exit, initialization exception, shutdown and plugin disable route to the same cleanup method. An active arena world unload is cancelled.

## Bot layer and AI

Citizens is isolated in `bot.entity`: it creates the player model and cached name-based skin. The combat system does not use Citizens navigation. `BotBrain` composes delayed perception, adaptation, hit selection, aim, critical and movement controllers. Movement uses bounded velocity steering suitable for the small flat arena. Detailed mechanics and parameters are in `BOT_AI.md`.

## Threading and persistence

All Bukkit entity/world/inventory operations run on the server thread. SQLite schema creation and occasional reads occur outside the tick loop; match/profile writes use one ordered background executor. WAL and prepared statements are used. Shutdown waits up to five seconds for queued writes.

Schema version 1 has `schema_version`, `player_stats`, `matches`, and `custom_profiles`. A miss is an intentional human arm-swing during ACTIVE with no corresponding successful damage event; bot attempts are explicit calls to the attack executor. Combo means consecutive successful attacks by one participant without a successful opposing attack between them.

## Extension points

The MVP has one `SwordKit`; the match owns a profile and kit, so a future `GameModeDefinition`/`KitDefinition` can select other inventories and controllers without changing lifecycle, arena allocation, persistence or cleanup. No empty NoDebuff controllers are included. Version-specific behavior is confined to Citizens rather than leaked through the project; no direct NMS is currently used.
