"""Extract platform-independent device behavior with recorded, explicit adapters."""
from pathlib import Path
import hashlib, json, re
PORT=Path(__file__).resolve().parents[1]; ROOT=PORT.parent
manifest=[]
def emit(source,destination,text,notes):
    p=PORT/destination;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
    manifest.append(dict(source=source,destination=destination,
        source_sha256=hashlib.sha256((ROOT/source).read_bytes()).hexdigest(),adaptation=notes))

solar='src/main/java/mods/eln/transparentnode/solarpanel/SolarPanelPowerProcess.java'
emit(solar,solar,(ROOT/solar).read_text().replace('NBTTagCompound','CompoundTag').replace('.setDouble(','.putDouble('),
     'Entire original class; CompoundTag and putDouble replace the 1.7 NBT API.')

gates='src/main/kotlin/mods/eln/sixnode/LogicGate.kt';s=(ROOT/gates).read_text()
body=s[s.index('abstract class LogicFunction'):s.index('class PalDescriptor')]+s[s.index('class Pal : LogicFunction'):]
body=body.replace('NBTTagCompound','CompoundTag').replace('.setBoolean(','.putBoolean(').replace('.setDouble(','.putDouble(').replace('.setInteger(','.putInt(').replace('.getInteger(','.getInt(')
body=body.replace('AnalogFunction.inputColors','listOf("A", "B", "C")')
body=body.replace('(1 / Eln.simulator.callPeriod)', '(1 / LogicTiming.nominalCallPeriod)')
body=body.replace('Eln.simulator.callPeriod/halfPeriod','LogicTiming.stepSeconds/halfPeriod')
header='''package mods.eln.sixnode.logicgate

import mods.eln.Eln
import mods.eln.misc.INBTTReady
import mods.eln.misc.LinearFunction
import mods.eln.misc.Utils
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component

// Text is translated by Minecraft's common language interface, safe on dedicated servers.
private fun tr(text: String): String = Component.translatable(text.replace(' ', '_')).string.let {
    if (it == text.replace(' ', '_')) text else it
}
object LogicTiming {
    const val nominalCallPeriod = 0.05
    var stepSeconds = 0.005
}

'''
emit(gates,'src/main/kotlin/mods/eln/sixnode/logicgate/LogicFunctions.kt',header+body,
     'Extract LogicFunction through JKFlipFlop, plus Pal. Original Boolean/analog/state/NBT logic. Replace legacy tooltip colors and translation boundary; supply the native solver timestep to oscillator phase.')

analog='src/main/kotlin/mods/eln/sixnode/AnalogChips.kt';s=(ROOT/analog).read_text()
body='\n'.join(s[s.index(start):s.index(end)] for start,end in [
    ('abstract class AnalogFunction','class PIDRegulatorElement'),
    ('open class VoltageControlledSawtoothOscillator','class AmplifierElement'),
    ('class VoltageControlledAmplifier','class SummingUnitElement'),
    ('class SampleAndHold','class FilterElement')])
body=body.replace('NBTTagCompound','CompoundTag').replace('setDouble(','putDouble(').replace('setBoolean(','putBoolean(')
body=body.replace('(1 / Eln.simulator.callPeriod)', '(1 / 0.05)').replace('Eln.simulator.callPeriod/halfPeriod','deltaTime/halfPeriod')
header='''package mods.eln.sixnode

import mods.eln.Eln
import mods.eln.misc.INBTTReady
import mods.eln.misc.LinearFunction
import mods.eln.misc.Utils
import mods.eln.solver.Equation
import mods.eln.solver.Constant
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import kotlin.math.sin

private fun tr(text: String): String = Component.translatable(text.replace(' ', '_')).string.let {
    if (it == text.replace(' ', '_')) text else it
}

'''
emit(analog,'src/main/kotlin/mods/eln/sixnode/AnalogFunctions.kt',header+body,
     'Extract all nine analog functions and their original state/NBT behavior. Translation and NBT API adapters; use supplied native timestep for oscillator phase. Original GUI classes are replaced by the native device menu.')

tests='src/test/kotlin/mods/eln/transparentnode/solarpanel/SolarPanelPowerProcessTest.kt'
t=(ROOT/tests).read_text();t=t[:t.index('    @Test\n    fun singleTilePanelStaysFloatingAfterNbtLoad')]+'}\n'
t=re.sub(r'^import (?:mods\.eln\.(?:cable|misc|node)\.[^\n]+|net\.minecraft\.nbt\.NBTTagCompound|kotlin\.test\.assertFalse)\n','',t,flags=re.M)
emit(tests,tests,t,'First six original tests, unchanged assertions. Seventh requires the old world adapter; replaced by native floating-panel GameTest.')
for name in ['BatterySlowProcessTest','nbt/NbtBatteryProcessTest']:
    path='src/test/kotlin/mods/eln/sim/'+name+'.kt'
    s=(ROOT/path).read_text().replace('NBTTagCompound','CompoundTag').replace('setDouble(','putDouble(')
    emit(path,path,s,'Entire original test; NBT API names modernized where needed.')
(PORT/'docs/device-math-migration.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Extracted original solar process, twelve logic functions, nine analog functions, and device tests.')
