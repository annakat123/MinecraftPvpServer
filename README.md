# PvPBotServer

Local Paper 26.2 Sword practice server. A player fights a configurable Citizens player-NPC named `PracticeBot`. Both sides use the same unenchanted diamond sword/armor and 20 HP.

## Verified stack

- Minecraft Java Edition / Paper API: **26.2** (`io.papermc.paper:paper-api:26.2.build.+`). The reproducible local server setup pins verified Paper **build 112**.
- Java: **25**, portable Amazon Corretto, downloaded only into ignored `runtime/` and SHA-256 verified.
- Gradle: **9.6.1**, Wrapper, Java 25 toolchain.
- Citizens: **2.0.43-SNAPSHOT build 4220**, pinned Jenkins artifact. Citizens has 26.2 code, but its initial upstream 26.2 port was explicitly marked untested; therefore in-game movement/knockback remain manual-test items.
- SQLite JDBC: **3.53.1.0**, bundled into the plugin JAR.
- Tests: JUnit **5.13.4**.

Primary references: [Paper project setup](https://docs.papermc.io/paper/dev/project-setup/), [Paper Java requirements](https://docs.papermc.io/paper/getting-started/), [Paper downloads service](https://docs.papermc.io/misc/downloads-service/), [Citizens source](https://github.com/CitizensDev/Citizens2), [Citizens API](https://wiki.citizensnpcs.co/API), [SQLite JDBC releases](https://github.com/xerial/sqlite-jdbc/releases), [Gradle Java compatibility](https://docs.gradle.org/current/userguide/compatibility.html).

## First start on Windows

1. Run `setup-local.bat` and explicitly type `YES` after reading the Mojang EULA link. It downloads verified Java, builds the plugin, installs the pinned Paper 26.2/Citizens builds, and prepares `local-server/`.
2. Run `run-server.bat`. Every run performs a clean build and copies the plugin automatically.
3. Start a legitimate authenticated Minecraft **26.2** client and connect to `localhost`.
4. Click **Duel Selector**, choose a difficulty, then click **Start SWORD duel**.

`online-mode=true`, `server-ip=` and port `25565` are written by setup. No public exposure or port forwarding is configured.

## Commands and UI

- Lobby hotbar: Duel Selector, Bot Settings, Statistics.
- `/stats` — player statistics; `/botstats [profile]` — aggregate profile statistics.
- `/pvpbot profile <EASY|NORMAL|HARD|EXPERT|CUSTOM>`.
- `/pvpbot debug [player]`, `/pvpbot seed <long>`, `/pvpbot arena info`, `/pvpbot capabilities`, `/pvpbot reload` (`pvpbot.admin`, op by default). `seed` sets a one-shot seed for the issuing player's next duel.

Configuration: `config.yml`, `bot-profiles.yml`, `arenas.yml`. Player Custom profiles and match/statistics data are stored by UUID in `plugins/PvPBot/pvpbot.db`. Invalid public parameters are clamped. Preset YAML changes require restart; `/pvpbot reload` reloads central settings only.

## Current limitations

- NoDebuff is intentionally not implemented.
- Legacy sword block-hit is unavailable in modern 26.2 and with the no-shield Sword kit.
- Citizens supplies the player entity/skin layer; direct velocity steering supplies close-range combat movement. The quality of Citizens 26.2 knockback, skin loading and fake-player attack semantics must be verified with a real client.
- “Death” is resolved from lethal final damage and immediately presented as victory/defeat, avoiding a vanilla respawn screen while retaining one-lethal-event-per-duel semantics.
- The settings GUI exposes every runtime numeric parameter in the current MVP on one page plus all technique toggles. Administrative YAML remains the place for preset editing.

See [ARCHITECTURE.md](ARCHITECTURE.md), [BOT_AI.md](BOT_AI.md), and [TESTING.md](TESTING.md).
