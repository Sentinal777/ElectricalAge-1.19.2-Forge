"""Write current release reports only from completed in-game and production evidence.

The historical 0.3 reports are retained under docs/history. Missing evidence fails
this command before it can replace the current reports with a completion claim.
"""
from pathlib import Path
import argparse
import csv
import json
import re
import xml.etree.ElementTree as ET

PORT=Path(__file__).resolve().parents[1]
DOCS=PORT/'docs'
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--full-log',required=True,type=Path)
parser.add_argument('--native-log',required=True,type=Path)
parser.add_argument('--production-result',required=True,type=Path,action='append')
parser.add_argument('--version',default='1.19.2-0.4.0-port')
args=parser.parse_args()

def passed(path):
    text=path.read_text(encoding='utf-8')
    counts=re.findall(r'All (\d+) required tests passed',text)
    assert counts and 'BUILD SUCCESSFUL' in text and 'failed!' not in text, f'Incomplete/failed evidence: {path}'
    return int(counts[-1])

full_count=passed(args.full_log)
native_count=passed(args.native_log)
assert full_count >= 286 and native_count >= 271, 'The final profiles must include the restored node-cover cases.'
units=[ET.parse(p).getroot() for p in (PORT/'build/test-results/test').glob('TEST-*.xml')]
assert units and all(int(t.get('failures','0'))+int(t.get('errors','0'))+int(t.get('skipped','0'))==0 for t in units)
unit_count=sum(int(t.get('tests','0')) for t in units)
assert unit_count >= 209
production=[json.loads(p.read_text(encoding='utf-8')) for p in args.production_result]
assert len(production)>=3 and all(p.get('actualWorldRestart') for p in production)
assert all(p.get('actualNodeCoverSaveRestart') for p in production)
assert any(not p['optionalMods'] for p in production)
assert any(p.get('actualEngineLoadJ',0)>0 for p in production)
assert any(p.get('normalInstalledCcLuaAllNineMethods') for p in production)
assert len({p['sha256'] for p in production})==1, 'Production profiles tested different JARs'
assert all(p.get('measuredTicksPerSecond',0) >= 18 for p in production)
assert all(p.get('worldGeneration', {}).get('chunksScanned', 0) > 0 for p in production)
production_tps=', '.join(f'{p["measuredTicksPerSecond"]:.2f}' for p in production)

native=json.loads((DOCS/'native-content.json').read_text(encoding='utf-8'))
reconciliation=json.loads((DOCS/'baseline-coverage-reconciliation.json').read_text(encoding='utf-8'))
tests=[(p.relative_to(PORT).as_posix(),p.read_text(encoding='utf-8')) for folder in ('src/main/kotlin/mods/eln/modern/testing','src/qa/kotlin','src/ccTest/kotlin') for p in (PORT/folder).rglob('*.kt')]
rows=[]
for p in native['parts']:
    expression=re.compile(r'["\']'+re.escape(p['id'])+r'["\']')
    direct=[name for name,text in tests if expression.search(text) and not any(word in name for word in ('Crafting','ResourceCompletion','SourceProcessing'))]
    if p.get('powerAntenna'):
        direct.append('src/main/kotlin/mods/eln/modern/testing/PowerAntennaGameTests.kt: allSixAntennasUseActualCommandedInputIndependentFeedbackAndFiniteReceiverEnergy')
    if p['id'] in ('current_source','creative_cable'):
        direct.append('tools/validate_production.py: actual installed JAR, 1 A source, 100 ohm load, disable and 256-node mesh')
    rows.append(dict(native_id=p['id'],original_name=p['name'],family=p['family'],legacy_id=p['legacyId'],behavior=p['behavior'],
                     classification='Fully functional and tested' if direct else 'Implemented but untested',
                     verification_scope='Named actual-world/client fixture; see referenced assertions and logs. This is not exhaustive testing of every configuration.' if direct else 'Original definition and shared runtime implemented; a separate operating fixture for this installation/calibration variant has not been established by literal test discovery.',
                     evidence='; '.join(sorted(set(direct)))))

limitations=[
 'The original development-only Conduit has no implemented source electrical/thermal function. It is excluded from ordinary equipment; the original deliberate Portable NaN debug item is replaced by isolated numerical fault-injection tests.',
 'Legacy OpenComputers 1.7 APIs and IC2-only integration are not advertised as modern tested runtimes. The ComputerCraft probe is restored through optional CC: Tweaked 1.101.4; FE and the pinned BCCE native MJ bridge provide the tested energy adapters.',
 'Original C-key node covers accept full collision-cube block models, including logs, glass, leaves and light-emitting blocks. Block entities, fluids and partial collision shapes are excluded. An unavailable mod cover retains its raw saved state and item data with a stone visual fallback.',
 'Source creative prototypes remain identified as such. Registration counts and source recipe counts are not independent proof of functional completeness.',
 'BCCE native linkage is pinned to published 8.0.23. Other BCCE versions and every engine/gate/machine combination are unverified. BCCE is not modified or embedded.',
 'Client verification used an isolated integrated server and a QA-only host pacing agent. The agent and QA sources are excluded from the normal installable JAR. This is automated client testing, not a long manual survival/multiplayer campaign.',
 'The component matrix distinguishes individual operating fixtures from implemented variants that share a tested runtime but have not been separately exercised. Broad modpack compatibility and every original localization/configuration option are not asserted.'
]
families=[
 ('Electrical simulation and distribution','Original MNA components, dynamic R/C/L, sources, separate terminals, loss/thermal failure, six-face multipart topology, original C-key covers with live internal circuits, actual overhead grids and transformer/pole/switch assemblies.'),
 ('Generation and storage','All seven source batteries; four solar variants and tracking/ghost terminals; wind/water, combustion, heat/steam/shaft generation and finite portable storage.'),
 ('Machines, mechanical power and fluids','Original processing inventories/boosters, furnaces, thermal networks and rooms, heat exchanger/steam, shafts/clutches/flywheels/large machines, engines, chargers, miners, replicator, incubator, transporter and turret.'),
 ('Electronics and automation','Original logic/analog/equations, sensors and controls, diodes/passives, fuse/breaker contacts, wireless/data loggers/meters/displays, MQTT, Modbus TCP and optional actual CC Lua peripheral.'),
 ('Rendering and interaction','Original OBJ/textures/icons, mounting frames, moving machinery/instruments, per-component screens and authoritative packets, lighting, audio and held models. Detailed per-family audits identify actual reviewed client runs.'),
 ('Survival and resources','Four original ores, original materials/tools/armor/resin/crops and contained hardware. 349 restored crafting variants with zero unresolved source crafting calls; 61 direct and 5 conditional processing operations independently checked.'),
 ('BuildCraft interoperability','Published BCCE engines, real item/fluid pipes, finite fuel/steam/shaft/electrical plant, bidirectional native MJ conversion and a real quarry constructing its frame and excavating iron ore from ELN electrical power.')
]
report=dict(version=args.version,reference=reconciliation['commit'],designation='Functional original Electrical Age port with explicit verification limits',
            native_parts=len(native['parts']),native_materials=len(native['materials']),
            tests=dict(unit=unit_count,withBcceAndCc=full_count,withoutOptionalMods=native_count,
                       fullLog=args.full_log.name,nativeLog=args.native_log.name),
            classificationPolicy='Implemented variants are not silently counted as individually exercised. Read verification_scope; a label is not a promise about every possible setup.',
            components=rows,baselineExceptions=[p for p in reconciliation['baselineComponents'] if p['registrationStatus']!='implemented'],
            native_materials_detail=native['materials'],families=[dict(family=a,implementation=b) for a,b in families],limitations=limitations,
            productionProfiles=production)
(DOCS/'feature-coverage.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
(DOCS/'test-results.json').write_text(json.dumps(dict(
    version=args.version, status='Executed final release validation',
    unit=dict(tests=unit_count,failures=0,errors=0,skipped=0),
    minecraftWithoutOptionalMods=dict(passed=native_count,failed=0,log=args.native_log.name),
    minecraftWithPublishedBcceAndCc=dict(passed=full_count,failed=0,log=args.full_log.name),
    overlappingProfilesAreNotSummed=True, productionProfiles=production,
    client=dict(actualIntegratedServer=True,automatedClientChecks=True,qaPacingAgentExcludedFromNormalJar=True,
                manualSurvivalCampaign=False,evidence='Per-device original-*-audit.json files and recorded graphical logs.'),
    notClaimed=['Long manual survival playthrough','Broad multiplayer/modpack campaign','Every installation/calibration variant','Unverified optional-mod versions']
),indent=2)+'\n',encoding='utf-8')
progress=json.loads((DOCS/'development-0.4.json').read_text(encoding='utf-8'))
progress['status']='Release implementation and normal-runtime validation complete; see packaged source-build verification for independent reproducibility.'
progress['fullRegression']=dict(log=args.full_log.name,minecraftTests=full_count,unitTests=unit_count,result='All required tests passed with published BCCE and CC.')
progress['nativeRegression']=dict(log=args.native_log.name,minecraftTests=native_count,unitTests=unit_count,result='All required tests passed without optional mods.')
progress['latestFullAttempt']=progress['fullRegression']
progress['verified']['lastFullAttempt']=f'All {full_count} Minecraft and {unit_count} unit tests passed: {args.full_log.name}'
progress['verified']['unit_tests']=unit_count
progress['immediate_work']=[]
progress['remaining_verification_limits']=limitations
(DOCS/'development-0.4.json').write_text(json.dumps(progress,indent=2)+'\n',encoding='utf-8')
signals=json.loads((DOCS/'original-simple-signal-audit.json').read_text(encoding='utf-8'))
signals['status']='Focused actual Minecraft, reviewed singleplayer client and final full/native regressions passed.'
signals['fullRegressionNote']=f'Final {full_count}-case BCCE/CC profile and {native_count}-case standalone native profile passed, including corrected original switch/source fixtures, light-engine readiness and original CC receiver discovery timing.'
(DOCS/'original-simple-signal-audit.json').write_text(json.dumps(signals,indent=2)+'\n',encoding='utf-8')
for audit_path in DOCS.glob('original-*-audit.json'):
    audit=json.loads(audit_path.read_text(encoding='utf-8'))
    audit['finalRegression']=dict(fullLog=args.full_log.name,withBcceAndCc=full_count,
                                  nativeLog=args.native_log.name,withoutOptionalMods=native_count,unit=unit_count,
                                  scope='All required cases passed. Existing per-device focused/client evidence and its limits remain authoritative; this field does not claim every configuration was exercised.')
    old_status=audit.get('status','')
    if any(word in old_status.lower() for word in ('next','pending','running','in progress')):
        audit['historicalCheckpointStatus']=old_status
        audit['status']='Implemented; recorded focused/client evidence retained and final full/native regressions passed. See the stated verification scope.'
    audit_path.write_text(json.dumps(audit,indent=2)+'\n',encoding='utf-8')
with (DOCS/'feature-coverage.csv').open('w',newline='',encoding='utf-8') as f:
    writer=csv.DictWriter(f,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)

def write(name,text):
    (DOCS/name).write_text(text.strip()+'\n',encoding='utf-8')

write('PORT_STATUS.md',f'''# Electrical Age {args.version}

Minecraft 1.19.2, Forge 43.5.0 exactly, Java 17. This release implements the original active Electrical Age 1.24.6 equipment families in a separate native Forge project. The parity reference is tag 1.24.6, commit 17f99f7e2dd6055527bb43578af66f1d62c6842e. It preserves the original engineering simulation and does not replace it with a generic energy network.

The recovered 0.2 source was reused; the recovered originals and previous 0.3 preview remain preserved. The later development catalog is not confused with the pinned baseline. 177 literal baseline registrations reconcile to 175 implemented active components plus the development Conduit and Portable NaN debug tool; 129 shared-item name discoveries reconcile without unresolved mappings. Dynamic definitions bring the native catalog to {len(native['parts'])} components and {len(native['materials'])} materials. Counts alone are not a completeness score.

'''+ '\n\n'.join('**'+a+'.** '+b for a,b in families)+'''

The detailed component classifications and their actual validation scope are in feature-coverage.json/csv. Some calibration or installation variants share tested code without a separate working fixture; those remain conservatively marked implemented but untested. The original-device JSON audits record equations, source conditions, intentional corrections and executed evidence.

## Verification limits

'''+ '\n\n'.join(limitations)+'''

Historical 0.3 status reports remain under docs/history. Their old missing-system descriptions do not describe this release. No personal Minecraft installation or world was modified.
''')

write('VALIDATION.md',f'''# Executed verification for {args.version}

Java 17 / Minecraft 1.19.2 / Forge 43.5.0. {unit_count} unit tests passed without failures, errors or skipped tests. The full published-BCCE/CC run passed {full_count} required Minecraft GameTests; the separate run without optional mods passed {native_count}. These overlapping profiles are not added together as independent tests.

Full log: {args.full_log.name}. Native log: {args.native_log.name}. The per-device JSON audits identify the focused and graphical runs, including actual models, GUI packets, audio, finite energy, inventories, chunk lifecycle and removal. Source processing verification independently compares 61 direct and 5 conditional operations; 349 crafting variants have no unresolved source crafting calls.

Actual singleplayer checks include original MQTT/Modbus instruments, the nine-method CC probe, Tutorial Sign file/overlay, controls/sensors/diodes, C-key cover placement and cover-only mining, and the published BCCE quarry constructing its frame and excavating placed iron ore using only ELN-generated electrical/MJ input. Cover checks include transparent/tinted/emissive models, real light and exact survival item conservation. All 254 published BCCE sprite holders and actual atlas references resolved to real textures. The upstream BCCE 194-holder warning is retained; no BCCE patch is delivered.

## Normal installable JAR

Fresh standalone Forge servers loaded the normal reobfuscated Jar-in-Jar JAR, without development class substitution, QA agent or client-smoke classes. Separate profiles cover ELN alone, BCCE, and optional CC. Each executed an analytical circuit, actual disconnection, finite FE/battery behavior, save/stop/restart, exact covered-block state/item persistence and a 256-cable loaded mesh. Optional profiles additionally exercise a published engine, a lossy physical MJ round trip and actual Lua on the normally installed probe. Production results and real tick profiles are included in feature-coverage.json and the evidence bundle. These are measurements of this host, not a hardware-independent performance guarantee.

The installed-JAR mesh initially exposed a 4.26 TPS bottleneck; a saved-world JVM recording confirmed 4.13 TPS and concentrated CPU in repeated MNA solves. The precision-preserving solver and exact cable-geometry caches raised the three production profiles to {production_tps} TPS in their actual 15-second windows. Minecraft's nominal rate remains 20 Hz; short values above 20 include catch-up ticks. No physical subdivisions or controller iterations were reduced. Exact-decimal, original copper-reference, exceptional-value and analytical circuit checks validate the numerical changes. Disabled batteries retain a finite 1e12-ohm off branch, so the restart check exposes its minute ongoing charge drift and bounds it to 1e-10 charge fraction while requiring exact stopped FE restoration.

Release packaging updates the bundled reports after those runs. Final-artifact.json records the delivered JAR hash and the SHA-256 of its sorted runtime entries, excluding documentation and ZIP metadata. Packaging verifies that this runtime payload matches each normally installed production JAR; the independent source-build report verifies it again against the delivered artifact.

Client runs used the isolated integrated server and the excluded host pacing agent. Manual mouse/keyboard survival and broad multiplayer/modpack campaigns are not claimed. See PORT_STATUS.md and the component matrix for remaining verification limits.
''')

write('GETTING_STARTED.md',f'''# Windows 11 installation and build

Use Minecraft 1.19.2 with Forge 43.5.0 exactly and Java 17. Create a new game directory for the first installation. Keep world backups before changing any mod version.

1. Install the 1.19.2 Forge 43.5.0 client from https://files.minecraftforge.net/net/minecraftforge/forge/index_1.19.2.html . Select its profile and a separate game directory.
2. Place ElectricalAge-1.19.2-0.4.0.jar in that directory's mods folder. Install the complete Jar-in-Jar artifact; the thin and sources JARs are not standalone runtime mods. Kotlin and Commons Numbers are already embedded.
3. Optional BuildCraft: install BCCE 8.0.23 for Forge 1.19.2, CurseForge file 9035804: https://www.curseforge.com/minecraft/mc-mods/buildcraft-community-edition/files/9035804 . It is a separate download and is not required to run ELN.
4. Optional computers: install CC: Tweaked 1.101.4 for Forge 1.19.2, file 5005332: https://www.curseforge.com/minecraft/mc-mods/cc-tweaked/files/5005332 . Computer Probe exposes the ElnProbe peripheral and the original nine signal/wireless methods.
5. Start a new test world. Begin with a battery or source, correctly rated cable, a load and a closed return. Use the wrench for orientation and individual equipment removal; use meters for live measurements. Fit original hardware, bulbs, coils, fuses and machine upgrades in their component screens. Original survival recipes are supplied as datapacks.

The Energy Converter selects electrical-to-FE/MJ export or FE/MJ-to-electrical import. Both directions use finite stored energy and losses. MQTT and Modbus networking are disabled by default in config/eln-common.toml; MQTT endpoints are listed separately in config/eln-mqtt.json. Enable and configure them deliberately if needed. The Tutorial Sign reads EA/tutorialSign.txt in that world's folder.

To disguise a surface-component node, hold C (configurable in Controls) and right-click it with a supported full block. Survival uses one block. The first normal break removes only the cover and leaves the live components; a later break removes equipment. Glass shows the components inside. Block entities, fluids and partial collision shapes cannot be used as covers.

## Build

Extract the complete source ZIP, set JAVA_HOME to JDK 17, and open PowerShell in ElectricalAge-development/forge-1.19.2. Run .\\gradlew.bat build. The first build requires Internet access for Gradle 8.8, ForgeGradle 6.0.54, Kotlin and the pinned compile-only BCCE/CC APIs. Later cached builds can use --offline. The legacy root wrapper is unchanged and is not the native port's build entry point.

Install build/libs/electrical-age-{args.version}-all.jar. Normal builds embed Kotlin/Commons Numbers and exclude BCCE, CC and QA code. For tests use .\\gradlew.bat runGameTestServer; add -PbcceIntegration and -PccIntegration for the optional published-mod profile. Inspect actual GameTest completion lines as well as the process result.

Do not use -PclientSmoke, -PlightDiagnostic, -PbcceIntegration or -PccIntegration when producing a normal release artifact. Build normally again after those QA runs. The original-device audits and tools/validate_production.py describe the isolated reproduction checks. Runtime worlds, credentials and caches are excluded from the source archive.
''')

write('BUILDCRAFT_COMPATIBILITY.md',f'''# BuildCraft Community Edition interoperability

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

Item/fluid machine capabilities operate without BCCE. The standalone native profile passed {native_count} GameTests and normal installed-JAR starts/restarts without it. The combined profile passed {full_count}; these counts overlap. See VALIDATION.md and the original-bcce-factory/other device audits for exact scenarios and limits. Every BCCE machine, engine, gate program and other mod combination is not asserted.
''')

write('CHECKPOINT.md',f'''# Release checkpoint: {args.version}

The complete modified source, original resources and licenses, exact 1.24.6 reference, wrappers, generated data, source-level audits and validation tools are preserved in the source deliverable. Previous recovery artifacts and the 0.3 preview are retained separately. Work is not left only in a temporary runtime folder.

Current equipment families and explicit verification limits are in PORT_STATUS.md and feature-coverage.json/csv. All required tests in the recorded full/native profiles passed; normal production profiles include actual world restarts and a 256-cable loaded mesh. No known compile blocker remains on the pinned target. First build needs dependencies; cached builds can use --offline.

Further maintenance should retain stable native/legacy IDs, use the exact source baseline, preserve finite physical state through save/pickup/chunk lifecycle, and validate the normal JAR after any API change. Additional manual survival/multiplayer/modpack campaigns, unexercised installation variants, legacy optional integration replacements and localization/configuration parity remain useful verification or compatibility work; no missing major active baseline equipment family is hidden behind registration counts.

The historical 0.3 audit is retained under docs/history and must not overwrite current results. tools/audit_coverage.py now delegates to pinned-baseline reconciliation. Per-device behavior audits and executed logs remain separate from registration discovery. No future scheduled work is created by this release.
''')
print(json.dumps(dict(version=args.version,fullMinecraft=full_count,nativeMinecraft=native_count,unit=unit_count,components=len(rows),individuallyExercised=sum(r['classification']=='Fully functional and tested' for r in rows)),indent=2))
