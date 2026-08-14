# PvPBot AI Context

> Read AI_CONTEXT.md first. Do not recursively inspect the whole repository by default. Use this file to identify the minimal relevant production files and tests for the task. Verify implementation details against those files before modifying them.

Production code and tests are authoritative if this handoff becomes stale. Use `README.md`, `ARCHITECTURE.md`, `BOT_AI.md`, and `TESTING.md` for deeper explanations.

## Current state

- Product version: 1.0.11.
- Branch: `main`.
- Immutable 1.0.11 baseline: `3a5b28a3077a4a646087b7ee3838e07b22666a7a` (approved/pushed PvPBot 1.0.10).
- Repository: `https://github.com/annakat123/MinecraftPvpServer`.
- Scope: local Sword PvP practice server against one configurable Citizens player-NPC (`PracticeBot`), not a general combat framework.
- 1.0.11 scope: observability, deterministic combat QA and explainability only; no intentional combat/balance changes.

## Runtime stack

- Paper API/server 26.2.
- Java toolchain/release 25.
- Citizens 2.0.43-SNAPSHOT (`citizens-main` + `citizensapi`).
- Gradle Kotlin DSL; JUnit 5; SQLite JDBC; Gson for controlled JSONL record serialization.
- No direct NMS. Bukkit/Paper entity/world operations stay on the server thread.

## Compact architecture map

- Plugin wiring/lifecycle: `src/main/java/dev/pvpbot/PvPBotPlugin.java`.
- Match lifecycle/events: `duel/match/DuelManager.java`, `DuelMatch.java`, `MatchStateMachine.java`.
- Main AI tick: `bot/ai/BotBrain.java`.
- Perception: `bot/ai/perception/{PerceptionSnapshot,LatencyBuffer,CombatFrame,RelativeMotion}.java`.
- Reaction: `bot/ai/reaction/ReactionGate.java`.
- Deterministic RNG: `bot/ai/random/MatchRandom.java`.
- HitSelect: `bot/combat/hitselect/HitSelectController.java`.
- Aim: `bot/combat/AimController.java`.
- Movement/vertical: `bot/movement/{MovementController,VerticalActionController,VerticalAction,KnockbackSignalPolicy}.java`.
- Attack pipeline: `bot/combat/attack/{AttackIntentPlanner,AttackIntent,AttackExecutor,PhysicalAttackProbe,PaperPhysicalAttackProbe,AttackTiming}.java`.
- Citizens boundary: `bot/entity/`.
- Tracing: `bot/trace/`.
- Deterministic QA: `src/test/java/dev/pvpbot/qa/`.

## Exact BotBrain tick pipeline

1. Increment the AI tick; expire combo timing using the current Bukkit tick.
2. Resolve the current Citizens bot entity; return if missing/invalid/not in world/dead.
3. Advance vertical state, observe grounded state, and execute at most one matured Jump Reset opportunity.
4. Capture exactly one coherent immutable `PerceptionSnapshot` from current world state.
5. Offer it once to `LatencyBuffer` with `simulatedPingMs`; poll the latest matured snapshot; return if none is available.
6. Set `latestPerceived`; let adaptation observe that captured tick once.
7. If the decision gate is ready, compute cooldown and replace held explainable HitSelect result; schedule the next decision gate once.
8. If the aim gate is ready, create a held `AimPlan` from matured perception; schedule once. Execute aim motor toward the held plan every tick.
9. If the movement gate is ready, create a held `MovementPlan` from matured perception and held decision; schedule once. Execute held movement every tick subject to physical safety/KB lock.
10. Evaluate critical provenance/window. A new critical setup jump ends that tick; rising/non-window critical motion holds attack.
11. Ask `AttackIntentPlanner` to create an ordinary decision/watchdog intent from matured perception, held decision, cadence, reach and bot-owned aim/critical state.
12. Consume an intent once through `AttackExecutor`: attempt bookkeeping → one Citizens animation → current physical probe → call `bot.attack(target)` only on target contact → post-attempt W/S handling.

Trace emissions are interleaved only at existing boundaries and never feed values back into this pipeline.

## Perception, latency and reaction

- One snapshot owns cloned target body/eye/velocity, local combat frame, distance/closing speed, local forward/lateral velocity, vertical velocities, HP, combo/hit timing, LOS and grounded state.
- Target reads enter cognition only through `captureObservation` and the latency buffer.
- Simulated ping delays information availability; it does not sleep or create scheduler tasks.
- Decision, aim and movement have independent `ReactionGate`s on AI ticks.
- Each gate draws jitter once after an update, clamps the final interval to non-negative, converts with `max(1, ceil(ms/50))`, then holds its plan until ready.
- Aim/movement motor execution continues every tick from held plans; newer perception cannot update them early.

## HitSelect architecture

`HitSelectController.decide` returns `DecisionResult(decision, reason, inputs)`. Preserve this ordered branch architecture and all formulas:

1. no LOS → close distance;
2. recent incoming combo pressure → escape;
3. outside reach → combo chase or close distance;
4. HitSelect disabled → attack only at cooldown >= 0.9, otherwise wait;
5. cooldown discipline threshold → wait;
6. recent counter window plus closing-speed condition → counter hit;
7. grounded/in-range critical eligibility → critical attack;
8. preferred-distance/patience/bait/adaptation condition → bait;
9. low commitment plus low closing speed → hold;
10. default attack.

Branch-exact reason codes are in `HitSelectController.DecisionReason`. `HitSelectEquivalenceTest` freezes the 1.0.10 logic and compares 100,000 deterministic generated inputs. Do not reorder branches while instrumenting.

## AttackIntent and physical execution

- `AttackIntent` is immutable and records sequence, creation/perception ticks, held decision, `DECISION|WATCHDOG` source, perceived distance/LOS, reach and intended-critical provenance.
- Planner uses delayed perception, current bot-owned aim eligibility, held decision and cadence. It never reads live target position/LOS.
- Attempt cadence is 10 AI ticks. A non-attack held decision may create an ordinary watchdog intent after 30 ticks since the previous attempt.
- `AttackExecutor` consumes each sequence once. Every consumed intent records one attempt and requests one animation before probing.
- Physical probe uses the bot's current eye/direction and configured reach with Paper ray tracing.
- `CONTACT`: target is the closest ray result, so normal `bot.attack(target)` is invoked.
- `WHIFF`: obstruction, other entity, current aim error or moved target; no melee call.
- `TARGET_INVALID`: invalid/dead/offline/cross-world target; no melee call.
- Contact is not a hit. Only the synchronous confirmed `EntityDamageByEntityEvent` updates bot hits/damage/combo/successful-hit timing and successful critical statistics.
- Attempts count consumed intents; misses are `max(0, attempts - confirmed hits)`.
- Current cooldown limitation: HitSelect uses PvPBot's internal attempt-based `AttackTiming.cooldown = min(1, ticksSinceAttempt/12.5)`. It does not model the real player's swing/cooldown or a full vanilla attack-strength state. Do not silently replace this in observability work.

## Movement and vertical state

- Movement planning uses matured `CombatFrame`, distance, held decision, combo and grounded/hit timing.
- Execution is direct bounded velocity steering; no Citizens navigator/pathfinder.
- Normal combined horizontal controller speed is clamped to `0.275`; catastrophic arena-edge recovery is a physical safety override and must not rewrite tactical target state.
- Strafe activation/direction/switch cadence stays on the `MOVEMENT` RNG stream.
- W-tap is a real one-tick sprint pause; S-tap is a brief backward velocity and must not overwrite KB lock.
- Jump Reset: one confirmed player→bot hit creates one opportunity and samples `TECHNIQUE` once. A selected opportunity may jump at T+1/T+2 only, executes at most once, preserves X/Z and cannot be created/retried by combo state, WHIFF, AttackIntent or critical setup.
- Critical setup: proactive, separate from incoming hit/JRESET, normal Y=0.42 jump, preserves X/Z. Only `criticalSetupActive && falling` can mark an intent intended-critical; JRESET falling alone cannot.
- Knockback: accepted physical event requires same-AI-tick confirmed incoming hit and melee `ENTITY_ATTACK|SWEEP_ATTACK` cause plus non-zero horizontal impulse. Lock starts next AI tick for four ticks. Normal movement/S-tap cannot overwrite X/Z; Jump Reset may change only Y; emergency arena recovery remains allowed.

## Citizens boundaries

- `BotAnimation` isolates animation from attack execution. `CitizensBotAnimation` sends one `PlayerAnimation.ARM_SWING` to the duel viewer. Do not add a second explicit swing around `bot.attack`.
- Skin selection is pre-spawn and stable. Use cached signed texture data or the stable client default for that duel.
- An uncached name starts asynchronous cache warming without attaching the fetch to a spawned NPC; a later duel can use the warmed texture.
- `SkinTrait` update/default fetching is disabled after selection. Never replace/respawn an active NPC when a skin fetch finishes.

## Determinism and adaptation

- One signed 64-bit match seed owns isolated `SplittableRandom` streams: `DECISION`, `DECISION_REACTION`, `AIM`, `AIM_REACTION`, `MOVEMENT`, `MOVEMENT_REACTION`, `CRITICAL`, `TECHNIQUE`.
- Streams are derived by SplitMix64 finalization of root seed XOR a unique fixed salt. Never add combat `Math.random`, `ThreadLocalRandom`, `new Random`, or consume one subsystem's stream for another.
- QA generation uses its own test-only `SplittableRandom`; QA input generation must never advance `MatchRandom`.
- Same seed/profile is reproducible only for the same external input sequence.
- Adaptation is a bounded rolling observed model of aggression, local lateral bias and jump rate. It observes each matured capture tick once; maximum configured strength remains 0.75. It is not ML and has no future knowledge.

## 1.0.11 trace architecture

- Interface: `CombatTraceSink`; implementations: Noop, in-memory, JSONL.
- Production default: Noop. Disabled hot paths check `enabled()` before high-frequency event allocation.
- Events are immutable typed records: match start/end, captured/matured perception, decision, aim plan/execution, movement plan, vertical action, KB start/end, intent, execution, confirmed hit, final trace summary.
- JSONL schema 1 envelope: `schema`, `tick`, `event`, typed `data`; one object per line.
- Live files: `plugins/PvPBot/combat-traces/<time>_match-<id>_seed-<seed>_profile-<profile>.jsonl`; no player UUID/name is written.
- One plugin-owned `AsyncJsonlTraceWriter`, one ordered daemon writer, `ArrayBlockingQueue` capacity 4096, non-blocking `offer`, dropped-event counter, no thread per duel.
- `BotBrain.tick` performs no disk/DB IO. The writer flushes/closes asynchronously; `TRACE_SUMMARY` contains the final exact dropped count.
- Physical/current-world data is labeled execution/diagnostic only and cannot feed cognition.
- Noop/InMemory same-input comparison proves zero observer effect at the harness boundary.

## Combat QA harness

- Root: `src/test/java/dev/pvpbot/qa/`.
- `CombatScenarioRunner` uses real plannable domain components; it does not recreate Minecraft/Paper physics.
- Scripted scenarios: HIGH_PING_SIDE_STEP, INCOMING_KNOCKBACK, JUMP_RESET_VALID, JUMP_RESET_INVALID, CRITICAL_SETUP, REACTION_HOLD, WATCHDOG_ATTACK, CONTACT_WITHOUT_DAMAGE, ARENA_EDGE_RECOVERY.
- Deterministic fuzz inputs: approach/retreat, strafe/switches, grounded/jump state, incoming hits/KB, bounded distance, LOS, ping and reaction combinations.
- Quick default: 128 generated seeds × 180 ticks plus scripted scenarios and negative controls.
- Extended default: 4000 seeds × 300 ticks (1.2M generated transitions) plus scripted scenarios.
- Reports: `build/reports/combat-qa/report.{txt,json}`; failures: metadata + last 100 trace events in `build/reports/combat-qa/failures/*.jsonl`.
- Reproduction: `.\gradlew.bat combatQa -PqaSeed=<seed> -PqaScenario=<scenario>`.
- Metrics are scenario/harness observations, never claims about real-human game balance.

Key reusable invariants include attempts/hits/misses accounting; one execution and one animation per consumed sequence; no melee call on WHIFF/TARGET_INVALID; Jump Reset opportunity/one-shot/no-reroll rules; critical/JRESET provenance separation; KB movement/S-tap suppression; finite/bounded controller vectors; reaction-held plan isolation; WHIFF cannot update confirmed-hit timing; watchdog cannot bypass cadence/physical validation.

Mandatory harness negative controls: JRESET without hit, movement write during KB lock, duplicate intent execution, WHIFF recorded as confirmed hit, and two animations for one attempt. Production source is never mutated for these tests.

## 1.0.11 authenticated-client findings

Four completed live traces were analyzed: NORMAL 85 ms, CUSTOM 305 ms, EXPERT 35 ms and HARD 55 ms. All closed with zero dropped trace events. These findings are intentionally deferred to 1.0.12; do not silently change 1.0.11 combat behavior while maintaining its observability baseline.

- Confirmed: after the fixed four-tick KB lock ends under CUSTOM 305 ms, a pre-hit held `MovementPlan` can still be active and write X/Z while the bot is physically airborne. Physical motor eligibility must eventually be separated from delayed cognition.
- Confirmed: two RNG-approved Jump Reset opportunities expired because Paper knockback kept the bot airborne throughout the T+1/T+2 grounded-only window. Live execution was 0/42 incoming-hit opportunities and 0/2 approvals. Do not tune probability before repairing eligibility/timing.
- Confirmed architecture defect: arena containment is square, while emergency recovery uses a radial `halfSize - 2` threshold and is evaluated before KB suppression. This can conflict with wall/corner approach and can bypass normal KB preservation.
- Observed diagnostic concern: 55 `KNOCKBACK_STARTED` events occurred across 38 completed incoming-KB episodes; 21 episodes had two accepted same-tick signals. Trace data cannot yet prove whether Citizens/Paper applied two physical impulses.
- High-ping result: CUSTOM 305 ms produced 17 attempts, 4 contacts/confirmed hits and 13 WHIFFs (76.47%); average matured perception age was 6.78 ticks. The physical ray correctly rejects stale aim, but `simulatedPingMs` is an information-availability delay, not RTT, one-way network delay or delayed physical knockback.
- Watchdog result: HARD used watchdog for 6/14 intents (42.86%), primarily while held reasons were `OUT_OF_REACH_CLOSE_DISTANCE` or `INCOMING_COMBO_ESCAPE`; investigate natural attack availability before tuning watchdog.
- Telemetry gap: add explicit JRESET roll/approval/expiry reason, applied movement vector and suppression/recovery reason, current physical grounded/position/velocity, and correlated KB signal identity before claiming exact causes for every live visual symptom.
- Current JSONL invariant audit found one execution per 50 intents, 29 CONTACT, 21 WHIFF, no invalid confirmed-hit attribution, no duplicate intent execution, no non-finite values and no dropped events. The QA invariant engine does not yet import live JSONL.
- Required 1.0.12 regressions: high-ping stale-plan recovery after KB, Paper-airborne JRESET window, duplicate KB signal correlation, edge recovery during KB, square-corner approach, post-KB plan freshness and live-trace invariant import.

## Commands and verification

- `/pvpbot seed <long>`: one-use next-duel match seed.
- `/pvpbot debug [player]`: live compact state.
- `/pvpbot trace on|off|status`: current duel or one-use next-duel trace.
- `gradlew.bat clean test`.
- `gradlew.bat combatQa [-PqaSeed=... -PqaScenario=... -PqaSeeds=...]`.
- `gradlew.bat combatQaExtended [-PqaSeed=... -PqaSeeds=...]`.
- Release verification: `git diff --check`, `gradlew clean test`, `gradlew combatQa`, `gradlew clean build`, then meaningful bounded `combatQaExtended`.

## Known limitations and mandatory client checks

- Harness proves domain/state invariants only; it cannot prove authenticated-client rendering, exact Paper friction/knockback feel, current Citizens packet visuals, or real-human balance.
- Mandatory client checks remain: one visible swing per attempt; WHIFF no damage; current ray follows NPC view; reach 2.0/6.0; block/other-entity obstruction; normal contact damage/KB; critical plausibility; W/S behavior after misses; Jump Reset timing and whether it actually reduces KB; KB movement preservation/resumption; stable NPC skin/entity identity.
- If local Paper 26.2 + Citizens runtime exists, retain the separate startup smoke path in `TESTING.md`; do not create a fake Minecraft server in unit tests.

## Intentionally deferred

- Utility AI and any smarter/rebalanced decision system.
- Player swing/cooldown model, personality, opening strategy, NoDebuff controllers, legacy cosmetic block-hit, ML adaptation.
- Automatic balance changes based on generated metrics.

## Decisions future sessions must preserve

- Do not alter balance while instrumenting/testing.
- Preserve one perception capture/latency path and independent held D/A/M reaction gates.
- Preserve AttackIntent cognition vs current-world physical execution vs confirmed damage separation.
- Preserve Jump Reset/critical/knockback provenance and one-shot timing.
- Preserve one Citizens animation per consumed intent and pre-spawn stable skin policy.
- Preserve isolated MatchRandom streams and QA RNG isolation.
- Keep tracing one-way, opt-in, bounded, non-blocking and Noop by default.
- Treat production source/tests as authoritative; update this file when architecture intentionally changes.

## Recent release history

- 1.0.9: introduced delayed-perception one-shot AttackIntent, current-view physical ray validation and real WHIFF/contact separation while keeping confirmed damage event-authoritative.
- 1.0.10: corrected event-relative Jump Reset timing, four-tick physical knockback preservation, one Citizens viewer attack animation and stable pre-spawn skin/cache warming; no balance redesign.
- 1.0.11: adds branch-exact HitSelect reasons, typed observational JSONL tracing, shared bounded async writer, live trace commands, deterministic scripted/fuzz combat QA, reusable invariants/negative controls, reports and failure reproduction; no Utility AI or intentional combat behavior changes.
