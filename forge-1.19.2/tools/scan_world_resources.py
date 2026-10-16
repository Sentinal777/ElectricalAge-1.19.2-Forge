"""Read saved Anvil chunks and count actual generated Electrical Age ores.

Run only against a stopped isolated validation world. This tool never writes
Minecraft data and does not infer generation from registered feature names.
"""
from pathlib import Path
import argparse
import collections
import gzip
import json
import struct
import zlib


class NBT:
    def __init__(self, data):
        self.data, self.offset = data, 0

    def take(self, count):
        result = self.data[self.offset:self.offset + count]
        self.offset += count
        return result

    def number(self, fmt):
        return struct.unpack('>' + fmt, self.take(struct.calcsize('>' + fmt)))[0]

    def string(self):
        return self.take(self.number('H')).decode('utf-8')

    def payload(self, kind):
        if kind in range(1, 7):
            return self.number({1: 'b', 2: 'h', 3: 'i', 4: 'q', 5: 'f', 6: 'd'}[kind])
        if kind == 7:
            return self.take(self.number('i'))
        if kind == 8:
            return self.string()
        if kind == 9:
            child, count = self.number('B'), self.number('i')
            return [self.payload(child) for _ in range(count)]
        if kind == 10:
            result = {}
            while True:
                child = self.number('B')
                if child == 0:
                    return result
                name = self.string()
                result[name] = self.payload(child)
        if kind in (11, 12):
            return [self.number('i' if kind == 11 else 'q') for _ in range(self.number('i'))]
        raise ValueError(f'Unknown NBT type {kind}')

    def read(self):
        kind = self.number('B')
        self.string()
        return self.payload(kind)


def scan(region):
    totals, chunks = collections.Counter(), 0
    for file in region.glob('*.mca'):
        data = file.read_bytes()
        for entry in range(1024):
            location = int.from_bytes(data[entry * 4:entry * 4 + 3], 'big') * 4096
            if not location:
                continue
            length = int.from_bytes(data[location:location + 4], 'big')
            kind = data[location + 4]
            compressed = data[location + 5:location + 4 + length]
            assert kind in (1, 2, 3), f'Unsupported external/compression type {kind}: {file}'
            raw = zlib.decompress(compressed) if kind == 2 else gzip.decompress(compressed) if kind == 1 else compressed
            chunk = NBT(raw).read()
            chunks += 1
            for section in chunk.get('sections', []):
                states = section.get('block_states', {})
                palette = states.get('palette', [])
                ores = {i: p['Name'] for i, p in enumerate(palette)
                        if p['Name'].startswith('eln:') and p['Name'].endswith('_ore')}
                if not ores:
                    continue
                if len(palette) == 1:
                    totals[palette[0]['Name']] += 4096
                    continue
                bits = max(4, (len(palette) - 1).bit_length())
                per_long, mask = 64 // bits, (1 << bits) - 1
                values = states['data']
                for index in range(4096):
                    value = (values[index // per_long] >> ((index % per_long) * bits)) & mask
                    if value in ores:
                        totals[ores[value]] += 1
    assert all(totals['eln:' + metal + '_ore'] > 0 for metal in ('copper', 'lead', 'tungsten', 'cinnabar')), totals
    return dict(chunksScanned=chunks, naturallyGeneratedOreBlocks=dict(totals),
                method='Read actual saved block-state palettes and packed indices; validation commands placed no ore blocks.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--region', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    assert args.region.is_dir()
    result = scan(args.region)
    args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result))
