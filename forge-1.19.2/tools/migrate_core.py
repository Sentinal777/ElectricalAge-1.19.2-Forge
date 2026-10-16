"""Reproducible migration of upstream simulation code; never changes upstream files."""
from pathlib import Path
import re, json, hashlib

PORT = Path(__file__).resolve().parents[1]
ROOT = PORT.parent

JAVA_SIM = {
    'DiodeProcess','ElectricalConnection','ElectricalLoad','ElectricalResistorHeatThermalLoad',
    'FurnaceProcess','IProcess','ITimeRemoverObserver','NodeElectricalGateInputHysteresisProcess',
    'NodeVoltageState','PhysicalConstant','SignalRp','ThermalConnection','ThermalLoad',
    'ThermalResistor',
}
KOTLIN_MISC = {
    'Profiler','INBTTReady','IFunction','FunctionTable','FunctionTableYProtect','LinearFunction',
    'SeriesFunction','PhysicalInterpolator','PhysicalInterpolatorNoRebound','RcInterpolator',
    'RcRcInterpolator','SlewLimiter','ThermalParameters','ElectricalSafety','NominalVoltage','HSLColor','WindProcess',
}
KOTLIN_SIM = {'BatteryProcess','BatterySlowProcess','Differentiator','Integrator','SignalLoadSupport'}
EXTRA = {'sim/nbt/NbtBatteryProcess.kt','sim/process/destruct/IDestructible.kt'}

def selected(path):
    s = path.as_posix().split('mods/eln/',1)[1]
    return (s.startswith(('sim/mna/','sim/process/heater/','solver/','sim/fsm/'))
      or s in EXTRA
      or s.startswith('sim/nbt/') and path.suffix=='.java'
      or s.startswith('sim/') and len(s.split('/'))==2 and path.stem in JAVA_SIM|KOTLIN_SIM
      or s.startswith('misc/') and path.stem in KOTLIN_MISC)

def modernize(s):
    s=s.replace('NBTTagCompound','CompoundTag')
    for old,new in {'setDouble':'putDouble','setFloat':'putFloat','setInteger':'putInt',
      'getInteger':'getInt','setBoolean':'putBoolean','setString':'putString','setLong':'putLong',
      'setByte':'putByte','setShort':'putShort','hasKey':'contains','setTag':'put',
      'getCompoundTag':'getCompound'}.items():
        s=re.sub(r'\.'+old+r'\(','.'+new+'(',s)
    return s

manifest=[]
for lang in ('java','kotlin'):
    for p in sorted((ROOT/'src/main'/lang/'mods/eln').rglob('*')):
        if p.suffix not in ('.java','.kt') or not selected(p): continue
        dest=PORT/p.relative_to(ROOT)
        dest.parent.mkdir(parents=True,exist_ok=True)
        text=modernize(p.read_text(encoding='utf-8'))
        notes='NBT API and platform boundary only'
        if p.stem=='SubSystem':
            text=text.replace('Idata[s.getId()] = v;','// Kirchhoff: accumulate all device currents at a shared node.\n        Idata[s.getId()] += v;')
            text=text.replace('double originalVoltage = d.state;','double originalVoltage = d.state;\n        double sourceVoltage = voltageSource.getVoltage();')
            text=text.replace('voltageSource.setVoltage(originalVoltage);\n\n        thevenin.resistance','voltageSource.setVoltage(sourceVoltage);\n\n        thevenin.resistance')
            notes+='; fix upstream RHS overwrite so parallel currents and series solar sources sum correctly'
            notes+='; restore the probed source setting rather than the previous state voltage'
            dot=r'DD stack = DD\.ZERO;\s*DD\[\] inverseRow = AInvdata\[idx2\];\s*for \(int idx = 0; idx < stateCount; idx\+\+\) \{\s*stack = stack\.add\(inverseRow\[idx\]\.multiply\(Idata\[idx\]\)\);\s*\}\s*'
            text=re.sub(dot+r'XtempData\[idx2\] = stack\.doubleValue\(\);','XtempData[idx2] = MnaDotProduct.sum(AInvdata[idx2], Idata, inverseRowsSafe[idx2]);',text)
            text=re.sub(dot+r'return stack\.doubleValue\(\);','return MnaDotProduct.sum(AInvdata[idx2], Idata, inverseRowsSafe[idx2]);',text)
            text=text.replace('DD[][] AInvdata;','DD[][] AInvdata;\n    boolean[] inverseRowsSafe;')
            text=text.replace('AInvdata = invertMatrix(A);','AInvdata = invertMatrix(A);\n            inverseRowsSafe = new boolean[AInvdata.length];\n            for (int row = 0; row < AInvdata.length; row++) inverseRowsSafe[row] = MnaDotProduct.isSafeRow(AInvdata[row]);')
            text=text.replace('ArrayList<ISubSystemProcessI> processI = new ArrayList<ISubSystemProcessI>();',
                'ArrayList<ISubSystemProcessI> processI = new ArrayList<ISubSystemProcessI>();\n    private final LinearRhs linearRhs = new LinearRhs();')
            text=text.replace('processI.remove(p);','processI.remove(p);\n        linearRhs.invalidate();')
            text=text.replace('processI.add(p);','processI.add(p);\n        linearRhs.invalidate();')
            probe='''double originalCurrent = solve(voltageSource.getCurrentState());

        double theveninResistance = (testVoltage - originalVoltage) / (originalCurrent - testCurrent);'''
            sensitivity='''double theveninResistance;
        int currentIndex = voltageSource.getCurrentState().getId();
        if (matrixValid && !singularMatrix && inverseRowsSafe[currentIndex] &&
                Double.isFinite(originalVoltage) && Double.isFinite(testCurrent) &&
                testVoltage != originalVoltage && linearRhs.canUse(processI, voltageSource)) {
            LinearRhs.stamp(this);
            theveninResistance = -1.0 / AInvdata[currentIndex][currentIndex].doubleValue();
        } else {
            double originalCurrent = solve(voltageSource.getCurrentState());
            theveninResistance = (testVoltage - originalVoltage) / (originalCurrent - testCurrent);
        }'''
            if probe not in text:raise ValueError('Thevenin source template changed; review the performance adapter')
            text=text.replace(probe,sensitivity)
            notes+='; use ordered primitive DD products with Java17 FMA residuals and legacy exceptional-value fallback'
            notes+='; analytical linear-source sensitivity retains both RHS stamps and stateful/unknown legacy fallback'
        if p.stem=='CurrentSource':
            text=text.replace('public void addToSubsystem(SubSystem s) {','public void addToSubsystem(SubSystem s) {\n        super.addToSubsystem(s);')
            text=text.replace('localSubSystem.removeProcess(this);','localSubSystem.removeProcess(this);\n        super.quitSubSystem();')
            notes+='; retain/clear subsystem ownership for source removal and topology changes'
        if p.stem=='PowerSource':
            text=text.replace('        s.addProcess(this);','        // VoltageSource already registers the RHS callback.\n        // Do not double the voltage after addToI accumulates contributions.')
            notes+='; remove duplicate inherited RHS callback registration'
        if p.stem=='PowerSourceBipole':
            for target in ['a.voltage','a.voltage - I * a.resistance']:
                text=text.replace('aSrc.setVoltage('+target+');','aSrc.setVoltage((aSrc.getVoltage() + '+target+') * 0.5);')
            for target in ['b.voltage','b.voltage + I * b.resistance']:
                text=text.replace('bSrc.setVoltage('+target+');','bSrc.setVoltage((bSrc.getVoltage() + '+target+') * 0.5);')
            notes+='; converge coupled source commands when the current limit is active'
        if p.stem in {'NbtElectricalGateInput','NbtElectricalGateOutput','NbtElectricalGateInputOutput'}:
            text=text.replace('Eln.instance.signalCableDescriptor.applyTo(this);','setSerialResistance(Eln.SIGNAL_SERIAL_RESISTANCE);')
        dest.write_text(text,encoding='utf-8')
        manifest.append({'source':str(p.relative_to(ROOT)), 'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),
          'destination':str(dest.relative_to(PORT)), 'migration':notes})

for p in sorted((ROOT/'src/test/kotlin/mods/eln').rglob('*.kt')):
    rel=p.relative_to(ROOT/'src/test/kotlin/mods/eln')
    if rel.as_posix().startswith(('sim/mna/','solver/','sim/fsm/')) or p.stem in {'BatteryProcessTest','DifferentiatorTest'}:
        if any(x in p.stem for x in ('Benchmark','Profiling')): continue
        dest=PORT/p.relative_to(ROOT);dest.parent.mkdir(parents=True,exist_ok=True)
        text=modernize(p.read_text(encoding='utf-8'))
        if p.stem=='PowerSourceBipoleTest':text=text.replace('process.rootSystemPreStepProcess()','repeat(40) { process.rootSystemPreStepProcess() }')
        dest.write_text(text,encoding='utf-8')

(PORT/'docs').mkdir(exist_ok=True)
(PORT/'docs/core-migration.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print(f'Migrated {len(manifest)} simulation, expression, and numerical helper files.')
