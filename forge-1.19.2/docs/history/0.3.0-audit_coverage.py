"""Conservative source coverage: registered counterparts never imply complete behavior."""
from pathlib import Path
import re,json,hashlib,csv,sys
PORT=Path(__file__).resolve().parents[1]
REF=Path(sys.argv[1]) if len(sys.argv)>1 else PORT.parent/'reference-1.24.6'
native=json.loads((PORT/'src/main/resources/assets/eln/parts.json').read_text(encoding='utf-8'))
materials=json.loads((PORT/'src/main/resources/assets/eln/materials.json').read_text(encoding='utf-8'))
by_id={(p['family'],p['legacyId']):p for p in native}
catalog=json.loads((PORT/'docs/literal-registrations.json').read_text(encoding='utf-8'))
rows=[]
for item in catalog:
    counterpart=by_id.get((item['family'],item['legacy_id']))
    rows.append(dict(original_name=item['name'],family=item['family'],legacy_id=item['legacy_id'],
        registration_source=item['source'],registration_line=item['line'],native_id=counterpart['id'] if counterpart else None,
        status='partially functional' if counterpart else 'missing',
        verification='See named executed tests; registration alone is not verification.' if counterpart else 'not executed'))
descriptors=[];references=[]
for p in (REF/'src/main').rglob('*'):
    if p.suffix not in ('.kt','.java'):continue
    s=p.read_text(encoding='utf-8')
    for m in re.finditer(r'\bclass\s+(\w+(?:Descriptor|Element))\b',s):
        descriptors.append({'class':m[1],'source':p.relative_to(REF).as_posix(),'line':s[:m.start()].count('\n')+1,
            'status':'requires source-specific parity validation'})
    for i,line in enumerate(s.splitlines(),1):
        if re.search(r'(?i)buildcraft|\bMJ\b|fuel|IFluidHandler',line):
            references.append({'source':p.relative_to(REF).as_posix(),'line':i,'text':line.strip()[:400]})
families=[
    ('electrical_solver','partial','Original MNA retained; current-source and regulated-source stamping repaired; analytical tests and world circuits executed.'),
    ('conductors_and_protection','partial','Nine electrical cable variants with restored 1.24.6 resistance/current ratings, voltage watchdog and heat calibration; switches and breakers. Voltage drop, overload burn-out and overvoltage tests executed. Grid poles, suspended grid cables, bundled wiring and covers missing.'),
    ('generation_and_storage','partial','Batteries and small solar retained; two fuel generators and shaft generator added. Wind/water and thermal electrical turbines missing.'),
    ('electronics_and_automation','partial','Previous logic/analog/wireless subset retained. Digital display independent value/dot/strobe inputs, range and dye restored and tested, including live GUI packet round trip. Full original sensors, programmable devices and external interfaces missing.'),
    ('processing_machines','partial','Furnace, macerators, compressors, electric rollers retained; mechanical rollers added. Arc furnace, miners, upgrades and most advanced processors missing.'),
    ('mechanical_power','partial','Joint, hub, flywheel, clutch, motor, generator and rollers added. Shaft energy equation, drag and merge friction retained; motor regulation differs. Gearbox/dual clutch/slip logic, crank, radial engines, fixed shafts, large machines and injury effects missing.'),
    ('fluid_and_thermal','partial','Persisted 2 L tanks, Forge fluid capabilities, fuel values and gas/steam turbine conversion. No independent pipe/tank family, steam production, heat exchanger, pumps or full original thermal network.'),
    ('multipart_and_rendering','partial','Original six-face carrier, original meshes and icons retained. Shaft meshes animate and axle pose matches graph ports. All 122 variants rendered in integrated-server gallery; converter/display real server menu controls exercised. Ghost blocks, full covers and some animation/GUI controls missing.'),
    ('survival_and_world_generation','partial','Four ores and Forge biome modifiers added from original rates/heights; modern vein sizes use original mean. All four found in 2025 newly generated QA chunks. Original full crafting progression, structures and all materials remain incomplete.'),
    ('buildcraft','partial','Optional 8.0.23 native MJ adapter and standard inventory/fluid capabilities. Exact test outcomes are recorded separately; unexecuted scenarios are not passed.'),
    ('persistence_and_networking','partial','Versioned component NBT retained; fractional FE, tank contents, shaft energy shares and topology grouping added. Mechanical energy/tank contents do not survive item pickup.'),
    ('tools_armor_entities_and_external_mods','missing','Most drills/scanners/armor, turret/vehicle families, IC2/ComputerCraft/OpenComputers and external protocol integrations remain missing.'),
]
report={'reference':{'release':'1.24.6','commit':'17f99f7e2dd6055527bb43578af66f1d62c6842e',
        'note':'Original exact reference inspected separately. Recovered main-tree registration metadata is used for previous-port ID continuity and is not an exhaustive runtime registry.'},
    'designation':'experimental partial port; not complete', 'native_parts':len(native),
    'additional_native_parts':[dict(p,status='partially functional',verification='See executed test report; dynamic registration IDs are not covered by the literal scanner.') for p in native if not any(r['native_id']==p['id'] for r in rows)],
    'native_materials':[dict(p,status='partially functional',verification='Registered with generated recipes; complete original material progression unverified.') for p in materials],
    'families':[{'family':a,'status':b,'limitations':c} for a,b,c in families],
    'registration_candidates':rows,'reference_descriptors':descriptors,'buildcraft_and_fluid_reference_hits':references,
    'reference_source_files':[{'path':p.relative_to(REF).as_posix(),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in (REF/'src/main').rglob('*') if p.suffix in ('.kt','.java')]}
(PORT/'docs/feature-coverage.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
with (PORT/'docs/feature-coverage.csv').open('w',newline='',encoding='utf-8') as f:
    writer=csv.DictWriter(f,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
print(f'Audited {len(rows)} literal registration candidates, {len(descriptors)} exact-reference descriptor/element classes and {len(report["reference_source_files"])} source files.')
