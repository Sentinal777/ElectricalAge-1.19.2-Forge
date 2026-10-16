package mods.eln.sim.mna;

import java.util.IdentityHashMap;
import java.util.List;
import mods.eln.sim.mna.component.Capacitor;
import mods.eln.sim.mna.component.CurrentSource;
import mods.eln.sim.mna.component.Inductor;
import mods.eln.sim.mna.component.VoltageSource;
import mods.eln.sim.mna.misc.ISubSystemProcessI;

/** Only exact, known side-effect-free source stamps may use inverse sensitivity. */
final class LinearRhs {
    private boolean dirty = true;
    private boolean pure;
    private final IdentityHashMap<VoltageSource, Integer> sources = new IdentityHashMap<>();

    void invalidate() { dirty = true; }

    boolean canUse(List<ISubSystemProcessI> processes, VoltageSource source) {
        if (dirty) {
            sources.clear();
            pure = true;
            for (ISubSystemProcessI process : processes) {
                Class<?> type = process.getClass();
                if (type != VoltageSource.class && type != CurrentSource.class &&
                        type != Capacitor.class && type != Inductor.class) pure = false;
                if (type == VoltageSource.class) sources.merge((VoltageSource) process, 1, Integer::sum);
            }
            dirty = false;
        }
        return pure && sources.getOrDefault(source, 0) == 1;
    }

    static void stamp(SubSystem system) {
        for (int i = 0; i < system.stateCount; i++) system.Idata[i] = 0.0;
        for (ISubSystemProcessI process : system.processI) process.simProcessI(system);
    }
}
