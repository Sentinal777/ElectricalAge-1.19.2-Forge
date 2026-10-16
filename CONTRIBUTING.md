# Contributing to the 1.19.2 beta

Contributions and forks are welcome under the existing code and asset licenses. You do not need to be a programmer to help: reproducible bug reports, survival playtesting, screenshots, explanations of confusing behavior, documentation, and translations are useful.

## Where to work

Use this repository's Issues and Pull Requests for the unofficial port. The active native project is `forge-1.19.2/`; the root build and `reference-1.24.6/` are preserved legacy/reference code. Keep Minecraft 1.19.2, exact Forge 43.5.0, and Java 17 as the beta target.

Read `AGENTS.md`, the native `docs/GETTING_STARTED.md`, `docs/PORT_STATUS.md`, and the relevant `docs/original-*-audit.json` before changing a device. Prefer Kotlin for new code. Keep registry IDs and saved-data formats stable unless a deliberate migration is included. Preserve original attribution and license notices.

## Reporting a bug

Include the exact ELN/Forge/Java versions, optional mods and their versions, the smallest circuit or machine that reproduces it, expected versus actual behavior, and steps from a fresh isolated world. Attach relevant logs/screenshots after removing personal information, credentials, and server addresses. State whether it happens without other mods. Please distinguish an actual runtime defect from an untested variant listed in the coverage matrix.

## Building and checking changes

From `forge-1.19.2/`, run `./gradlew build` (Windows: `.\gradlew.bat build`) with JDK 17. The normal runtime artifact ends in `-all.jar` and embeds Kotlin/Commons Numbers. First-time setup needs Internet access.

Run `./gradlew test` for unit checks and `./gradlew runGameTestServer -PqaWorld=gametest-your-change` for real native GameTests. Optional published-mod checks add `-PbcceIntegration -PccIntegration`; use a different fresh QA world and inspect actual Minecraft test completion, not only Gradle's exit code. Accept the Minecraft EULA yourself when required by the game; no personal worlds should be used for these checks.

Client checks and QA flags are documented separately. `-PclientSmoke`, `-PlightDiagnostic`, `-PbcceIntegration`, and `-PccIntegration` are not normal release-build flags. Build normally again after QA runs. The host pacing agent is a diagnostic aid, not required for ordinary players.

For electrical changes, compare against analytical/source behavior and check energy accounting. For inventories/fluids/entities, check real resources, removal, and reload behavior. For visual/GUI changes, inspect actual in-game results. Use checks appropriate to the change and report what you actually ran.

## Pull requests and AI use

Explain the player-visible problem and resulting behavior. Include reproduction steps, relevant checks, and save/registry compatibility implications. Small focused changes are easier to review. Do not weaken assertions, remove functionality, or substitute registration counts for operating-device checks.

AI-assisted contributions are welcome. Disclose substantial AI assistance and describe how the result was reviewed and verified. The existing beta was heavily AI-assisted and needs independent human review; do not assume that a passing test or an AI-generated explanation proves correctness. Preserve the original engineering model and document intentional departures.

Helpful areas include independent code/numerical review, extended survival progression and multiplayer testing, installation variants without individual fixtures, cross-mod compatibility, performance profiling, documentation, and translations. Experienced maintainers are welcome; no response-time or release schedule is promised.
