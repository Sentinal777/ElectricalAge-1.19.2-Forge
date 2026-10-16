# BuildCraft Community Edition interoperability

Verified target: published BCCE 8.0.23, Forge Minecraft 1.19.2 file 9035804, source commit 23c6af379676ce5262c5c0cb6f1f331edc9b12c6. ELN uses Forge 43.5.0 and Java 17. No BCCE source or binary changes are delivered; BCCE remains optional and separate.

The pinned original baseline contains BuildCraft fuel values and generic item/fluid automation, but no active direct MJ integration implementation. The modern native MJ bridge is an equivalent added adapter. It reads BCCE's own micro-MJ/FE conversion service, supports its MJ_ONLY mode, shares one finite fractional buffer between directions and applies the configured conversion loss. Native linkage is pinned to the inspected version; other versions are unverified.

Actual executed assemblies include published engine-to-ELN resistor; ELN converter-to-quarry; real chest/wooden item-pipe input and finished-output extraction; real fuel tank/wooden fluid pipes; finite gasoline generators, radial/gas turbines and liquid heat furnace; and coal-to-heat-to-steam-to-powered-BCCE-pipe-to-shaft-generator-to-electrical-load with complete energy accounting. The live quarry built its own frame and excavated real iron ore using only ELN electrical input. Published pipe, mounted gate, quarry and all 254 actual atlas references were inspected. A normal production two-converter MJ/electrical loop performed work while its seeded energy decreased.

| Original interaction or modern equivalent | Native adapter | Executed verification |
| --- | --- | --- |
| Original BuildCraft fuel heating-value entry | Device-specific qualified BCCE fuel filters and finite Forge fluid tanks | Actual tank and wooden fluid pipe feeding gasoline generators, liquid heater and turbine/engine assemblies |
| Original RF energy-converter export | Directional Forge Energy plus optional BCCE native MJ provider | Actual ELN power drove the published quarry through frame construction and iron excavation |
| Modern BCCE MJ import equivalent | Native MJ receiver, one fractional buffer and power-limited MNA source | Published creative engine powered an ELN resistor; actual engine removal stopped transfer |
| Generic original inventory automation | Sided Forge item handlers and recipe/output validation | Real wooden item pipes supplied input and extracted finished output without stealing raw input |
| Generic original fluid automation | Filtered Forge tanks, sided access and saved fractional fuel | Actual BCCE fuel and steam pipe assemblies, tank reload and a complete finite coal-to-electric plant |
| Optional dependency and conversion safety | Version-gated BCCE adapter; independent native FE operation | Full native and combined suites, normal JAR restarts, shared transfer budgets and lossy real MJ round trip |
| Legacy IC2/OpenComputers-specific runtimes | Tested FE/BCCE energy adapters and optional CC: Tweaked probe | Those legacy runtimes themselves are not claimed as modern tested integrations |

Both conversion directions share one fractional buffer and per-tick transfer budgets. Simulated requests leave it untouched; mode and enabled state restrict capability direction. Native MJ uses BCCE's own micro-MJ conversion service rather than treating one micro-MJ as one FE. The default FE scale is 1 FE/J with 0.95 conversion efficiency per direction. Tests cover repeated calls, fractional remainders, persistence and physical round-trip loss.

Fuel acceptance is per device. Gasoline generators retain their source-compatible qualified fuels; the liquid heater and turbine/engine adapters retain their own actual heating-value and fuel filters. Native steam production and heat exchange are finite and survive persistence. Fluid temperature/grade is not a complete independent chemical-fluid simulation.

Item/fluid machine capabilities operate without BCCE. The standalone native profile passed 271 GameTests and normal installed-JAR starts/restarts without it. The combined profile passed 286; these counts overlap. See VALIDATION.md and the original-bcce-factory/other device audits for exact scenarios and limits. Every BCCE machine, engine, gate program and other mod combination is not asserted.
