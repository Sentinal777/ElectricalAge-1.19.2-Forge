"""Reconcile the pinned baseline manifests with the actual native catalog.

Discovery and validation evidence remain separate: matching an ID, registering an
item, or finding a name in a test is not itself proof of feature completeness.
"""
from pathlib import Path
import json
import re

PORT = Path(__file__).resolve().parents[1]
DOCS = PORT / 'docs'
BASELINE = json.loads((DOCS / 'baseline-literal-registrations.json').read_text(encoding='utf-8'))
NATIVE = json.loads((DOCS / 'native-content.json').read_text(encoding='utf-8'))
PARTS = NATIVE['parts']
MATERIALS = NATIVE['materials']
TESTS = [(p.relative_to(PORT).as_posix(), p.read_text(encoding='utf-8'))
         for folder in ('src/main/kotlin/mods/eln/modern/testing', 'src/qa/kotlin', 'src/ccTest/kotlin')
         for p in (PORT / folder).rglob('*.kt')]
RUNTIMES = [(p.relative_to(PORT).as_posix(), p.read_text(encoding='utf-8'))
            for p in (PORT / 'src/main/kotlin/mods/eln/modern').rglob('*Runtime.kt')]

def evidence(part):
    quoted = re.compile(r'["\']' + re.escape(part['id']) + r'["\']')
    return {
        'behavior': part.get('behavior', part.get('kind')),
        'nativeDefinition': 'src/main/resources/assets/eln/parts.json' if 'behavior' in part else 'src/main/resources/assets/eln/materials.json',
        'implementationReferences': [path for path, text in RUNTIMES
                                     if quoted.search(text) or 'Behavior.' + part.get('behavior', 'INVALID') in text],
        'testDiscoveryReferences': [path for path, text in TESTS if quoted.search(text)],
        'testDiscoveryCaveat': 'References identify candidate evidence; inspect the assertions and recorded run results. Dynamic family tests may cover IDs not written literally.'
    }

entries = []
matched = set()
for entry in BASELINE['entries']:
    row = dict(entry)
    matches = [p for p in PARTS if p['id'] == entry.get('native_id') or
               (p['family'] == entry['family'] and p['legacyId'] == entry['legacy_id'])]
    if len(matches) == 1:
        part = matches[0]
        matched.add(part['id'])
        row.update(native_id=part['id'], registrationStatus='implemented', evidence=evidence(part))
    elif entry['family'] == 'six' and entry['legacy_id'] == 8128:
        row.update(registrationStatus='excluded_development_prototype',
                   classification='Not technically practical to preserve as functional equipment',
                   reason='Baseline registers Conduit only during isDevelopmentRun; its original element exposes no electrical or thermal loads. No survival functionality exists to reproduce.',
                   sourceEvidence=['SixNodeRegistration.kt:122', 'sixnode/ConduitCableDescriptor.kt'])
    elif entry['family'] == 'six' and entry['legacy_id'] == 8000:
        row.update(registrationStatus='diagnostic_replaced',
                   classification='Intentionally replaced by equivalent functionality',
                   reason='Original creative Portable NaN deliberately injects NaN resistance, temperature and heat into the simulation. Numerical fault-injection unit tests replace the world-corrupting debug item; it is not survival equipment.',
                   sourceEvidence=['sixnode/PortableNaN.kt', 'SixNodeRegistration.kt:1589'],
                   testEvidence=['src/test/kotlin/mods/eln/sim/mna/component/ComponentTypesTest.kt',
                                 'src/test/kotlin/mods/eln/sim/mna/SubSystemMatrixTest.kt',
                                 'src/test/kotlin/mods/eln/sim/mna/process/PowerSourceBipoleTest.kt'])
    else:
        row.update(registrationStatus='unresolved', classification='Missing')
    entries.append(row)

shared = json.loads((DOCS / 'baseline-shared-item-registrations.json').read_text(encoding='utf-8'))
tools = {'multimeter': 'Content.multimeter', 'thermometer': 'Content.thermometer', 'wrench': 'Content.wrench'}
items = []
for entry in shared['entries']:
    row = dict(entry)
    part = next((p for p in MATERIALS + PARTS if p['id'] == entry.get('native_id')), None)
    if part:
        row.update(registrationStatus='implemented', evidence=evidence(part))
    elif entry.get('native_id') in tools:
        row.update(registrationStatus='implemented', implementation=tools[entry['native_id']])
    else:
        row.update(registrationStatus='unresolved', classification='Missing')
    items.append(row)

report = {
    'baseline': BASELINE['baseline'], 'commit': BASELINE['commit'],
    'status': 'Pinned-source registration reconciliation; functional verification is reported separately',
    'scope': 'Comment-stripped literal component registrations and shared item-name discoveries, supplemented by all native dynamic definitions. Source conditionals and debug-only equipment are identified explicitly.',
    'nativeCounts': {'components': len(PARTS), 'materials': len(MATERIALS)},
    'baselineComponents': entries, 'baselineSharedItemDiscovery': items,
    'additionalAndDynamicNativeComponents': [dict(id=p['id'], family=p['family'], legacyId=p['legacyId'], evidence=evidence(p)) for p in PARTS if p['id'] not in matched],
    'inactiveOrPostBaseline': {
        'Under Track Power': 'The 1.24.6 registration block is commented out in TransparentNodeRegistration.kt:190.',
        '240V Lamp Supply; Wire Roller/Insulator/Combiner; One-way DC converters; Isolation Transformer; LargeFlywheel': 'Names from the recovered later development registration catalog; absent from the pinned 1.24.6 baseline. They are not active baseline omissions.'
    },
    'limitations': ['CC: Tweaked restores the ComputerCraft probe on the target version. Legacy OpenComputers 1.7-only runtime is not claimed as a tested modern integration.',
                    'Development Conduit and the deliberate NaN debug item are accounted for above.',
                    'Per-device behavior audits and actual run logs remain the functional-validation evidence; registration reconciliation alone is insufficient.']
}
(DOCS / 'baseline-coverage-reconciliation.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
unresolved = [e for e in entries + items if e['registrationStatus'] == 'unresolved']
print(f"Pinned baseline: {len(entries)} component registrations; {len(items)} shared-item discoveries; {len(PARTS)} native components; {len(MATERIALS)} native materials.")
print(f"Unresolved registration mappings: {len(unresolved)}; original development/debug exclusions: 2.")
if unresolved:
    print(json.dumps(unresolved, indent=2))
    raise SystemExit(1)
