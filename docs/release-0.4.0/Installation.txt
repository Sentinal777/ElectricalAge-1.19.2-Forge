# Windows 11 installation and build

Use Minecraft 1.19.2 with Forge 43.5.0 exactly and Java 17. Create a new game directory for the first installation. Keep world backups before changing any mod version.

1. Install the 1.19.2 Forge 43.5.0 client from https://files.minecraftforge.net/net/minecraftforge/forge/index_1.19.2.html . Select its profile and a separate game directory.
2. Place ElectricalAge-1.19.2-0.4.0.jar in that directory's mods folder. Install the complete Jar-in-Jar artifact; the thin and sources JARs are not standalone runtime mods. Kotlin and Commons Numbers are already embedded.
3. Optional BuildCraft: install BCCE 8.0.23 for Forge 1.19.2, CurseForge file 9035804: https://www.curseforge.com/minecraft/mc-mods/buildcraft-community-edition/files/9035804 . It is a separate download and is not required to run ELN.
4. Optional computers: install CC: Tweaked 1.101.4 for Forge 1.19.2, file 5005332: https://www.curseforge.com/minecraft/mc-mods/cc-tweaked/files/5005332 . Computer Probe exposes the ElnProbe peripheral and the original nine signal/wireless methods.
5. Start a new test world. Begin with a battery or source, correctly rated cable, a load and a closed return. Use the wrench for orientation and individual equipment removal; use meters for live measurements. Fit original hardware, bulbs, coils, fuses and machine upgrades in their component screens. Original survival recipes are supplied as datapacks.

The Energy Converter selects electrical-to-FE/MJ export or FE/MJ-to-electrical import. Both directions use finite stored energy and losses. MQTT and Modbus networking are disabled by default in config/eln-common.toml; MQTT endpoints are listed separately in config/eln-mqtt.json. Enable and configure them deliberately if needed. The Tutorial Sign reads EA/tutorialSign.txt in that world's folder.

To disguise a surface-component node, hold C (configurable in Controls) and right-click it with a supported full block. Survival uses one block. The first normal break removes only the cover and leaves the live components; a later break removes equipment. Glass shows the components inside. Block entities, fluids and partial collision shapes cannot be used as covers.

## Build

Extract the complete source ZIP, set JAVA_HOME to JDK 17, and open PowerShell in ElectricalAge-development/forge-1.19.2. Run .\gradlew.bat build. The first build requires Internet access for Gradle 8.8, ForgeGradle 6.0.54, Kotlin and the pinned compile-only BCCE/CC APIs. Later cached builds can use --offline. The legacy root wrapper is unchanged and is not the native port's build entry point.

Install build/libs/electrical-age-1.19.2-0.4.0-port-all.jar. Normal builds embed Kotlin/Commons Numbers and exclude BCCE, CC and QA code. For tests use .\gradlew.bat runGameTestServer; add -PbcceIntegration and -PccIntegration for the optional published-mod profile. Inspect actual GameTest completion lines as well as the process result.

Do not use -PclientSmoke, -PlightDiagnostic, -PbcceIntegration or -PccIntegration when producing a normal release artifact. Build normally again after those QA runs. The original-device audits and tools/validate_production.py describe the isolated reproduction checks. Runtime worlds, credentials and caches are excluded from the source archive.
