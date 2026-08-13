# Testing

Automated command: `build.bat` (equivalent to `gradlew.bat clean build`). Startup QA: `scripts\setup-local.ps1 -AcceptEula`, then launch `java -Xms1G -Xmx2G -jar paper.jar --nogui`, wait for `Done`, issue `stop`, and inspect all of `local-server\logs\latest.log`. Automated startup does not prove client-side PvP quality.

RNG unit tests verify repeatable named streams, different fixed-seed sequences, isolation of motor streams and `DECISION_REACTION`/`AIM_REACTION`/`MOVEMENT_REACTION`, one-shot consumption of an administrative next-duel seed, and pending-seed removal on quit. `ReactionGate` tests cover deterministic schedules, zero and symmetric jitter, negative-result clamping, immediate initial readiness, 50 ms tick conversion, and the absence of jitter resampling while waiting. Domain tests verify smooth held aim execution and continuous held movement execution while newer opposite-direction perception is withheld until the corresponding gate opens. Profile tests cover legacy YAML and SQLite Custom payload migration plus new-only serialization. Perception tests continue to verify latency maturation before adaptation, one-time learning of a matured tick, rotation-invariant local forward/lateral projection, opposite lateral signs, degenerate geometry, local adaptive aim bias and the documented closing-speed sign.

`AttackIntent` tests cover delayed in-range/out-of-range planning without a live-target input, delayed LOS, actual-aim eligibility, decision/watchdog sources and watchdog count, current-world whiff/contact outcomes, exact attempt/swing/probe/attack ordering, single consumption, miss cadence reset, attempt-versus-confirmed-hit timing, event-deferred hit accounting and critical-whiff exclusion. The central scenario models a 2.7-block delayed target followed by a current sideways movement: one intent and swing, a current-ray whiff, no `attack(target)`, one miss and no retry. Full replay still requires the same profile and the same perception/input sequence; a human opponent supplies external state and therefore same-seed live duels need not be visually identical.

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
- [ ] Simulated ping independently delays information availability
- [ ] Decision, aim, and movement reaction controls independently change response feel without freezing aim/movement execution
- [ ] `/pvpbot debug` shows D/A/M configured reaction, plan age, and ticks until update
- [ ] Hit-select waits for cooldown/counters rather than attacking each tick
- [ ] Very high simulated ping produces visible, plausible stale-perception whiffs
- [ ] Lower aim accuracy produces more real misses rather than only delayed/suppressed attacks
- [ ] Sidestepping after the bot's delayed observation can produce one visible swing with no damage
- [ ] A miss never invokes melee damage; debug attempt/hit/miss/watchdog values remain consistent
- [ ] Successful current-ray contact still uses normal Minecraft armor damage and knockback
- [ ] Blocks obstruct the current attack ray, and another entity never counts as target contact
- [ ] Critical setting changes falling attacks
- [ ] W-tap stops sprint for a real tick; S-tap creates a short retreat after hits and misses
- [ ] Jump-reset affects pressure response and is mechanically useful on Paper 26.2
- [ ] Combo chase and escape work without teleporting
- [ ] Adaptation remains bounded but changes prediction after repeated movement
- [ ] `/pvpbot capabilities` reports block-hit unavailable
- [ ] `/pvpbot seed 12345` affects exactly the next duel, and `/pvpbot debug` shows `Seed: 12345`
- [ ] A following duel without another seed command shows a newly generated seed
- [ ] Set `/pvpbot seed 12345`, disconnect before a duel, reconnect, and confirm the seed was discarded

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
- [ ] Bot attempt increments on every AttackIntent swing; bot hit increments only after a damage event; bot miss is attempts minus hits
- [ ] A missed intended-critical swing does not increment successful bot criticals
- [ ] Combo definition is consistent: consecutive successful hits until opponent hits
- [ ] Longest combo, `/stats`, Statistics item and match history database row are correct

### Mandatory authenticated-client AttackIntent validation

Automated tests do not establish Citizens client rendering or live combat fidelity. Before accepting 1.0.9, use an authenticated Minecraft 26.2 client to verify all of the following:

- [ ] A whiff produces exactly one visible main-hand swing and no damage
- [ ] Paper ray tracing follows the Citizens NPC's current rendered view/crosshair
- [ ] Configured reach works at both 2.0 and 6.0 blocks
- [ ] A solid block between bot and target prevents contact
- [ ] Rapid sideways movement after delayed perception produces plausible stale whiffs
- [ ] A valid contact applies normal damage through unenchanted diamond armor
- [ ] A valid contact applies normal server knockback
- [ ] Falling-window critical behavior and successful critical statistics remain plausible
- [ ] W-tap and S-tap still execute after missed attempts
- [ ] One intent never produces a double swing or delayed retry
