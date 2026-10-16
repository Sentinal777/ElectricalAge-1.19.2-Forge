"""Extract literal original crafting recipes for review, retaining unresolved source expressions.

No ingredient substitutions or generic fallback recipes are synthesized. The reviewed
result is stored alongside this tool so ordinary resource generation needs no legacy
Minecraft classes or external checkout.
"""
from pathlib import Path
import argparse,json,re

def uncomment(source):
    return re.sub(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*',
                  lambda m: m[0] if m[0][0] in '\"\'' else ''.join('\n' if c=='\n' else ' ' for c in m[0]),source)

def split_args(text):
    result=[];start=0;depth=0;quote=None;escaped=False
    for i,c in enumerate(text):
        if quote:
            if escaped:escaped=False
            elif c=='\\':escaped=True
            elif c==quote:quote=None
        elif c in '\"\'':quote=c
        elif c in '([{':depth+=1
        elif c in ')]}':depth-=1
        elif c==',' and depth==0:result.append(text[start:i].strip());start=i+1
    result.append(text[start:].strip());return result

def literal(text):
    if re.fullmatch(r'\s*"(?:[^"\\]|\\.)*"(?:\s*\+\s*"(?:[^"\\]|\\.)*")*\s*',text):
        return ''.join(json.loads(m[0]) for m in re.finditer(r'"(?:[^"\\]|\\.)*"',text))

def item(text):
    m=re.fullmatch(r'(?:Eln\.)?findItemStack\((.*)\)',text,re.S)
    if m:
        args=split_args(m[1]);name=literal(args[0])
        if name is not None and (len(args)==1 or args[1].isdigit()):return {'elnName':name,'count':int(args[1]) if len(args)>1 else 1}
    m=re.fullmatch(r'ItemStack\((?:Items|Blocks)\.([\w]+)(?:,\s*(\d+)(?:,\s*(-?\d+|OreDictionary.WILDCARD_VALUE))?)?\)',text)
    if m:return {'vanilla':m[1],'count':int(m[2] or 1),'metadata':m[3] or '0'}
    name=literal(text)
    if name is not None:return {'ore':name}
    return {'expression':re.sub(r'\s+',' ',text)}

def extract(source):
    source=uncomment(source);recipes=[]
    for match in re.finditer(r'\b(addRecipe|addShapelessRecipe)\s*\(',source):
        start=match.end();i=start;depth=1;quote=None;escape=False
        while i<len(source) and depth:
            c=source[i]
            if quote:
                if escape:escape=False
                elif c=='\\':escape=True
                elif c==quote:quote=None
            elif c in '\"\'':quote=c
            elif c=='(':depth+=1
            elif c==')':depth-=1
            i+=1
        args=split_args(source[start:i-1]);line=source[:match.start()].count('\n')+1
        if not args:continue
        before=source[max(0,match.start()-120):match.start()]
        category='crafting'
        if re.search(r'\bfun\s*$',before):category='helper_declaration'
        elif re.match(r'Recipe\s*\(',args[0]):category='processing'
        elif before.endswith('.') or re.match(r'(?:ShapedOreRecipe|ShapelessOreRecipe)\s*\(',args[0]):category='helper_implementation'
        row={'line':line,'category':category,'type':'shaped' if match[1]=='addRecipe' else 'shapeless','result':item(args[0])}
        if row['type']=='shapeless':row['ingredients']=[item(a) for a in args[1:]]
        else:
            n=1;pattern=[]
            while n<len(args) and literal(args[n]) is not None:pattern.append(literal(args[n]));n+=1
            if not pattern or len(args[n:])%2:row['unresolvedArguments']=args[1:]
            else:
                row['pattern']=pattern;row['key']={}
                for k,v in zip(args[n::2],args[n+1::2]):
                    if re.fullmatch(r"'[^']'",k):row['key'][k[1]]=item(v)
                    else:row['unresolvedArguments']=args[1:];break
        recipes.append(row)
    return recipes

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('source',type=Path);parser.add_argument('output',type=Path);args=parser.parse_args()
    rows=extract(args.source.read_text(encoding='utf-8'));args.output.write_text(json.dumps({'baseline':'Electrical Age 1.24.6','source':'mods/eln/craft/CraftingRecipes.kt','recipes':rows},indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    print('Extracted',len(rows),'original recipe calls; unresolved arguments remain explicit.')
