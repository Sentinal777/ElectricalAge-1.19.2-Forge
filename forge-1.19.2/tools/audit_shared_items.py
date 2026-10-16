"""Discover original shared-item registrations across all three registration sources.

The main item-name audit alone misses resources registered beside components,
including the five sacrificial fuses. Dynamic loops still need source review.
"""
from pathlib import Path
import json,re
PORT=Path(__file__).resolve().parents[1]
ROOT=PORT.parent/'reference-1.24.6'
if not ROOT.is_dir():ROOT=PORT.parents[1]/'upstream-1.24.6'
parts=json.loads((PORT/'src/main/resources/assets/eln/parts.json').read_text(encoding='utf-8'))
materials=json.loads((PORT/'src/main/resources/assets/eln/materials.json').read_text(encoding='utf-8'))
names={v['name']:v['id'] for v in parts+materials}
names.update({'MultiMeter':'multimeter','Thermometer':'thermometer','Wrench':'wrench'})
rows=[]
for filename in ('ItemRegistration.kt','SixNodeRegistration.kt','TransparentNodeRegistration.kt'):
    path=ROOT/'src/main/kotlin/mods/eln/registration'/filename
    source=path.read_text(encoding='utf-8')
    source=re.sub(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*[\s\S]*?\*/',lambda m:'\n'*m[0].count('\n') if m[0].startswith(('//','/*')) else m[0],source)
    literals=list(re.finditer(r'(?:I18N\.)?TR_NAME\(\s*I18N\.Type\.\w+\s*,\s*"([^"\n]+)"',source))
    for i,m in enumerate(literals):
        tail=source[m.end():literals[i+1].start() if i+1<len(literals)else len(source)]
        registration=re.search(r'(?:Eln\.)?sharedItem\.(addElement|addWithoutRegistry)\(',tail)
        if registration is None:continue
        rows.append(dict(name=m[1],native_id=names.get(m[1]),source=filename,line=source[:m.start()].count('\n')+1,hidden=registration[1]=='addWithoutRegistry'))
missing=[r for r in rows if not r['native_id']]
report=dict(baseline='1.24.6',commit='17f99f7e2dd6055527bb43578af66f1d62c6842e',note='Literal-name discovery across the three registration classes, including shared items registered beside blocks. Dynamic names, overloaded registrations, inactive functions and aliases still require source review; presence does not establish behavior.',entries=rows)
(PORT/'docs/baseline-shared-item-registrations.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Pinned literal shared-item candidates:',len(rows),'unmatched names:',len(missing))
for row in missing:print(row['source'],row['line'],row['name'],'(hidden)' if row['hidden']else '')
