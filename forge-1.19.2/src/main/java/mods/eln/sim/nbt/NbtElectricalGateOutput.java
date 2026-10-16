package mods.eln.sim.nbt;

import mods.eln.Eln;
import mods.eln.misc.Utils;

public class NbtElectricalGateOutput extends NbtElectricalLoad {

    public NbtElectricalGateOutput(String name) {
        super(name);
        setSerialResistance(Eln.SIGNAL_SERIAL_RESISTANCE);
    }

    public String plot(String str) {
        return Utils.plotSignal(getVoltage());
    }
}
