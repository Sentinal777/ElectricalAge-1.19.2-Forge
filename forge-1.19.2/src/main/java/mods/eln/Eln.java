package mods.eln;

import mods.eln.misc.FunctionTable;
import mods.eln.config.PortConfig;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Forge entry point and preserved public constants used by the upstream solver. */
@Mod(Eln.MODID)
public final class Eln {
    public static final String MODID = "eln";
    public static final Logger LOGGER = LogManager.getLogger("ElectricalAge");
    public static final Logger logger = LOGGER;
    public static final PortConfig config = new PortConfig();
    public static final double LVU=50, MVU=200, HVU=800, VVU=3200, SVU=5, SVUinv=0.2;
    public static final double SVII=0.00001, gateOutputCurrent=0.1, gateInputCurrent=0.00005;
    public static final double signalVoltageAcceptNegative=-0.5, signalVoltageAcceptPositive=5.5;
    public static final double SIGNAL_SERIAL_RESISTANCE=0.002;
    public static final int simMetricsPublishIntervalTicks=20;
    public static Eln instance = new Eln(false);
    public FunctionTable batteryVoltageFunctionTable = new FunctionTable(new double[]{0,0.9,1,1.025,1.04,1.05,2},1.2);
    private Eln(boolean ignored) {}
    public Eln() {
        instance=this;
        config.load();
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,
            mods.eln.config.ForgePortConfig.INSTANCE.getSpec());
        mods.eln.modern.Content.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(mods.eln.modern.CircuitManager.INSTANCE);
        mods.eln.modern.Network.register();
        LOGGER.info("Electrical Age: initializing the 1.19.2 port");
    }
}
