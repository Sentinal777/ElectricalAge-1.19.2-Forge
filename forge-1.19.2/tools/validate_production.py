"""Load a normal reobfuscated JAR in a fresh, isolated Forge dedicated server.

Requires an existing Forge43.5.0 server library directory and its previously
accepted eula.txt. Never points Minecraft at an existing world or mod folder.
Optional mod JARs are copied only when explicitly supplied as arguments.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import queue
import re
import shutil
import subprocess
import threading
import time
import zipfile
from scan_world_resources import scan

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--java', required=True, type=Path)
parser.add_argument('--jar', required=True, type=Path)
parser.add_argument('--forge-server', required=True, type=Path)
parser.add_argument('--output', required=True, type=Path)
parser.add_argument('--optional-mod', action='append', default=[], type=Path)
parser.add_argument('--port', type=int, default=25592)
parser.add_argument('--minimum-tps', type=float, default=18.0)
args = parser.parse_args()
for p in (args.java, args.jar, args.forge_server, *args.optional_mod):
    if not p.exists():
        parser.error(f'Missing required input: {p}')
accepted = args.forge_server / 'eula.txt'
if not accepted.exists() or 'eula=true' not in accepted.read_text(encoding='utf-8').replace(' ', '').lower():
    parser.error('The supplied Forge server does not have an already accepted eula.txt.')
with zipfile.ZipFile(args.jar) as jar:
    forbidden = [n for n in jar.namelist() if n.endswith('.class') and
                 ('ClientSmoke' in n or n.startswith(('buildcraft/', 'dan200/computercraft/')))]
    assert not forbidden, f'QA or optional-mod classes embedded in production artifact: {forbidden[:10]}'
if args.output.exists():
    parser.error('Output must be a new directory; previous evidence and worlds are preserved.')
args.output.mkdir(parents=True)
server = args.output / 'server'
server.mkdir()

def link_or_copy(source, destination):
    try:
        os.link(source, destination)
    except OSError:
        shutil.copy2(source, destination)
    return destination

shutil.copytree(args.forge_server / 'libraries', server / 'libraries', copy_function=link_or_copy)
shutil.copyfile(accepted, server / 'eula.txt')
(server / 'mods').mkdir()
for source in (args.jar, *args.optional_mod):
    shutil.copy2(source, server / 'mods' / source.name)
(server / 'server.properties').write_text(
    f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={args.port}\nlevel-name=eln-production-validation\n'
    'level-seed=1246\nview-distance=6\nsimulation-distance=6\nspawn-protection=0\n'
    'max-tick-time=-1\ngamemode=creative\n', encoding='utf-8')

class Instance:
    def __init__(self, name):
        self.lines = []
        self.events = queue.Queue()
        self.file = (args.output / f'{name}.log').open('w', encoding='utf-8')
        self.process = subprocess.Popen(
            [str(args.java.resolve()), '-Xms256M', '-Xmx3G',
             '@libraries/net/minecraftforge/forge/1.19.2-43.5.0/win_args.txt', '--nogui'],
            cwd=server, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, encoding='utf-8', errors='replace',
            creationflags=0x08000000 if os.name == 'nt' else 0)
        self.reader = threading.Thread(target=self.collect, daemon=True)
        self.reader.start()
        self.wait_for('Done (', timeout=240)
        print(f'{name}: production Forge startup passed', flush=True)

    def collect(self):
        for line in self.process.stdout:
            self.lines.append(line)
            self.file.write(line)
            self.file.flush()
            self.events.put(line)

    def wait_for(self, pattern, timeout=30):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                line = self.events.get(timeout=min(1, max(.01, deadline - time.monotonic())))
                if pattern in line:
                    return line
            except queue.Empty:
                if self.process.poll() is not None:
                    break
        raise RuntimeError(f'Production server did not report {pattern!r}; inspect {self.file.name}')

    def command(self, text):
        assert self.process.poll() is None, 'Production server exited'
        self.process.stdin.write(text + '\n')
        self.process.stdin.flush()

    def number(self, path):
        self.command('data get block ' + path)
        line = self.wait_for('has the following block data:')
        match = re.search(r'has the following block data: ([-+0-9.Ee]+)[bBsSdDfFlL]?\s*$', line)
        assert match, f'Unexpected real production NBT reading: {line}'
        return float(match[1])

    def text(self, path):
        self.command('data get block ' + path)
        return self.wait_for('has the following block data:')

    def finish(self):
        self.command('save-all flush')
        self.wait_for('Saved the game')
        self.command('stop')
        self.process.wait(timeout=90)
        self.reader.join(timeout=10)
        self.file.close()
        assert self.process.returncode == 0
        text = ''.join(self.lines)
        for failure in ('Circuit simulation paused', 'NoClassDefFoundError', 'Exception ticking', 'Failed to start'):
            assert failure not in text, f'Production runtime reported {failure}'
        return text

def block(x, z, identity, rotation=0, extra=''):
    carrier = 'machine_node' if identity in ('cost_oriented_battery', 'energy_converter') else 'six_node'
    return f'setblock {x} 120 {z} eln:{carrier}' + '{parts:[{id:"' + identity + \
        f'",face:0,componentDataVersion:8,orientationVersion:2,rotation:{rotation}' + extra + '}]} replace'

results = {'artifact': args.jar.name, 'sha256': hashlib.sha256(args.jar.read_bytes()).hexdigest(),
           'forge': '43.5.0', 'minecraft': '1.19.2', 'java': 17,
           'optionalMods': [{'name': p.name, 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in args.optional_mod]}
optional_entries = set()
for optional in args.optional_mod:
    with zipfile.ZipFile(optional) as z:
        optional_entries.update(z.namelist())
instance = None
try:
    instance = Instance('first-start')
    instance.command('forceload add -16 -16 64 48')
    instance.command('fill -2 120 -2 24 122 26 minecraft:air')
    instance.command('fill -2 119 -2 24 119 26 minecraft:stone')
    for command in (block(0, 0, 'electrical_source'), block(1, 0, 'low_voltage_cable'),
                    block(2, 0, 'creative_power_resistor', 2), block(3, 0, 'ground_cable'),
                    block(5, 0, 'electrical_source'), block(6, 0, 'energy_converter'),
                    block(9, 0, 'ground_cable'), block(10, 0, 'cost_oriented_battery'),
                    block(11, 0, 'creative_power_resistor', 2), block(12, 0, 'ground_cable')):
        instance.command(command)
    time.sleep(5)
    current = instance.number('2 120 0 parts[0].current')
    assert .49 < abs(current) < .51, current
    instance.command('setblock 1 120 0 minecraft:air')
    instance.command('data modify block 5 120 0 parts[0].enabled set value 0b')
    instance.command('data modify block 10 120 0 parts[0].enabled set value 0b')
    time.sleep(2)
    stopped = instance.number('2 120 0 parts[0].current')
    assert abs(stopped) < 1e-6, stopped
    saved_fe = instance.number('6 120 0 feEnergy')
    saved_charge = instance.number('10 120 0 parts[0].charge')
    stopped_battery_current = instance.number('10 120 0 parts[0].current')
    assert abs(stopped_battery_current) < 1e-8, stopped_battery_current
    assert saved_fe > 0 and 0 < saved_charge < .5
    for command in (block(0, 5, 'current_source'), block(1, 5, 'creative_cable'),
                    block(2, 5, 'creative_power_resistor', 2), block(3, 5, 'ground_cable')):
        instance.command(command)
    time.sleep(2)
    actual_source_current = instance.number('2 120 5 parts[0].current')
    actual_source_voltage = instance.number('2 120 5 parts[0].voltage') - instance.number('2 120 5 parts[0].voltageB')
    assert abs(abs(actual_source_current) - 1) < 1e-6 and abs(abs(actual_source_voltage) - 100) < 1e-4, (actual_source_current, actual_source_voltage)
    instance.command('data modify block 0 120 5 parts[0].enabled set value 0b')
    time.sleep(1)
    assert abs(instance.number('2 120 5 parts[0].current')) < 1e-6
    results.update(actualCurrentSourceA=actual_source_current, actualCurrentSourceLoadV=actual_source_voltage,
                   actualCurrentSourceDisable=True, actualCreativeCable=True)
    if 'buildcraft/builders/tile/TileQuarry.class' in optional_entries:
        for command in (block(22, 0, 'energy_converter', extra=',value:1.0d'),
                        block(23, 0, 'creative_power_resistor', 2), block(24, 0, 'ground_cable'),
                        'setblock 22 119 0 buildcraftcore:engine[type=creative]{currentOutputIndex:3} replace',
                        'setblock 21 119 0 minecraft:redstone_block'):
            instance.command(command)
        time.sleep(20)
        engine_current = instance.number('23 120 0 parts[0].current')
        engine_work = instance.number('23 120 0 parts[0].energy')
        assert .45 < abs(engine_current) < .51 and engine_work > 10, (engine_current, engine_work)
        instance.command('setblock 22 119 0 minecraft:air')
        instance.command(block(21, 3, 'energy_converter', extra=',value:1.0d'))
        instance.command(block(22, 3, 'energy_converter'))
        instance.command('data modify block 21 120 3 feEnergy set value 1000.0d')
        time.sleep(12)
        remaining = instance.number('21 120 3 feEnergy') + instance.number('22 120 3 feEnergy')
        roundtrip_work = instance.number('22 120 3 parts[0].energy')
        assert remaining < 1000 and roundtrip_work > 10, (remaining, roundtrip_work)
        results.update(publishedBcceEngineA=engine_current, actualEngineLoadJ=engine_work,
                       actualMjRoundTripRemainingFe=remaining, actualMjRoundTripExportJ=roundtrip_work)
    if 'dan200/computercraft/shared/computer/blocks/TileComputerBase.class' in optional_entries:
        computer_root = server / 'eln-production-validation/computercraft/computer/7001'
        computer_root.mkdir(parents=True, exist_ok=True)
        (computer_root / 'startup.lua').write_text('''local ok,err=pcall(function()
 local p
 for i=1,100 do p=peripheral.find("ElnProbe");if p then break end;sleep(0.1) end
 assert(p,"normal installed peripheral missing")
 assert(math.abs(p.signalGetIn("ZN")-0.5)<0.01,"normal physical input")
 p.signalSetDir("XP","out");p.signalSetOut("XP",0.75)
 assert(p.signalGetDir("XP")=="out" and math.abs(p.signalGetOut("XP")-0.75)<0.001)
 p.wirelessSet("production-radio",0.6);assert(p.wirelessGet("absent","bigger")==0)
 p.wirelessRemove("production-radio");p.wirelessSet("remove-all",0.1);p.wirelessRemoveAll()
 assert(not pcall(p.signalSetOut,"XP",0/0),"nonfinite input accepted")
 os.setComputerLabel("ELN_NORMAL_PROBE_OK")
end)
if not ok then local f=fs.open("probe-error.txt","w");f.write(tostring(err));f.close();os.setComputerLabel("ELN_NORMAL_PROBE_ERROR") end
''', encoding='utf-8')
        instance.command('setblock 8 120 4 eln:computer_probe{parts:[{id:"computer_probe",face:0,componentDataVersion:8,orientationVersion:2}]} replace')
        instance.command(block(8, 3, 'signal_source', extra=',value:2.5d'))
        instance.command(block(9, 4, 'signal_cable'))
        instance.command('setblock 7 120 4 computercraft:computer_normal{ComputerId:7001,On:1b,Label:"ELN_NORMAL_PROBE_BOOTING"} replace')
        label = ''
        for _ in range(30):
            time.sleep(1)
            instance.command('data get block 7 120 4 Label')
            label = instance.wait_for('has the following block data:')
            if 'ELN_NORMAL_PROBE_OK' in label:
                break
            if 'ELN_NORMAL_PROBE_ERROR' in label:
                raise AssertionError((computer_root / 'probe-error.txt').read_text(encoding='utf-8'))
        assert 'ELN_NORMAL_PROBE_OK' in label, label
        actual_output = instance.number('9 120 4 parts[0].voltage')
        assert 3.74 < actual_output < 3.76, actual_output
        results.update(normalInstalledCcLuaAllNineMethods=True, actualProbeOutputV=actual_output)
    for x in range(1, 17):
        for z in range(8, 24):
            instance.command(block(x, z, 'low_voltage_cable'))
    instance.command(block(0, 8, 'electrical_source'))
    instance.command(block(17, 8, 'creative_power_resistor', 2))
    instance.command(block(18, 8, 'ground_cable'))
    time.sleep(3)
    network_current = instance.number('17 120 8 parts[0].current')
    assert .45 < abs(network_current) < .51, network_current
    instance.command('data modify block 8 120 16 nodeCoverState set value {Name:"minecraft:oak_log",Properties:{axis:"x"}}')
    instance.command('data modify block 8 120 16 nodeCoverItem set value {id:"minecraft:oak_log",Count:1b,tag:{BlockStateTag:{axis:"x"}}}')
    assert 'minecraft:oak_log' in instance.text('8 120 16 nodeCoverState.Name')
    instance.command('execute if block 8 120 16 eln:six_node[covered=true,cover_opacity=15] run say ELN_PRODUCTION_COVER_STATE_OK')
    instance.wait_for('ELN_PRODUCTION_COVER_STATE_OK')
    instance.command('debug start')
    instance.wait_for('Started tick profiling')
    time.sleep(15)
    instance.command('debug stop')
    profile = instance.wait_for('Stopped tick profiling')
    tps_match = re.search(r'\(([0-9.]+) ticks per second\)', profile)
    results.update(analyticalCircuitA=current, disconnectedA=stopped, savedFe=saved_fe,
                   savedBatteryCharge=saved_charge, stoppedBatteryCurrentA=stopped_battery_current, meshCableNodes=256, meshLoadA=network_current,
                   realTickProfile=profile.strip(), measuredTicksPerSecond=float(tps_match[1]) if tps_match else None)
    assert results['measuredTicksPerSecond'] is not None and results['measuredTicksPerSecond'] >= args.minimum_tps, results
    instance.finish()
    instance = Instance('world-restart')
    restored_fe = instance.number('6 120 0 feEnergy')
    restored_charge = instance.number('10 120 0 parts[0].charge')
    # Disabled batteries retain the original finite 1e12-ohm off-state branch.
    # Server ticks continue between readings and while restarting; expose that
    # minute drift instead of asserting mathematically impossible bit equality.
    charge_drift = restored_charge - saved_charge
    assert restored_fe == saved_fe and abs(charge_drift) <= 1e-10, (saved_fe, restored_fe, saved_charge, restored_charge)
    assert abs(instance.number('2 120 0 parts[0].current')) < 1e-6
    assert .45 < abs(instance.number('17 120 8 parts[0].current')) < .51
    assert 'minecraft:oak_log' in instance.text('8 120 16 nodeCoverState.Name')
    assert '"x"' in instance.text('8 120 16 nodeCoverState.Properties.axis')
    assert instance.number('8 120 16 nodeCoverItem.Count') == 1
    instance.command('execute if block 8 120 16 eln:six_node[covered=true,cover_opacity=15] run say ELN_PRODUCTION_COVER_RESTART_OK')
    instance.wait_for('ELN_PRODUCTION_COVER_RESTART_OK')
    results.update(actualWorldRestart=True, restoredFe=restored_fe, restoredBatteryCharge=restored_charge,
                   batteryChargeDrift=charge_drift, maximumPermittedChargeDrift=1e-10,
                   actualNodeCoverSaveRestart=True, coverRetainedBlockState='minecraft:oak_log[axis=x]', coverRetainedItems=1)
    instance.finish()
    instance = None
except Exception as failure:
    results.update(validationComplete=False, failure=str(failure))
    (args.output / 'results.partial.json').write_text(json.dumps(results, indent=2) + '\n', encoding='utf-8')
    raise
finally:
    if instance is not None and instance.process.poll() is None:
        instance.command('stop')
        try:
            instance.process.wait(timeout=60)
        except subprocess.TimeoutExpired:
            instance.process.terminate()
results['worldGeneration'] = scan(server / 'eln-production-validation/region')
(args.output / 'results.json').write_text(json.dumps(results, indent=2) + '\n', encoding='utf-8')
print(json.dumps(results), flush=True)
