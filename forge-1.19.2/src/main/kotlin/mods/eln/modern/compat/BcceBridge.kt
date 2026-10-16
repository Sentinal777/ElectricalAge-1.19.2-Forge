package mods.eln.modern.compat

import buildcraft.api.v2.BuildCraftApi
import buildcraft.api.v2.BuildCraftServices
import buildcraft.lib.internal.mj.*
import mods.eln.modern.FaceNodeEntity
import net.minecraft.core.Direction
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.energy.IEnergyStorage
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction

/** Loaded only after buildcraftlib is detected. Verified against the published 8.0.23 Forge JAR. */
object BcceBridge {
    private fun ratio()=BuildCraftApi.service(BuildCraftServices.ENERGY).conversion().microMjPerFe().coerceAtLeast(1)
    fun supports(cap: Capability<*>)=cap==MjCapabilities.CAP_CONNECTOR || cap==MjCapabilities.CAP_RECEIVER ||
        cap==MjCapabilities.CAP_PASSIVE_PROVIDER || cap==MjCapabilities.CAP_READABLE
    fun adapter(node: FaceNodeEntity): Any = object : IMjReceiver, IMjPassiveProvider, IMjReadable {
        private fun storage(): IEnergyStorage?=node.getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null)
        override fun canConnect(other: IMjConnector)=!node.isRemoved
        override fun canReceive()=storage()?.canReceive() ?: false
        override fun getPowerRequested()=(storage()?.receiveEnergy(Int.MAX_VALUE,true) ?: 0).toLong()*ratio()
        override fun receivePower(microJoules: Long,action: FluidAction): Long {
            if(microJoules<=0)return microJoules.coerceAtLeast(0)
            val requested=(microJoules/ratio()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val accepted=storage()?.receiveEnergy(requested,action==FluidAction.SIMULATE) ?: 0
            return microJoules-accepted.toLong()*ratio()
        }
        override fun extractPower(min: Long,max: Long,doExtract: Boolean): Long {
            if(max<=0 || min>max)return 0
            val cap=storage() ?: return 0
            val request=(max/ratio()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val possible=cap.extractEnergy(request,true).toLong()*ratio()
            if(possible<min.coerceAtLeast(0))return 0
            return if(doExtract)cap.extractEnergy(request,false).toLong()*ratio() else possible
        }
        override fun getStored()=(storage()?.energyStored ?: 0).toLong()*ratio()
        override fun getCapacity()=(storage()?.maxEnergyStored ?: 0).toLong()*ratio()
    }
    /** Use MJ on native BCCE endpoints, sharing the same FE budget and buffer. */
    fun push(node: FaceNodeEntity,target: BlockEntity,side: Direction): Boolean {
        val receiver=target.getCapability(MjCapabilities.CAP_RECEIVER,side).resolve().orElse(null) ?: return false
        val storage=node.getCapability(ForgeCapabilities.ENERGY).resolve().orElse(null) ?: return true
        if(!storage.canExtract() || !receiver.canReceive())return true
        val offered=storage.extractEnergy(Int.MAX_VALUE,true).toLong()*ratio()
        if(offered<=0)return true
        val excess=receiver.receivePower(offered,FluidAction.SIMULATE).coerceIn(0,offered)
        val transferable=((offered-excess)/ratio()).toInt()
        val executable=transferable.toLong()*ratio()
        if(executable>0) {
            val left=receiver.receivePower(executable,FluidAction.EXECUTE).coerceIn(0,executable)
            // Debiting a partially accepted FE unit conservatively prevents an energy gain.
            val used=((executable-left+ratio()-1)/ratio()).toInt()
            storage.extractEnergy(used,false)
        }
        return true
    }
}
