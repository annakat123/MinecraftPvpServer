# Testing

Automated command: `build.bat` (equivalent to `gradlew.bat clean build`). Startup QA: `scripts\setup-local.ps1 -AcceptEula`, then launch `java -Xms1G -Xmx2G -jar paper.jar --nogui`, wait for `Done`, issue `stop`, and inspect all of `local-server\logs\latest.log`. Automated startup does not prove client-side PvP quality.

## Manual Minecraft 26.2 checklist

### Join

- [ ] Join `localhost` using an authenticated Minecraft 26.2 account
- [ ] Appear in the generated lobby with correct hotbar items
- [ ] Hunger remains full and does not interfere

### GUI and Custom profile

- [ ] Duel Selector, Bot Settings and Statistics open
- [ ] Lobby controls cannot be dropped, moved, shift-clicked, number-key swapped, dragged, transferred to a container or moved to the offhand
- [ ] Normal inventory interaction remains available outside the PvPBot lobby; the Sword duel inventory is unaffected
- [ ] EASY/NORMAL/HARD/EXPERT/CUSTOM selection changes
- [ ] Every numeric setting changes with left/right and Shift clicks and remains within range
- [ ] Every technique enabled flag toggles
- [ ] Save, reconnect, and confirm Custom profile reloads; Reset restores NORMAL defaults

### Sword start

- [ ] Sword starts duel and arena teleport works
- [ ] `PracticeBot` player model spawns with a visible configured/fallback skin
- [ ] Both sides have unenchanted diamond sword/armor and 20 HP
- [ ] Three-second countdown freezes movement and blocks early damage
- [ ] Fight begins simultaneously

### Bot behavior

- [ ] Bot approaches/holds/retreats naturally and changes strafe direction
- [ ] Aim rotates smoothly and visibly differs between EASY and EXPERT
- [ ] Reach 2.0 versus 6.0 changes effective attack eligibility
- [ ] Simulated ping and reaction independently change response feel
- [ ] Hit-select waits for cooldown/counters rather than attacking each tick
- [ ] Critical setting changes falling attacks
- [ ] W-tap stops sprint for a real tick; S-tap creates a short retreat
- [ ] Jump-reset affects pressure response and is mechanically useful on Paper 26.2
- [ ] Combo chase and escape work without teleporting
- [ ] Adaptation remains bounded but changes prediction after repeated movement
- [ ] `/pvpbot capabilities` reports block-hit unavailable

### Ending and cleanup

- [ ] Lethal player damage shows DEFEAT; lethal bot damage shows VICTORY
- [ ] Title remains about 2.5 seconds, then bot despawns
- [ ] Player returns to lobby with lobby inventory and arena becomes available
- [ ] A new duel can start immediately after Victory/Defeat cleanup without a stale-match warning
- [ ] Disconnect/reconnect during countdown and ACTIVE leaves no NPC, reservation, freeze or task
- [ ] `stop` during a fight cleans NPC/task and closes SQLite without errors
- [ ] Unexpected teleport/arena exit cleans the match

### Statistics

- [ ] Match, win/loss, actual successful hits, damage and criticals update
- [ ] Miss means an ACTIVE intentional arm swing without a successful damage event
- [ ] Combo definition is consistent: consecutive successful hits until opponent hits
- [ ] Longest combo, `/stats`, Statistics item and match history database row are correct
