# Bot AI

The runtime pipeline is world snapshot → logical latency buffer → reaction gate with jitter → hit-select decision → aim/movement/combat execution. It never sleeps or delays packets. The bot sees only delayed `PerceptionSnapshot` data; current entity access is limited to executing the chosen action.

Each duel owns one signed 64-bit seed. `MatchRandom` derives isolated `DECISION`, `AIM`, `MOVEMENT`, `CRITICAL`, and `TECHNIQUE` child streams by applying the SplitMix64 finalizer to `rootSeed XOR subsystemSalt`; subsystem salts are fixed 64-bit constants, and each child uses Java's `SplittableRandom`. Streams are created once per `BotBrain`, so an additional AIM draw cannot advance MOVEMENT. `/pvpbot debug` exposes the active seed. Administrators can set a one-use seed for their next duel with `/pvpbot seed <long>`; it is consumed when that duel is created and is not persisted.

Random skin selection in `ConfiguredSkinProvider` and the match UUID are independent non-combat operations; neither affects combat behavior nor consumes any match stream.

Reproducibility means that the same seed and profile produce the same AI random decisions when given the same sequence of perception/input states. A duel against a human is not guaranteed to look identical because player position, timing, network state, and other world inputs are external to the RNG.

- **Perception:** distance, relative movement, vertical state, health, line of sight, hit timing and combo.
- **Latency/reaction:** snapshots mature after simulated ping; decisions update after an independent reaction delay and jitter.
- **Hit-select:** prioritizes escape under pressure, reach, modern cooldown discipline, counter window, critical opportunity and spacing/bait. It is not a random attack chance.
- **Aim:** predicted target point, bounded yaw/pitch rotation, persistent Gaussian error and bounded adaptive lateral bias; never snaps directly by default.
- **Reach:** one centralized 2.0–6.0 check plus active match, target, line-of-sight and arena validity.
- **Criticals:** starts a bounded jump only for a selected critical decision and attacks during a falling airborne window.
- **Movement:** direct approach/hold/retreat/chase plus noisy variable strafing and arena-center recovery; no mob pathfinder.
- **Sprint reset:** W-tap stops sprint for a real tick after selected hits; S-tap applies a brief backward velocity. Both are independent.
- **Jump reset:** a bounded on-ground jump opportunity while receiving combo pressure. This implementation is a movement/knockback timing approximation that requires client validation on 26.2.
- **Combo:** each successful opposing hit resets the prior side's combo. Chasing and escape affect movement decisions.
- **Adaptation:** rolling observations of aggression, lateral bias and jumping create a confidence-bounded aim/aggression bias. Maximum configured strength is 0.75; no future knowledge or ML is used.

## Parameters

All numeric values are `double`; GUI changes are clamped. All listed controls affect live runtime behavior.

| Name | Range | Default | Effect |
|---|---:|---:|---|
| `simulatedPingMs` | 0–500 ms | 85 | Snapshot availability delay |
| `baseReactionMs` | 20–500 ms | 130 | Minimum decision cadence |
| `reactionJitterMs` | 0–200 ms | 30 | Random reaction variation |
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
