"""Pinned literal item-name inventory. Loops, inactive branches and aliases require source review."""
from pathlib import Path
import json,re
PORT=Path(__file__).resolve().parents[1]
ROOT=PORT.parent/'reference-1.24.6'
if not ROOT.is_dir():ROOT=PORT.parents[1]/'upstream-1.24.6'
path=ROOT/'src/main/kotlin/mods/eln/registration/ItemRegistration.kt'
source=path.read_text(encoding='utf-8')
pattern=r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*[\s\S]*?\*/'
source=re.sub(pattern,lambda m: '\n'*m[0].count('\n') if m[0].startswith(('//','/*')) else m[0],source)
parts=json.loads((PORT/'src/main/resources/assets/eln/parts.json').read_text(encoding='utf-8'))
materials=json.loads((PORT/'src/main/resources/assets/eln/materials.json').read_text(encoding='utf-8'))
names={v['name']:v['id'] for v in parts+materials}
names.update({'MultiMeter':'multimeter','Thermometer':'thermometer','Wrench':'wrench'})
# Fold literal concatenation (the upstream Yellow Brush registration).
source=re.sub(r'"([^"\n]*)"\s*\+\s*"([^"\n]*)"',lambda m:'"'+m[1]+m[2]+'"',source)
entries=[]
for m in re.finditer(r'(?:I18N\.)?TR_NAME\(\s*I18N\.Type\.\w+\s*,\s*"([^"\n]+)"',source):
    entries.append(dict(name=m[1],native_id=names.get(m[1]),line=source[:m.start()].count('\n')+1))
missing=[v for v in entries if not v['native_id']]
report=dict(baseline='1.24.6',commit='17f99f7e2dd6055527bb43578af66f1d62c6842e',
    note='Literal TR_NAME calls in ItemRegistration.kt; exact name matching is a discovery aid, not proof of behavior or completed dynamic families. Includes inactive source functions and aliases.',entries=entries)
(PORT/'docs/baseline-literal-item-names.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'Pinned literal item names: {len(entries)}; unmatched names: {len(missing)}')
for v in missing:print(v['line'],v['name'])
