package mods.eln.modern.thermal

import mods.eln.modern.FaceNodeEntity
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.Fluids
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler
import net.minecraftforge.fluids.capability.templates.FluidTank
import net.minecraftforge.registries.ForgeRegistries

data class HeatFluidPair(val input: Fluid,val output: Fluid,val joulesPerOutputMb: Double,
    val maximumInputMb: Int,val ratio: Int=1,val minimum: Double?=null,val maximum: Double?=null)
object HeatFluidPairs {
    private fun find(vararg names: String)=ForgeRegistries.FLUIDS.values.firstOrNull {
        val key=ForgeRegistries.FLUIDS.getKey(it)
        key!=null && key.path in names && (key.path.startsWith("ic2") || key.path=="steam" || key.namespace in setOf("ic2","ic2classic","industrialcraft"))
    }
    val all: List<HeatFluidPair> by lazy {
        val result=mutableListOf<HeatFluidPair>()
        val hot=find("ic2hotcoolant","hot_coolant");val cold=find("ic2coolant","coolant")
        if(hot!=null && cold!=null) {
            result.add(HeatFluidPair(cold,hot,-1920.0/7,9,minimum=300.0))
            result.add(HeatFluidPair(hot,cold,1920.0/7,9))
        }
        find("ic2hotwater","hot_water")?.let { result.add(HeatFluidPair(it,Fluids.WATER,1/.45/2,36,minimum=26.85,maximum=76.85)) }
        val steam=find("steam","ic2steam") ?: SteamFluids.steam.get()
        result.add(HeatFluidPair(Fluids.WATER,steam,-1/.45,36,10,minimum=100.0))
        result
    }
}

/** Original 1000 mB DOWN input / UP output tanks, including unavailable-mod fluid preservation. */
class HeatExchangerStorage(private val node: FaceNodeEntity) {
    private var unknownInput=CompoundTag();private var unknownOutput=CompoundTag()
    val input=object : FluidTank(1000) {
        override fun isFluidValid(stack: FluidStack)=unknownInput.isEmpty && HeatFluidPairs.all.any { it.input.isSame(stack.fluid) }
        override fun onContentsChanged() { node.setChanged() }
    }
    val output=object : FluidTank(1000) {
        override fun isFluidValid(stack: FluidStack)=unknownOutput.isEmpty && HeatFluidPairs.all.any { it.output.isSame(stack.fluid) }
        override fun onContentsChanged() { node.setChanged() }
    }
    val unknown get()=!unknownInput.isEmpty || !unknownOutput.isEmpty
    fun handler(side: Direction?): IFluidHandler? {
        val tanks=when(side) { Direction.DOWN -> listOf(input);Direction.UP -> listOf(output);null -> listOf(input,output);else -> return null }
        return object : IFluidHandler {
            override fun getTanks()=tanks.size
            override fun getFluidInTank(tank: Int)=tanks.getOrNull(tank)?.fluid ?: FluidStack.EMPTY
            override fun getTankCapacity(tank: Int)=tanks.getOrNull(tank)?.capacity ?: 0
            override fun isFluidValid(tank: Int,stack: FluidStack)=tanks.getOrNull(tank)?.isFluidValid(stack) ?: false
            override fun fill(stack: FluidStack,action: IFluidHandler.FluidAction): Int {
                for(tank in tanks)if(tank.isFluidValid(stack))return tank.fill(stack,action)
                return 0
            }
            override fun drain(stack: FluidStack,action: IFluidHandler.FluidAction): FluidStack {
                for(tank in tanks)if(!tank.isEmpty && tank.fluid.isFluidEqual(stack))return tank.drain(stack,action)
                return FluidStack.EMPTY
            }
            override fun drain(amount: Int,action: IFluidHandler.FluidAction): FluidStack {
                for(tank in tanks)if(!tank.isEmpty)return tank.drain(amount,action)
                return FluidStack.EMPTY
            }
        }
    }
    fun save()=CompoundTag().also {
        it.put("input",if(unknownInput.isEmpty)input.writeToNBT(CompoundTag())else unknownInput.copy())
        it.put("output",if(unknownOutput.isEmpty)output.writeToNBT(CompoundTag())else unknownOutput.copy())
    }
    fun load(t: CompoundTag) {
        val i=t.getCompound("input").copy();val o=t.getCompound("output").copy()
        i.putInt("Amount",i.getInt("Amount").coerceIn(0,input.capacity))
        o.putInt("Amount",o.getInt("Amount").coerceIn(0,output.capacity))
        input.readFromNBT(i);output.readFromNBT(o)
        unknownInput=if(input.isEmpty && i.getInt("Amount")>0)i.copy()else CompoundTag()
        unknownOutput=if(output.isEmpty && o.getInt("Amount")>0)o.copy()else CompoundTag()
    }
}
