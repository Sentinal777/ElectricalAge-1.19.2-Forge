# Electrical Age 1.19.2-0.4.0-port

Minecraft 1.19.2, Forge 43.5.0 exactly, Java 17. This release implements the original active Electrical Age 1.24.6 equipment families in a separate native Forge project. The parity reference is tag 1.24.6, commit 17f99f7e2dd6055527bb43578af66f1d62c6842e. It preserves the original engineering simulation and does not replace it with a generic energy network.

The recovered 0.2 source was reused; the recovered originals and previous 0.3 preview remain preserved. The later development catalog is not confused with the pinned baseline. 177 literal baseline registrations reconcile to 175 implemented active components plus the development Conduit and Portable NaN debug tool; 129 shared-item name discoveries reconcile without unresolved mappings. Dynamic definitions bring the native catalog to 227 components and 172 materials. Counts alone are not a completeness score.

**Electrical simulation and distribution.** Original MNA components, dynamic R/C/L, sources, separate terminals, loss/thermal failure, six-face multipart topology, original C-key covers with live internal circuits, actual overhead grids and transformer/pole/switch assemblies.

**Generation and storage.** All seven source batteries; four solar variants and tracking/ghost terminals; wind/water, combustion, heat/steam/shaft generation and finite portable storage.

**Machines, mechanical power and fluids.** Original processing inventories/boosters, furnaces, thermal networks and rooms, heat exchanger/steam, shafts/clutches/flywheels/large machines, engines, chargers, miners, replicator, incubator, transporter and turret.

**Electronics and automation.** Original logic/analog/equations, sensors and controls, diodes/passives, fuse/breaker contacts, wireless/data loggers/meters/displays, MQTT, Modbus TCP and optional actual CC Lua peripheral.

**Rendering and interaction.** Original OBJ/textures/icons, mounting frames, moving machinery/instruments, per-component screens and authoritative packets, lighting, audio and held models. Detailed per-family audits identify actual reviewed client runs.

**Survival and resources.** Four original ores, original materials/tools/armor/resin/crops and contained hardware. 349 restored crafting variants with zero unresolved source crafting calls; 61 direct and 5 conditional processing operations independently checked.

**BuildCraft interoperability.** Published BCCE engines, real item/fluid pipes, finite fuel/steam/shaft/electrical plant, bidirectional native MJ conversion and a real quarry constructing its frame and excavating iron ore from ELN electrical power.

The detailed component classifications and their actual validation scope are in feature-coverage.json/csv. Some calibration or installation variants share tested code without a separate working fixture; those remain conservatively marked implemented but untested. The original-device JSON audits record equations, source conditions, intentional corrections and executed evidence.

## Verification limits

The original development-only Conduit has no implemented source electrical/thermal function. It is excluded from ordinary equipment; the original deliberate Portable NaN debug item is replaced by isolated numerical fault-injection tests.

Legacy OpenComputers 1.7 APIs and IC2-only integration are not advertised as modern tested runtimes. The ComputerCraft probe is restored through optional CC: Tweaked 1.101.4; FE and the pinned BCCE native MJ bridge provide the tested energy adapters.

Original C-key node covers accept full collision-cube block models, including logs, glass, leaves and light-emitting blocks. Block entities, fluids and partial collision shapes are excluded. An unavailable mod cover retains its raw saved state and item data with a stone visual fallback.

Source creative prototypes remain identified as such. Registration counts and source recipe counts are not independent proof of functional completeness.

BCCE native linkage is pinned to published 8.0.23. Other BCCE versions and every engine/gate/machine combination are unverified. BCCE is not modified or embedded.

Client verification used an isolated integrated server and a QA-only host pacing agent. The agent and QA sources are excluded from the normal installable JAR. This is automated client testing, not a long manual survival/multiplayer campaign.

The component matrix distinguishes individual operating fixtures from implemented variants that share a tested runtime but have not been separately exercised. Broad modpack compatibility and every original localization/configuration option are not asserted.

Historical 0.3 status reports remain under docs/history. Their old missing-system descriptions do not describe this release. No personal Minecraft installation or world was modified.
