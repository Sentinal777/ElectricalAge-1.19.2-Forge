"""Inventory all source files and extract only provable literal registration IDs.

Non-literal/dynamic registrations are explicitly unresolved, never silently assigned IDs.
"""
from pathlib import Path
import re,json
PORT=Path(__file__).resolve().parents[1];ROOT=PORT.parent
inventory=[]
for p in (ROOT/'src/main').rglob('*'):
    if p.suffix not in ('.java','.kt'):continue
    s=p.read_text(encoding='utf-8');dest=PORT/p.relative_to(ROOT)
    inventory.append({'source':str(p.relative_to(ROOT)), 'lines':len(s.splitlines()),
      'native_counterpart_present':dest.is_file(), 'minecraft_imports':re.findall(r'^import ((?:net.minecraft|net.minecraftforge|cpw.mods)[\w.*]+)',s,re.M)})
entries=[]
for filename,family in [('SixNodeRegistration.kt','six'),('TransparentNodeRegistration.kt','transparent'),('ItemRegistration.kt','shared')]:
    p=ROOT/'src/main/kotlin/mods/eln/registration'/filename;s=p.read_text(encoding='utf-8')
    groupcalls={m[1]:int(m[2]) for m in re.finditer(r'\b(register\w+)\((\d+)\)',s)}
    # Include no-argument helpers as boundaries so they cannot inherit a previous group ID.
    funs=list(re.finditer(r'(?:private )?fun (register\w+)\(([^)]*)\)',s))
    for i,f in enumerate(funs):
        body=s[f.end():funs[i+1].start() if i+1<len(funs) else len(s)]
        group=groupcalls.get(f[1]) if f[2]=='id: Int' else None;sid=None
        tokens=re.finditer(r'subId\s*=\s*(\d+)\b|(?:I18N\.)?TR_NAME\([^,]+,\s*"([^"\n]+)"',body)
        for token in tokens:
            if token[1] is not None:sid=int(token[1]);continue
            name=token[2]
            tail=body[token.end():];nxt=re.search(r'subId\s*=|(?:I18N\.)?TR_NAME\(',tail)
            segment=tail[:nxt.start()] if nxt else tail
            if group is None or sid is None or not re.search(r'addDescriptor\(subId \+ \(id shl 6\)',segment):continue
            descriptor=re.search(r'\b(\w+Descriptor)\(',segment)
            model=re.search(r'getObj\("([^"]+)"\)',segment)
            entries.append({'family':family,'group':group,'sub_id':sid,'legacy_id':(group<<6)|sid,
              'name':name,'descriptor':descriptor[1] if descriptor else None,'model':model[1] if model else None,
              'source':filename,'line':s[:f.end()+token.start()].count('\n')+1})
(PORT/'docs/source-inventory.json').write_text(json.dumps(inventory,indent=2)+'\n')
(PORT/'docs/literal-registrations.json').write_text(json.dumps(entries,indent=2)+'\n')
print(f'{len(inventory)} source files, {sum(x["native_counterpart_present"] for x in inventory)} with a native counterpart; {len(entries)} literal registration candidates (not exhaustive).')
