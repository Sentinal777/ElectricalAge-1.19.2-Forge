"""Reviewed dynamic loops from CraftingRecipes.kt 1.24.6; preserve source line evidence."""
from copy import deepcopy

def expand(records):
    rows=[];redirected=[]
    def replaced(value,bindings):
        if isinstance(value,dict):
            if value.get('expression') in bindings:return deepcopy(bindings[value['expression']])
            return {k:replaced(v,bindings) for k,v in value.items()}
        if isinstance(value,list):return [replaced(v,bindings) for v in value]
        return value
    def present(tag):return {'type':'forge:not','value':{'type':'forge:tag_empty','tag':tag}}
    for row in records:
        line=row['line']
        if line in (190,194):
            redirected.append({'line':line,'implementation':'generate_content.py craftBrush wool-tag collapse and sixteen original dye variants, with life=0 empty brush'})
            continue
        if line in (786,787,810,814,818):
            for metal in ('Aluminum','Aluminium','Steel'):
                copy=replaced(row,{'oreName':{'ore':'ingot'+metal},'ingotType':{'ore':'ingot'+metal},'blockType':{'ore':'block'+metal}})
                copy['variant']=metal.lower();copy['conditions']=[present('forge:'+('ingots/' if line in (786,787) else 'storage_blocks/')+metal.lower())]
                rows.append(copy)
        elif line==793:
            copy=deepcopy(row);copy['conditions']=[{'type':'forge:tag_empty','tag':'forge:ingots/'+metal} for metal in ('aluminum','aluminium','steel')];rows.append(copy)
        elif line==926:
            for metal in ('blockSteel','blockAluminum','blockAluminium','casingMachineAdvanced'):
                for panel in ('Small Solar Panel','Small Rotating Solar Panel'):
                    copy=replaced(row,{'findItemStack(panel)':{'elnName':panel,'count':1},'metal':{'ore':metal}})
                    copy['variant']=metal.lower()+'_'+('rotating' if 'Rotating' in panel else 'fixed');rows.append(copy)
        elif line==2471:
            copy=deepcopy(row);copy['result']={'elnName':'Computer Probe','count':1};rows.append(copy)
        elif line==2461:
            copy=deepcopy(row);copy['result']={'elnName':'Energy Converter','count':1};rows.append(copy)
        else:rows.append(row)
    return rows,redirected
