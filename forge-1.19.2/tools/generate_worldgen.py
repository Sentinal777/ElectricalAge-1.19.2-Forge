"""Forge 1.19.2 ore features based on 1.24.6 ItemRegistration/OreDescriptor parameters."""
from pathlib import Path
import json
PORT=Path(__file__).resolve().parents[1]
RES=PORT/'src/main/resources'
def write(path,value):
    p=RES/path;p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
mining=['eln:arc_clay_block','eln:arc_metal_block']
lang=RES/'assets/eln/lang/en_us.json';language=json.loads(lang.read_text(encoding='utf-8'))
for metal,count,size,height in [('copper',30,8,79),('lead',8,6,23),('tungsten',6,6,31),('cinnabar',3,6,31)]:
    id=metal+'_ore';mining.append('eln:'+id)
    write(f'assets/eln/blockstates/{id}.json',{'variants':{'':{'model':'eln:block/'+id}}})
    write(f'assets/eln/models/block/{id}.json',{'parent':'minecraft:block/cube_all','textures':{'all':'eln:blocks/'+metal+'ore'}})
    write(f'assets/eln/models/item/{id}.json',{'parent':'eln:block/'+id})
    language['block.eln.'+id]=metal.title()+' Ore'
    write(f'data/eln/loot_tables/blocks/{id}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'eln:'+id}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    targets=[{'target':{'predicate_type':'minecraft:tag_match','tag':'minecraft:stone_ore_replaceables'},'state':{'Name':'eln:'+id}},
        {'target':{'predicate_type':'minecraft:tag_match','tag':'minecraft:deepslate_ore_replaceables'},'state':{'Name':'eln:'+id}}]
    write(f'data/eln/worldgen/configured_feature/ore_{metal}.json',{'type':'minecraft:ore','config':{'size':size,'discard_chance_on_air_exposure':0.,'targets':targets}})
    write(f'data/eln/worldgen/placed_feature/ore_{metal}.json',{'feature':'eln:ore_'+metal,'placement':[
        {'type':'minecraft:count','count':count},{'type':'minecraft:in_square'},
        {'type':'minecraft:height_range','height':{'type':'minecraft:uniform','min_inclusive':{'absolute':0},'max_inclusive':{'absolute':height}}},
        {'type':'minecraft:biome'}]})
    write(f'data/eln/forge/biome_modifier/ore_{metal}.json',{'type':'forge:add_features','biomes':'#minecraft:is_overworld','features':['eln:ore_'+metal],'step':'underground_ores'})
    output='minecraft:copper_ingot' if metal=='copper' else 'eln:mercury' if metal=='cinnabar' else 'eln:'+metal+'_ingot'
    write(f'data/eln/recipes/smelt_{id}.json',{'type':'minecraft:smelting','ingredient':{'item':'eln:'+id},'result':output,'experience':.7,'cookingtime':200})
    # Processing belongs to the reviewed source-recipe generator. Remove the old preview override.
    (RES/f'data/eln/recipes/grind_{id}.json').unlink(missing_ok=True)
    write(f'data/forge/tags/items/ores/{metal}.json',{'replace':False,'values':['eln:'+id]})
write('data/minecraft/tags/blocks/mineable/pickaxe.json',{'replace':False,'values':mining})
# Original OreBlock/Arc blocks set no higher harvest tier; any pickaxe can harvest their rock material.
(RES/'data/minecraft/tags/blocks/needs_iron_tool.json').unlink(missing_ok=True)
lang.write_text(json.dumps(language,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Generated four original ore types and Forge biome modifiers.')
