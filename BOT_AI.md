# Bot AI

The 1.0.9 runtime pipeline is world state → one immutable `PerceptionSnapshot` → one logical simulated-latency buffer → latest matured/perceived snapshot. A new matured snapshot is learned by adaptation exactly once. Three independent scheduled gates then control decision, aim planning, and movement planning; held state is executed between gate openings. The implementation never sleeps, delays packets, or creates one scheduler task per reaction.

Simulated ping and reaction are independent. `simulatedPingMs` controls when captured information becomes available to the bot. Reaction controls how long the bot takes to change actionable state after that information is available. Reaction never re-enters the latency buffer and therefore cannot create a second perception delay.

`ReactionGate` runs on the `BotBrain` tick timeline, not wall-clock time. After a channel update, it draws one uniformly distributed integer offset from `[-jitterMs, +jitterMs]`, computes `max(0, baseMs + offset)`, converts that final interval with `max(1, ceil(intervalMs / 50))`, and schedules one `nextUpdateTick`. It does not redraw while waiting. Paper runs at nominal 20 TPS, approximately 50 ms per tick, so 0–50 ms all resolve to the next available tick; there is no sub-tick execution. The first matured perception may update all three initially-ready gates immediately, after which normal schedules begin. Simulated latency is therefore still honored at startup.

`PerceptionSnapshot` owns cloned target body/eye locations and velocity together with captured distance, local motion, health, line of sight, ground state, hit timing and combo state. A single snapshot replaces the former independent decision and target-observation buffers. Closing speed is the change between consecutive captured distances: positive means the distance is decreasing and negative means it is increasing. The value matures with the snapshot before any AI system consumes it.

Target movement is resolved in a horizontal combat frame captured with the observation. `forward` is the normalized horizontal vector from bot to target and `right = (-forward.z, 0, forward.x)`. Target velocity projected with dot products yields `forwardVelocity` and `lateralVelocity`; positive lateral velocity means target-right relative to the bot. A zero-length bot-target direction produces zero projections. Adaptive aim bias is applied along this captured `right` vector, never along a fixed world axis.

Attack cognition and physical execution are separate. `AttackIntentPlanner` uses the latest matured `PerceptionSnapshot`, held decision, the existing attempt cadence, profile reach and current bot-owned aim/critical state. It never reads the current target position, distance or line of sight. With aim enabled, eligibility requires the bot's actual current yaw/pitch to have reached the held `AimPlan` under the existing tolerance. With aim disabled, current bot look is compared with the delayed target eye position; the live target location is not used.

Each immutable `AttackIntent` records a monotonic sequence, creation tick, source perception tick, held decision, `DECISION`/`WATCHDOG` source, perceived distance/LOS, configured reach and intended-critical flag. It is consumed exactly once on the same `BotBrain` tick when ready; 1.0.9 adds no generic click or motor delay. Consumption records one ACTIVE bot attempt, calls `swingMainHand()`, then performs a neutral current-world ray trace from the bot eye along its physically executed view direction. The combined Paper `World.rayTrace(Location, Vector, double, FluidCollisionMode, boolean, double, Predicate)` call uses configured reach, precise block collision shapes, the closest entity hit and `raySize=0`. Solid obstruction, another entity, current aim error or a moved/out-of-reach target therefore produces `WHIFF`; invalid/despawned/cross-world targets produce `TARGET_INVALID`. Only a ray result whose entity is the duel target calls `bot.attack(target)`.

Physical contact is not a successful hit. `EntityDamageByEntityEvent` remains authoritative for `botHits`, damage, combo, lethality and `lastSuccessfulOutgoingHitTick`. `lastAttackAttemptTick` updates for every consumed intent, including a whiff, and continues to drive the unchanged nominal attack cadence. `botAttempts` counts intents, and `botMisses = max(0, botAttempts - botHits)`. W-tap/S-tap `movement.afterAttack(...)` runs after every consumed attempt, hit or miss. The watchdog creates an ordinary intent subject to the same delayed eligibility, cadence and physical probe; debug reports its count plus the last source/result. Successful bot criticals are counted only when the synchronous damage event occurs while the contacting intent is active. This preserves the existing falling-window approximation; pure whiffs never increment `botCrits`, but exact vanilla critical-mechanics validation is deferred.

Each duel owns one signed 64-bit seed. `MatchRandom` derives isolated `DECISION`, `DECISION_REACTION`, `AIM`, `AIM_REACTION`, `MOVEMENT`, `MOVEMENT_REACTION`, `CRITICAL`, and `TECHNIQUE` child streams by applying the SplitMix64 finalizer to `rootSeed XOR subsystemSalt`; subsystem salts are unique fixed 64-bit constants, and each child uses Java's `SplittableRandom`. Reaction jitter never consumes motor/error streams, so changing a reaction channel does not shift another channel or the aim-error/movement sequence. Streams are created once per `BotBrain`. `/pvpbot debug` exposes the active seed and compact `configured base±jitter:plan age/ticks remaining` values for D/A/M. Administrators can set a one-use seed for their next duel with `/pvpbot seed <long>`; it is consumed when that duel is created and is not persisted.

Random skin selection in `ConfiguredSkinProvider` and the match UUID are independent non-combat operations; neither affects combat behavior nor consumes any match stream.

Reproducibility means that the same seed and profile produce the same AI random decisions when given the same sequence of perception/input states. A duel against a human is not guaranteed to look identical because player position, timing, network state, and other world inputs are external to the RNG.

- **Perception:** one coherent delayed snapshot containing target body/eye/velocity, distance, combat-frame relative movement, vertical state, health, line of sight, hit timing and combo.
- **Decision reaction:** when its gate opens, `HitSelectController.decide(...)` uses the latest matured snapshot and replaces the held `Decision`. The existing formulas are unchanged, and no random decision recomputation occurs while the gate is closed.
- **Aim reaction:** when its gate opens, planning creates a held `AimPlan` from the delayed target eye, delayed velocity prediction, captured combat frame, adaptive lateral bias, accuracy, and persistent angular error. Every server tick, motor execution reads the bot's current eye/rotation and approaches that held point using `maxYawSpeed`/`maxPitchSpeed`. It does not read a live target point. Slow aim reaction therefore tracks an older plan smoothly instead of freezing and jumping.
- **Aim error:** the 1.0.7 error amplitude, Gaussian formulas, persistence, and 22% refresh chance are retained. The refresh trial now occurs once per meaningful aim replan rather than at uncontrolled 20 Hz. Consequently, at reaction intervals longer than one tick, error changes less frequently; this intentional timing change ties error deterministically to cognition without rebalancing amplitude.
- **Movement reaction:** when its gate opens, planning converts the latest delayed combat frame, distance, held decision, combo pressure, and delayed grounded/hit timing into a held `MovementPlan`. Every tick, execution reapplies that plan's forward/retreat/hold vector and sprint behavior. Newer target position/direction cannot change it before the movement gate opens. Arena-center recovery still uses the live bot position as a physical safety boundary, not as target planning input.
- **Strafe timing:** strafe activation, direction, and the existing random 6-plus-tick switch cadence remain internal execution timing on the `MOVEMENT` stream. They can progress while a held movement strategy executes and are not forced to switch at each movement reaction. Movement reaction changes the captured combat frame/strategy; it neither produces per-tick left/right oscillation nor consumes strafe randomness.
- **Hit-select:** prioritizes escape under pressure, reach, modern cooldown discipline, counter window, critical opportunity and spacing/bait. It is not a random attack chance.
- **Aim:** predicted target point, bounded yaw/pitch rotation, persistent Gaussian error and bounded adaptive lateral bias; never snaps directly by default.
- **Reach:** delayed 2.0–6.0 cognitive eligibility plus an exact current-view Paper ray trace at the same configured maximum distance.
- **Criticals:** starts a bounded jump only for a selected critical decision and attacks during a falling airborne window.
- **Movement:** direct approach/hold/retreat/chase plus noisy variable strafing and arena-center recovery; no mob pathfinder.
- **Sprint reset:** W-tap stops sprint for a real tick after selected hits; S-tap applies a brief backward velocity. Both are independent.
- **Jump reset:** a bounded on-ground jump opportunity while receiving combo pressure. This implementation is a movement/knockback timing approximation that requires client validation on 26.2.
- **Combo:** each successful opposing hit resets the prior side's combo. Chasing and escape affect movement decisions.
- **Adaptation:** each newly matured snapshot is learned exactly once; rolling delayed observations of aggression, local lateral bias and jumping create a confidence-bounded aim/aggression bias. Maximum configured strength is 0.75; no future knowledge or ML is used.

## Parameters

All numeric values are `double`; GUI changes are clamped. All listed controls affect live runtime behavior.

| Name | Range | Default | Effect |
|---|---:|---:|---|
| `simulatedPingMs` | 0–500 ms | 85 | Snapshot availability delay |
| `reaction.decisionMs` | 0–500 ms | 130 | Tactical/HitSelect response delay |
| `reaction.decisionJitterMs` | 0–200 ms | 30 | Symmetric decision interval jitter |
| `reaction.aimMs` | 0–500 ms | 130 | Aim-plan response delay |
| `reaction.aimJitterMs` | 0–200 ms | 30 | Symmetric aim-plan interval jitter |
| `reaction.movementMs` | 0–500 ms | 130 | Movement-plan response delay |
| `reaction.movementJitterMs` | 0–200 ms | 30 | Symmetric movement-plan interval jitter |
| `reach.blocks` | 2.0–6.0 | 2.9 | Central attack eligibility distance |
| `aim.accuracy` | 0–1 | .68 | Aim error amplitude |
| `aim.predictionStrength` | 0–1 | .42 | Target velocity lead |
| `aim.maxYawSpeed` | 3–90°/tick | 19 | Horizontal turn limit |
| `aim.maxPitchSpeed` | 2–90°/tick | 13 | Vertical turn limit |
| `hitSelect.skill` | 0–1 | .55 | Counter-window and cooldown decision quality |
| `hitSelect.chance` | 0–1 | .85 | Low-skill willingness to commit rather than hold |
| `hitSelect.patience` | 0–1 | .50 | Willingness to bait/wait |
| `hitSelect.counterHitPreference` | 0–1 | .50 | Counter timing window |
| `hitSelect.cooldownDiscipline` | 0–1 | .75 | Required modern cooldown strength |
| `hitSelect.baitPreference` | 0–1 | .35 | Profile preference recorded for future utility expansion; current bait requires patience and observed aggression |
| `criticals.skill` | 0–1 | .50 | Critical eligibility/probability |
| `criticals.chance` | 0–1 | .45 | Critical attempt frequency |
| `strafe.skill` | 0–1 | .60 | Direction-switch stability |
| `strafe.chance` | 0–1 | .85 | Tick-level lateral movement chance |
| `strafe.intensity` | 0–1 | .60 | Lateral velocity |
| `spacing.skill` | 0–1 | .58 | Retreat strength |
| `spacing.preferredDistance` | 1.8–4.5 | 2.85 | Hold/approach boundary |
| `spacing.forwardPressure` | 0–1 | .58 | Approach/chase velocity |
| `sprintReset.skill` | 0–1 | .50 | General multiplier for W/S sprint resets |
| `wTap.skill`, `wTap.chance` | 0–1 | .50, .52 | Product controls one-tick sprint stop |
| `sTap.skill`, `sTap.chance` | 0–1 | .42, .38 | Product controls backward reset |
| `jumpReset.skill`, `jumpReset.chance` | 0–1 | .35, .32 | Product controls pressured grounded jump |
| `combo.chaseSkill` | 0–1 | .55 | Profile chase competence; outgoing combo selects chase state |
| `combo.escapeSkill` | 0–1 | .50 | Profile escape competence; incoming combo selects retreat state |
| `adaptation.strength` | 0–.75 | .25 | Bounded learned bias |

Toggles: `aim`, `reach`, `hitSelect`, `criticals`, `strafe`, `spacing`, `wTap`, `sTap`, `jumpReset`, `combo`, `adaptation`. Aim/reach/critical/strafe/W/S/jump/combo/adaptation are `SUPPORTED` at the implementation level but require the real-client checklist. `BLOCK_HIT` is `UNSUPPORTED_BY_VERSION` and `UNSUPPORTED_BY_CURRENT_KIT`. No legacy cosmetic block-hit is faked.

Built-in EASY/NORMAL/HARD/EXPERT profiles initially copy each 1.0.7 `baseReactionMs` value to all three base channels and each `reactionJitterMs` value to all three jitter channels; no unrelated difficulty values were rebalanced. When a YAML preset or SQLite Custom payload contains legacy fields and lacks a corresponding new field, `ProfileMigration` supplies that legacy value to the missing channels. Explicit new fields win. Runtime profiles contain only the six new keys, and newly saved Custom profiles serialize only those keys.

Criticals, W-tap, S-tap, jump-reset, and attack execution continue to use held tactical/movement state within the existing scope. 1.0.9 introduces `AttackIntent` without a separate technique reaction channel. No Utility AI, player swing/cooldown model, personality, opening strategy, JSONL telemetry, or NoDebuff behavior is introduced.
