"""Audit pinned 1.24.6 independently of the newer recovered development checkout.

Literal registrations only: dynamic loops still require the reviewed source/recipe audit.
"""
from pathlib import Path
import re,json

PORT=Path(__file__).resolve().parents[1]
ROOT=PORT.parent/'reference-1.24.6'
if not ROOT.is_dir():ROOT=PORT.parents[1]/'upstream-1.24.6'
def uncomment(source):
    pattern=r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/'
    return re.sub(pattern,lambda m: '\n'*m[0].count('\n') if m[0].startswith(('//','/*')) else m[0],source)
entries=[]
for filename,family in [('SixNodeRegistration.kt','six'),('TransparentNodeRegistration.kt','transparent')]:
    path=ROOT/'src/main/kotlin/mods/eln/registration'/filename
    source=uncomment(path.read_text(encoding='utf-8'))
    groups={m[1]:int(m[2]) for m in re.finditer(r'\b(register\w+)\((\d+)\)',source)}
    functions=list(re.finditer(r'(?:private )?fun (register\w+)\(([^)]*)\)',source))
    for i,function in enumerate(functions):
        body=source[function.end():functions[i+1].start() if i+1<len(functions) else len(source)]
        local_group=re.search(r'\b(?:val|var)\s+id\s*=\s*(\d+)\b',body) if not function[2].strip() else None
        group=groups.get(function[1]) if function[2]=='id: Int' else int(local_group[1]) if local_group else None
        if group is None:continue
        sub=None
        for token in re.finditer(r'subId\s*=\s*(\d+)\b|(?:I18N\.)?TR_NAME\([^,]+,\s*"([^"\n]+)"',body):
            if token[1] is not None:sub=int(token[1]);continue
            tail=body[token.end():];next_token=re.search(r'subId\s*=|(?:I18N\.)?TR_NAME\(',tail)
            segment=tail[:next_token.start()] if next_token else tail
            direct=re.search(r'addDescriptor\(subId \+ \(id shl 6\)',segment)
            assigned=re.search(r'completId\s*=\s*subId\s*\+\s*\(id shl 6\)',body) and re.search(r'addDescriptor\(completId\s*,',segment)
            if sub is None or not (direct or assigned):continue
            entries.append(dict(family=family,group=group,sub_id=sub,legacy_id=(group<<6)|sub,name=token[2],source=filename,
                line=source[:function.end()+token.start()].count('\n')+1))
parts=json.loads((PORT/'src/main/resources/assets/eln/parts.json').read_text(encoding='utf-8'))
native={(v['family'],v['legacyId']):v['id'] for v in parts}
for entry in entries:entry['native_id']=native.get((entry['family'],entry['legacy_id']))
report=dict(baseline='1.24.6',commit='17f99f7e2dd6055527bb43578af66f1d62c6842e',note='Comment-stripped literal component calls; loops and optional/inactive source branches require separate review.',entries=entries)
(PORT/'docs/baseline-literal-registrations.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
missing=[v for v in entries if not v['native_id']]
print('Pinned baseline literal components:',len(entries),'unmatched native IDs:',len(missing))
for entry in missing:print(entry['family'],entry['legacy_id'],entry['name'])
