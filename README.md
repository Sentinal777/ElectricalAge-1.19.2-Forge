# Electrical Age - Unofficial Forge 1.19.2 Port

**0.4.0 beta | Minecraft 1.19.2 | Forge 43.5.0 exactly | Java 17**

An unofficial community port of the original Electrical Age engineering mod to modern Forge. It preserves detailed voltage/current circuit simulation, electrical distribution, machines, automation, mechanical and fluid systems, and optional BuildCraft interoperability. It is based on Electrical Age 1.24.6, commit `17f99f7e2dd6055527bb43578af66f1d62c6842e`, with recovered earlier 1.19.2 port work reused.

## AI disclosure and community invitation

**This port was developed with substantial AI assistance using OpenAI ChatGPT/Codex.** AI performed source migration, implementation, debugging, test development, and release packaging under the project owner's direction. The project owner supplied goals, decisions, and feedback and does not have a programming background. This is not presented as independently human-reviewed code.

This beta is offered as a foundation for others to inspect, improve, and build upon under the inherited licenses. Contributions, forks, bug reports, independent code review, playtesting, documentation, and translations are welcome. Experienced maintainers are especially welcome. There is no promise of a particular support or release schedule.

The original Electrical Age authors deserve the credit for the original mod, simulation, artwork, and gameplay. This port is not an official release or an endorsement by the upstream team. See [original credits](docs/credits.md), [AI disclosure](docs/AI_DISCLOSURE.md), and [licenses](LICENSE.md).

## Download and install

Download `ElectricalAge-1.19.2-0.4.0.jar` from the [beta release](https://github.com/Sentinal777/ElectricalAge-1.19.2-Forge/releases/tag/v0.4.0-beta). Use a separate Minecraft 1.19.2 game directory with **Forge 43.5.0 and Java 17**, and place the JAR in its `mods` directory. Start with a new test world; back up saves before changing versions.

Kotlin and Commons Numbers are embedded. Optional mods are separate downloads:

- [BuildCraft Community Edition 8.0.23](https://www.curseforge.com/minecraft/mc-mods/buildcraft-community-edition/files/9035804)
- [CC: Tweaked 1.101.4](https://www.curseforge.com/minecraft/mc-mods/cc-tweaked/files/5005332)

See [installation and build instructions](forge-1.19.2/docs/GETTING_STARTED.md). BCCE and CC are optional; ELN also runs without them.

## Beta status and verification

The implemented port covers the original active equipment families: the original MNA electrical solver, six-face components and covers, generation and storage, cables/protection/transformers/overhead transmission, processing machines, thermal/steam/shaft systems, instruments and controls, recipes/resources/world generation, original models/GUI/audio, and BCCE power/item/fluid integration.

Executed checks for the delivered runtime:

- **210 unit tests** passed.
- **286 Minecraft GameTests** passed with BCCE/CC, and **271** passed in a separate standalone profile. These overlapping profiles are not summed.
- Actual integrated-client checks covered models, GUIs, packets, audio, lighting, and gameplay interactions.
- Three normal installed-JAR dedicated-server profiles checked real circuits, save/restart persistence, covered components, loaded networks, and natural ores; optional profiles checked published BCCE engines/MJ and real CC Lua calls. A client check also exercised an ELN-powered BCCE quarry.
- A fresh extraction of the delivered source rebuilt and passed all 210 unit tests. Its runtime payload matched the delivered JAR, excluding documentation and ZIP metadata.

**Beta limits:** some calibration/installation variants share tested implementations without their own individual fixture. A long manual survival, multiplayer, or broad modpack campaign has not been completed. Legacy OpenComputers/IC2 runtimes are not supported as tested modern integrations. The original unfinished development Conduit and deliberate NaN debug item are accounted for separately. Read the [coverage report](forge-1.19.2/docs/PORT_STATUS.md), [component matrix](forge-1.19.2/docs/feature-coverage.csv), [test results](forge-1.19.2/docs/VALIDATION.md), and [BuildCraft matrix](forge-1.19.2/docs/BUILDCRAFT_COMPATIBILITY.md) for exact scope.

## Build and contribute

The **native port is in `forge-1.19.2/`**. The repository root also preserves the original legacy project; its wrapper is not the build entry point for this port.

With JDK 17 installed:

```powershell
cd forge-1.19.2
.\gradlew.bat build
```

On Linux/macOS, use `./gradlew build` from the same directory. The normal installable result is `build/libs/electrical-age-1.19.2-0.4.0-port-all.jar`. The first build downloads the pinned dependencies.

[Read the contribution guide](CONTRIBUTING.md). Issues and pull requests in **this repository** are welcome; please do not send port-specific support requests to the upstream team.

## Licenses and provenance

Original Electrical Age code is **LGPL v3**. Original graphics/models are generally **CC BY-NC-SA 3.0 Unported**, with the named exceptions and public-domain assets in [LICENSE.md](LICENSE.md). Preserve creator credits, the noncommercial asset terms, and share-alike obligations. Community reuse is subject to these licenses; the entire project is not relicensed under MIT or another blanket license.

Dependency and numerical-code notices are in [NOTICES.md](forge-1.19.2/docs/NOTICES.md) and [docs/licenses](forge-1.19.2/docs/licenses). No Minecraft, BCCE, or CC runtime is bundled in this repository. The included small QA pacing agent is for automated client checks and is excluded from the installable mod.

`reference-1.24.6/` preserves the exact original reference. `docs/upstream-publication/` preserves the inherited README/contribution guide and legacy workflows. The original audited source ZIP and its manifest remain release assets/reference records; GitHub publication adds this README, AI disclosure, contribution templates, and release documentation without changing the verified runtime source.
