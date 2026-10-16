"""Emit resolved, original shaped/shapeless recipes and a precise unresolved audit."""
from pathlib import Path
import json,re,itertools

VANILLA={'golden_carrot':'golden_carrot','dye':'dye','reeds':'sugar_cane','slime_ball':'slime_ball','speckled_melon':'glistering_melon_slice','fire_charge':'fire_charge','gunpowder':'gunpowder','snowball':'snowball','netherbrick':'nether_brick','clay':'clay_ball','wooden_door':'oak_door','sign':'oak_sign','boat':'oak_boat','lit_pumpkin':'jack_o_lantern','lit_redstone_lamp':'redstone_lamp','redstone_torch':'redstone_torch','stonebrick':'stone_bricks','brick_block':'bricks','nether_brick':'nether_bricks','lapis_block':'lapis_block','quartz_block':'quartz_block','hardened_clay':'terracotta'}
VANILLA['noteblock']='note_block'
DYES=['ink_sac','red_dye','green_dye','cocoa_beans','lapis_lazuli','purple_dye','cyan_dye','light_gray_dye','gray_dye','pink_dye','lime_dye','yellow_dye','light_blue_dye','magenta_dye','orange_dye','bone_meal']
ALIASES={'50V Macerator':'48v_macerator','200V Macerator':'240v_macerator','50V Compressor':'48v_compressor','200V Compressor':'240v_compressor','50V Plate Machine':'48v_plate_machine','200V Plate Machine':'240v_plate_machine','50V Fuel Generator':'48v_fuel_generator','200V Fuel Generator':'240v_fuel_generator','50V Turbine':'50v_heat_turbine','200V Turbine':'200v_heat_turbine','On/OFF Regulator 1%':'on_off_regulator_1_percent','On/OFF Regulator 10%':'on_off_regulator_10_percent'}
ALIASES['itemRubber']='rubber'
DICT={'dictCheapChip':'cheap_chip','dictAdvancedChip':'advanced_chip','dictTungstenIngot':'tungsten_ingot','dictTungstenDust':'tungsten_dust','dictCopperIngot':'copper_ingot'}
DESCRIPTORS={'signalBusCableDescriptor':'signal_bus_cable','signalCableDescriptor':'signal_cable','lowVoltageCableDescriptor':'low_voltage_cable','meduimVoltageCableDescriptor':'medium_voltage_cable','highVoltageCableDescriptor':'high_voltage_cable','veryHighVoltageCableDescriptor':'very_high_voltage_cable','lowCurrentCableDescriptor':'low_current_cable','mediumCurrentCableDescriptor':'medium_current_cable','highCurrentCableDescriptor':'high_current_cable'}
STATIC_ITEMS={'helmetCopper':'copper_helmet','chestplateCopper':'copper_chestplate','legsCopper':'copper_leggings','bootsCopper':'copper_boots',
    'swordCopper':'copper_sword','pickaxeCopper':'copper_pickaxe','axeCopper':'copper_axe','shovelCopper':'copper_shovel','hoeCopper':'copper_hoe','arcClayBlock':'arc_clay_block','arcMetalBlock':'arc_metal_block'}

def restore(parts,materials,port,write):
    ids={d['id'] for d in parts+materials}|{'multimeter','thermometer','wrench','arc_clay_block','arc_metal_block'}
    names={d['name']:d['id'] for d in parts+materials};names.update({k:v for k,v in ALIASES.items() if v in ids})
    names.update({'MultiMeter':'multimeter','Thermometer':'thermometer','Wrench':'wrench'})
    for material in materials:
        m=re.fullmatch(r'(.+) (Ingot|Dust|Plate)',material['name'])
        if m:names[m[2].lower()+m[1].replace(' ','')]=material['id']
    # IDs retained from the recovered native build use the original literal names as aliases.
    catalog=json.loads((port/'docs/literal-registrations.json').read_text(encoding='utf-8'))
    by_legacy={(p['family'],p['legacyId']):p['id'] for p in parts}
    for old in catalog:
        target=by_legacy.get((old['family'],old['legacy_id']))
        if target:names[old['name']]=target
    records=json.loads((port/'tools/original_crafting.json').read_text(encoding='utf-8'))['recipes']
    from expand_crafting import expand
    records,redirected=expand(records)
    emitted=[];unresolved=[];used={};conditional=[]
    def ore(name):
        if name=='itemRubber':return {'tag':'forge:rubber'}
        if name=='materialString':return {'item':'minecraft:string'}
        if name=='resin':return {'item':'eln:tree_resin'}
        if name=='logWood':return {'tag':'minecraft:logs'}
        if name=='plankWood':return {'tag':'minecraft:planks'}
        if name=='stickWood':return {'tag':'forge:rods/wooden'}
        if name=='casingMachine':return {'item':'eln:machine_block'}
        if name=='casingMachineAdvanced':return {'item':'eln:advanced_machine_block'}
        for prefix,folder in [('ingot','ingots'),('dust','dusts'),('plate','plates'),('ore','ores'),('block','storage_blocks'),('gem','gems')]:
            if name.startswith(prefix):return {'tag':'forge:'+folder+'/'+name[len(prefix):].lower()}
        raise ValueError('unmapped original ore dictionary key: '+name)
    def ingredient(value):
        if 'elnName' in value:
            if value['elnName'] not in names:raise ValueError('missing original item: '+value['elnName'])
            return {'item':'eln:'+names[value['elnName']]}
        if 'ore' in value:return ore(value['ore'])
        if 'vanilla' in value:
            name=value['vanilla'];meta=value['metadata']
            if meta in ['32767','OreDictionary.WILDCARD_VALUE']:
                if name in ['log','log2']:return {'tag':'minecraft:logs'}
                if name=='planks':return {'tag':'minecraft:planks'}
                if name=='wool':return {'tag':'minecraft:wool'}
                raise ValueError('unmapped vanilla wildcard: '+name)
            if name=='dye':name=DYES[int(meta)]
            elif name=='sapling' and meta=='1':name='spruce_sapling'
            elif name=='coal' and meta=='1':name='charcoal'
            elif meta!='0':raise ValueError('unmapped vanilla metadata: '+name+'/'+meta)
            if name=='planks':name='oak_planks'
            if name=='log':name='oak_log'
            if name=='wool':name='white_wool'
            return {'item':'minecraft:'+VANILLA.get(name,name)}
        expression=value.get('expression','')
        m=re.fullmatch(r'ItemStack\(\s*(?:Items|Blocks)\.(\w+)\s*(?:,\s*\d+\s*(?:,\s*(-?\d+|OreDictionary.WILDCARD_VALUE)\s*)?)?\)',expression)
        if m:return ingredient({'vanilla':m[1],'metadata':m[2] or '0'})
        m=re.fullmatch(r'ItemStack\(Eln\.(\w+)\)',expression)
        if m and STATIC_ITEMS.get(m[1]) in ids:return {'item':'eln:'+STATIC_ITEMS[m[1]]}
        m=re.fullmatch(r'Eln\.instance\.(\w+)\.newItemStack\((\d*)\)',expression)
        if m and DESCRIPTORS.get(m[1]) in ids:return {'item':'eln:'+DESCRIPTORS[m[1]]}
        if expression.startswith('Eln.dict'):
            name=DICT.get(expression[4:])
            if name in ids:return {'item':'eln:'+name}
        m=re.fullmatch(r'Items\.(\w+)',expression)
        if m:return ingredient({'vanilla':m[1],'metadata':'0'})
        m=re.fullmatch(r'firstExistingOre\((.*)\)',expression)
        if m and re.fullmatch(r'\s*"[^"]+"(?:\s*,\s*"[^"]+")+\s*',m[1]):
            options=[ore(name) for name in re.findall(r'"([^"]+)"',m[1])]
            if any('tag' not in option for option in options):raise ValueError('non-tag original ore choice')
            key='choice'+str(len(conditional));conditional.append((key,options));return {'originalChoice':key}
        raise ValueError('unresolved original expression: '+expression)
    for row in records:
        if row.get('category','crafting')!='crafting':continue
        target_name=row['result'].get('elnName');target=names.get(target_name)
        result_count=row['result'].get('count',1)
        m=re.fullmatch(r'ItemStack\(Eln\.(\w+)\)',row['result'].get('expression',''))
        if m and STATIC_ITEMS.get(m[1]) in ids:target=STATIC_ITEMS[m[1]]
        m=re.fullmatch(r'Eln\.instance\.(\w+)\.newItemStack\((\d*)\)',row['result'].get('expression',''))
        if m and DESCRIPTORS.get(m[1]) in ids:target=DESCRIPTORS[m[1]];result_count=int(m[2] or '1')
        vanilla_result=None
        if 'vanilla' in row['result']:
            try:vanilla_result=ingredient(row['result'])['item'];target='original_'+vanilla_result.replace(':','_')
            except (ValueError,KeyError,IndexError):pass
        if not target:unresolved.append({'line':row['line'],'target':target_name,'reason':'target is not registered or requires dynamic expansion'});continue
        conditional.clear()
        try:
            if 'unresolvedArguments' in row:raise ValueError('dynamic or unsupported source arguments')
            result={'item':vanilla_result or 'eln:'+target,'count':result_count}
            if row['type']=='shaped':
                pattern=row['pattern'];keys={k:ingredient(v) for k,v in row['key'].items()}
                letters=set(''.join(pattern))-{' '};keys={k:v for k,v in keys.items() if k in letters}
                if not letters or letters-set(keys):raise ValueError('empty or incomplete original pattern')
                recipe={'type':'minecraft:crafting_shaped','pattern':pattern,'key':keys,'result':result}
            else:recipe={'type':'minecraft:crafting_shapeless','ingredients':[ingredient(v) for v in row['ingredients']],'result':result}
            if target in ['christmas_tree','holiday_candle','string_lights']:recipe={'type':'forge:conditional','recipes':[{'conditions':[{'type':'eln:festive_season'}],'recipe':recipe}]}
            if target=='x_ray_scanner':recipe={'type':'forge:conditional','recipes':[{'conditions':[{'type':'eln:scanner_crafting_enabled'}],'recipe':recipe}]}
            if row.get('conditions'):recipe={'type':'forge:conditional','recipes':[{'conditions':row['conditions'],'recipe':recipe}]}
            if conditional:
                variants=[]
                for selection in itertools.product(*(range(len(options)) for _,options in conditional)):
                    conditions=[];choices={}
                    for (key,options),chosen in zip(conditional,selection):
                        conditions += [{'type':'forge:tag_empty','tag':option['tag']} for option in options[:chosen]]
                        if chosen<len(options)-1:conditions.append({'type':'forge:not','value':{'type':'forge:tag_empty','tag':options[chosen]['tag']}})
                        choices[key]=options[chosen]
                    variants.append({'conditions':conditions,'recipe':replace_choices(recipe,choices)})
                recipe={'type':'forge:conditional','recipes':variants}
            number=used.get(target,0);used[target]=number+1
            path=target if number==0 else target+'_original_'+str(row['line'])
            if row.get('variant'):path=target+'_original_'+str(row['line'])+'_'+row['variant']
            write('data/eln/recipes/'+path+'.json',recipe);emitted.append({'line':row['line'],'target':target,'file':path})
        except (ValueError,KeyError,IndexError) as error:unresolved.append({'line':row['line'],'target':target,'reason':str(error)})
    classified=[{'line':row['line'],'category':row['category'],'expression':row['result'].get('expression','')} for row in records if row.get('category','crafting')!='crafting']
    # Remove only the obsolete earlier primary files now replaced by reviewed loop variants.
    for target in ('utility_pole','direct_utility_pole','transmission_tower','grid_dc_dc_converter','grid_switch','2x3_solar_panel'):
        if not any(row['file']==target for row in emitted):(port/'src/main/resources/data/eln/recipes'/str(target+'.json')).unlink(missing_ok=True)
    (port/'docs/original-crafting-audit.json').write_text(json.dumps({'baseline':'1.24.6','emitted':emitted,'unresolved':unresolved,'nonCraftingCalls':classified,'customDynamicCalls':redirected},indent=2)+'\n',encoding='utf-8')
    print('Restored',len(emitted),'original crafting variants;',len(unresolved),'crafting source calls remain unresolved.')

def replace_choices(value,choices):
    if isinstance(value,dict):return choices[value['originalChoice']] if 'originalChoice' in value else {k:replace_choices(v,choices) for k,v in value.items()}
    if isinstance(value,list):return [replace_choices(v,choices) for v in value]
    return value
