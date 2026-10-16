"""Convert legacy resource paths and translations; preserve original attribution."""
from pathlib import Path
import json, re, shutil, hashlib

PORT=Path(__file__).resolve().parents[1]
ROOT=PORT.parent
SOURCE=ROOT/'src/main/resources/assets/eln'
TARGET=PORT/'src/main/resources/assets/eln'

def path_name(s):
    return re.sub(r'[^a-z0-9/._-]', '_', s.replace('\\','/').lower())

manifest=[]; models={}; destinations={}
for p in sorted(SOURCE.rglob('*')):
    if not p.is_file(): continue
    rel=p.relative_to(SOURCE)
    # Retain runtime resources here. The untouched source tree retains authoring files.
    if p.suffix.lower() not in {'.png','.ogg','.json','.obj','.mtl','.txt','.lang'}: continue
    if rel.parts[0]=='temp': continue
    if p.suffix=='.lang':
        entries={}
        for line in p.read_text(encoding='utf-8-sig').splitlines():
            if not line or line.startswith(('#','!')): continue
            pair=re.split(r'(?<!\\)=',line,maxsplit=1)
            if len(pair)!=2: continue
            def unescape(s):
                return re.sub(r'\\([nrt=:\\])',lambda m:{'n':'\n','r':'\r','t':'\t'}.get(m[1],m[1]),s)
            entries[unescape(pair[0])]=unescape(pair[1])
        target=TARGET/'lang'/(p.stem.lower()+'.json');target.parent.mkdir(parents=True,exist_ok=True)
        target.write_text(json.dumps(entries,ensure_ascii=False,indent=2)+'\n')
    else:
        target=TARGET/path_name(str(rel));target.parent.mkdir(parents=True,exist_ok=True)
        if target in destinations:
            if destinations[target]!=p.read_bytes():
                raise RuntimeError(f'Case-folding collision at {target}')
        destinations[target]=p.read_bytes()
        if p.suffix.lower() in {'.obj','.mtl'}:
            lines=[]
            for line in p.read_text(errors='replace').splitlines():
                if line.startswith(('mtllib ','map_Kd ')):
                    op,argument=line.split(None,1);line=op+' '+path_name(argument)
                lines.append(line)
            target.write_text('\n'.join(lines)+'\n')
        elif p.name=='sounds.json':
            data=json.loads(p.read_text())
            def convert(v):
                if isinstance(v,str): return 'eln:'+path_name(v)
                if isinstance(v,list): return [convert(x) for x in v]
                if isinstance(v,dict): return {k:convert(x) if k in ('name','sounds') else x for k,x in v.items()}
                return v
            converted={path_name(k):convert(v) for k,v in data.items()}
            for name,distance in [('alarma',48),('smallalarm_critical',48),('timer',4)]:
                for sound in converted[name]['sounds']:
                    if isinstance(sound,dict):sound['attenuation_distance']=distance
            target.write_text(json.dumps(converted,indent=2)+'\n')
        else: shutil.copy2(p,target)
        if p.suffix.lower()=='.obj': models[p.stem.lower()]=path_name(str(rel))
    manifest.append({'source':str(rel),'destination':str(target.relative_to(TARGET)), 'source_sha256':hashlib.sha256(p.read_bytes()).hexdigest()})

(TARGET/'model_index.json').write_text(json.dumps(models,indent=2)+'\n')
# A few upstream exporters wrote absolute workstation paths or stale relative paths.
# Repair unambiguous existing textures. Record unresolved paths instead of inventing artwork.
repairs=[];unresolved=[]
import os
for p in TARGET.rglob('*.mtl'):
    out=[]
    for line in p.read_text().splitlines():
        if line.startswith('map_Kd '):
            old=line[7:];candidate=(p.parent/old).resolve()
            if not candidate.is_file():
                basename=Path(old).name
                options=list(TARGET.rglob(basename))
                if basename=='incandescentlampcarbon.png':options=[TARGET/'textures/items/incandescentcarbonlamp.png']
                if len(options)>1 and len({hashlib.sha256(x.read_bytes()).hexdigest() for x in options})==1:options=options[:1]
                if len(options)==1:
                    line='map_Kd '+os.path.relpath(options[0],p.parent)
                    repairs.append({'material':str(p.relative_to(TARGET)),'old':old,'new':line[7:]})
                else:unresolved.append({'material':str(p.relative_to(TARGET)),'texture':old})
        out.append(line)
    p.write_text('\n'.join(out)+'\n')
(PORT/'docs/asset-path-repairs.json').write_text(json.dumps({'repaired':repairs,'unresolved':unresolved},indent=2)+'\n')
(PORT/'docs/assets-migration.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(f'Migrated {len(manifest)} runtime resources including {len(models)} OBJ models.')
