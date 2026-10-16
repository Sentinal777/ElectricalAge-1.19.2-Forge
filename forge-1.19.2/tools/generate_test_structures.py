"""Generate a clear, adequately spaced template for actual overhead-grid GameTests."""
from pathlib import Path
import gzip,struct
def string(s):
    b=s.encode('utf-8');return struct.pack('>H',len(b))+b
def tag(kind,name,data):return bytes([kind])+string(name)+data
def ints(values):return bytes([3])+struct.pack('>i',len(values))+b''.join(struct.pack('>i',n) for n in values)
size=(50,14,16)
palette=bytes([10])+struct.pack('>i',1)+tag(8,'Name',string('minecraft:air'))+b'\0'
blocks=[]
for x in range(size[0]):
    for y in range(size[1]):
        for z in range(size[2]):blocks.append(tag(9,'pos',ints([x,y,z]))+tag(3,'state',struct.pack('>i',0))+b'\0')
root=tag(3,'DataVersion',struct.pack('>i',3120))+tag(9,'size',ints(size))+tag(9,'palette',palette)+tag(9,'blocks',bytes([10])+struct.pack('>i',len(blocks))+b''.join(blocks))+tag(9,'entities',bytes([10])+struct.pack('>i',0))+b'\0'
p=Path(__file__).resolve().parents[1]/'src/main/resources/data/eln/structures/grid_empty.nbt'
p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(gzip.compress(bytes([10])+string('')+root))
print('Generated grid test template',size)
