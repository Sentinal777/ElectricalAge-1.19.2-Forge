package mods.eln.modern.compat

import dan200.computercraft.api.lua.*
import dan200.computercraft.api.peripheral.*
import mods.eln.modern.*
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.CapabilityManager
import net.minecraftforge.common.capabilities.CapabilityToken

/** Loaded only when ComputerCraft is installed; its API and runtime are never bundled in ELN. */
object CcProbeBridge {
    private val capability=CapabilityManager.get(object : CapabilityToken<IPeripheral>() {})
    fun supports(cap: Capability<*>)=cap===capability
    fun adapter(node: FaceNodeEntity): Any=Probe(node,ComputerProbes.part(node) ?: error("No Computer Probe"))
    private class Probe(private val node: FaceNodeEntity,private val part: PartState): IDynamicPeripheral {
        override fun getType()="ElnProbe"
        override fun getTarget(): Any=node
        override fun getMethodNames()=ComputerProbes.methods.copyOf()
        override fun equals(other: IPeripheral?)=other is Probe && other.node===node && other.part===part
        override fun callMethod(computer: IComputerAccess,context: ILuaContext,method: Int,arguments: IArguments): MethodResult {
            if(method !in ComputerProbes.methods.indices)throw LuaException("Unknown Computer Probe method")
            // Copy primitive arguments while the Lua argument object is valid; never retain it in a task.
            val side=if(method in 0..4)arguments.getString(0)else ""
            val value=if(method==2 || method==5)arguments.getDouble(1)else 0.0
            val text=if(method==0)arguments.getString(1)else if(method in setOf(5,6,8))arguments.getString(0)else ""
            val aggregation=if(method==8)arguments.optString(1,"bigger")else "bigger"
            return context.executeMainThreadTask {
                try { ComputerProbes.call(node,part,method,side,value,text,aggregation) }catch(e: IllegalArgumentException) { throw LuaException(e.message ?: "Invalid Computer Probe request") }
            }
        }
    }
}
