package mods.eln.modern

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.Connection
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.energy.IEnergyStorage
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.ItemStackHandler
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler
import net.minecraftforge.fluids.capability.templates.FluidTank
import mods.eln.config.ForgePortConfig

class FaceNodeEntity(pos: BlockPos, state: BlockState) : BlockEntity(Content.nodeEntity.get(), pos, state) {
    private var lastLightCheck=Long.MIN_VALUE
    val parts = arrayOfNulls<PartState>(6)
    var coverStateData=CompoundTag()
    var coverItemData=CompoundTag()
    var gridRenderLinks=ListTag()
    var gridFailed=false
    override fun getRenderBoundingBox(): net.minecraft.world.phys.AABB {
        val grid=parts.filterNotNull().mapNotNull { it.definition.grid }.maxOfOrNull { it.range } ?: 0.0
        return if(grid>0)net.minecraft.world.phys.AABB(blockPos).inflate(grid+16)
            else parts.filterNotNull().fold(net.minecraft.world.phys.AABB(blockPos)) { box,p ->
                val body=box.minmax(PartGeometry.body(p).move(blockPos))
                if(p.definition.behavior==Behavior.AUTO_MINER)body.minmax(net.minecraft.world.phys.AABB(blockPos.below(AutoMiners.depth(p)+2)))else body
            }
    }
    private var unknownParts = ListTag()
    private var unknownFluid=CompoundTag()
    val exchanger=mods.eln.modern.thermal.HeatExchangerStorage(this)
    private val exchangerCaps=hashMapOf<Direction?,LazyOptional<IFluidHandler>>()
    private fun exchangerCapability(side: Direction?)=exchangerCaps.getOrPut(side) {
        exchanger.handler(side)?.let { handler -> LazyOptional.of { handler } } ?: LazyOptional.empty()
    }
    val isMachine get() = blockState.block == Content.machine.get() || blockState.block==Content.computerProbe.get()
    val energyBuffer=EnergyBuffer()
    var feStored: Int
        get()=energyBuffer.stored
        set(value) { energyBuffer.energy=value.toDouble() }
    private var transferTick=Long.MIN_VALUE
    private var receivedThisTick=0
    private var extractedThisTick=0
    private fun resetTransfer() {
        val tick=level?.gameTime ?: 0
        if(tick!=transferTick) { transferTick=tick;receivedThisTick=0;extractedThisTick=0 }
    }
    private fun converter()=parts.filterNotNull().firstOrNull { it.definition.behavior==Behavior.FE_CONVERTER && it.enabled && !it.tripped && !isRemoved }
    val tank=object : FluidTank(2000) {
        override fun isFluidValid(stack: FluidStack)=unknownFluid.isEmpty && parts.filterNotNull().any { it.definition.fluidMachine && FuelFluids.accepts(it.definition.behavior,stack.fluid) }
        override fun onContentsChanged() { setChanged() }
    }
    private var fluidCapability: LazyOptional<IFluidHandler> = LazyOptional.of { tank }
    val inventory = object : ItemStackHandler(6) {
        override fun onContentsChanged(slot: Int) { setChanged() }
        override fun isItemValid(slot: Int, stack: ItemStack) = if(slot==1)parts.filterNotNull().any { it.definition.processing } && (stack.item as? MaterialItem)?.definition?.kind=="MACHINE_BOOSTER" else slot == 0 && processInput(stack)
        override fun getSlotLimit(slot: Int)=if(slot==1)5 else 64
    }
    private fun automation(input: Boolean,output: Boolean) = object : IItemHandler {
        override fun getSlots() = if(parts.filterNotNull().any { it.definition.behavior==Behavior.ROLLING_SHAFT })2 else 5
        override fun getStackInSlot(slot: Int) = if(slot in 0 until getSlots())inventory.getStackInSlot(if(slot==0)0 else slot+1)else ItemStack.EMPTY
        override fun insertItem(slot: Int, stack: ItemStack, simulate: Boolean) =
            if(input && slot==0)inventory.insertItem(0,stack,simulate) else stack
        override fun extractItem(slot: Int, amount: Int, simulate: Boolean) =
            if(output && slot in 1 until getSlots())inventory.extractItem(slot+1,amount,simulate) else ItemStack.EMPTY
        override fun getSlotLimit(slot: Int) = 64
        override fun isItemValid(slot: Int, stack: ItemStack) = input && slot==0 && processInput(stack)
    }
    private fun processInput(stack: ItemStack): Boolean {
        val world=level ?: return false
        return parts.filterNotNull().any { part ->
            val kind=part.definition.behavior
            if(kind==Behavior.FURNACE)world.recipeManager.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING,
                net.minecraft.world.SimpleContainer(stack),world).isPresent
            else MachineRecipes.find(if(kind==Behavior.ROLLING_SHAFT)Behavior.PLATE_MACHINE else kind,stack,world,false)!=null
        }
    }
    private var itemCapability: LazyOptional<IItemHandler> = LazyOptional.of { automation(true,true) }
    private var inputCapability: LazyOptional<IItemHandler> = LazyOptional.of { automation(true,false) }
    private var outputCapability: LazyOptional<IItemHandler> = LazyOptional.of { automation(false,true) }
    private fun heatFuelHandler()=object : IItemHandler {
        private fun fuel()=parts.filterNotNull().firstOrNull { it.definition.behavior in setOf(Behavior.HEAT_FURNACE,Behavior.INCUBATOR) }?.let { PartInventory.get(this@FaceNodeEntity,it) }
        override fun getSlots()=1
        override fun getStackInSlot(slot: Int)=if(slot==0)fuel()?.getStackInSlot(0) ?: ItemStack.EMPTY else ItemStack.EMPTY
        override fun insertItem(slot: Int,stack: ItemStack,simulate: Boolean)=if(slot==0)fuel()?.insertItem(0,stack,simulate) ?: stack else stack
        override fun extractItem(slot: Int,amount: Int,simulate: Boolean)=ItemStack.EMPTY
        override fun getSlotLimit(slot: Int)=if(slot==0)64 else 0
        override fun isItemValid(slot: Int,stack: ItemStack)=slot==0 && (fuel()?.isItemValid(0,stack) ?: false)
    }
    private var meterCapability: LazyOptional<IItemHandler> = LazyOptional.of { EnergyMeterControls.handler(this) }
    private var processorCapability: LazyOptional<IItemHandler> = LazyOptional.of { SignalProcessors.handler(this) }
    private var loggerCapability: LazyOptional<IItemHandler> = LazyOptional.of { DataLoggerAutomation.handler(this) }
    private var turretCapability: LazyOptional<IItemHandler> = LazyOptional.of { TurretAutomation.handler(this) }
    private var autoMinerCapability: LazyOptional<IItemHandler> = LazyOptional.of { AutoMinerAutomation.handler(this) }
    private var fabricatorCapability: LazyOptional<IItemHandler> = LazyOptional.of { FabricatorAutomation.handler(this) }
    private var heatFuelCapability: LazyOptional<IItemHandler> = LazyOptional.of { heatFuelHandler() }
    private val energyStorage = object : IEnergyStorage {
        override fun receiveEnergy(maxReceive: Int, simulate: Boolean): Int {
            if(!canReceive())return 0
            resetTransfer()
            val accepted=minOf(maxReceive.coerceAtLeast(0),energyBuffer.capacity-energyBuffer.stored-1,
                (ForgePortConfig.feTransferPerTick.get()-receivedThisTick).coerceAtLeast(0)).coerceAtLeast(0)
            if(!simulate && accepted>0) { energyBuffer.add(accepted.toDouble());receivedThisTick+=accepted;setChanged() }
            return accepted
        }
        override fun extractEnergy(maxExtract: Int, simulate: Boolean): Int {
            if(!canExtract())return 0
            resetTransfer()
            val available=minOf(maxExtract.coerceAtLeast(0),energyBuffer.stored,
                (ForgePortConfig.feTransferPerTick.get()-extractedThisTick).coerceAtLeast(0))
            if (!simulate && available > 0) { energyBuffer.remove(available.toDouble());extractedThisTick+=available;setChanged() }
            return available
        }
        override fun getEnergyStored() = feStored
        override fun getMaxEnergyStored() = 100000
        override fun canExtract() = converter()?.value?.let { it<0.5 } ?: false
        override fun canReceive() = converter()?.value?.let { it>=0.5 } ?: false
    }
    private var energyCapability: LazyOptional<IEnergyStorage> = LazyOptional.of { energyStorage }
    private var bcceCapability: LazyOptional<Any> = LazyOptional.empty()
    private var ccProbeCapability: LazyOptional<Any> = LazyOptional.empty()
    private var ccProbeOwner: PartState?=null
    override fun <T : Any?> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if(!isRemoved && IntegrationSupport.computerCraft() && ComputerProbes.part(this)!=null && mods.eln.modern.compat.CcProbeBridge.supports(cap)) {
            val current=ComputerProbes.part(this)
            if(ccProbeOwner!==current) { ccProbeCapability.invalidate();ccProbeCapability=LazyOptional.empty();ccProbeOwner=current }
            if(!ccProbeCapability.isPresent)ccProbeCapability=LazyOptional.of { mods.eln.modern.compat.CcProbeBridge.adapter(this) }
            return ccProbeCapability.cast()
        }
        if(!isRemoved && cap==ForgeCapabilities.ITEM_HANDLER && parts.filterNotNull().any { it.definition.behavior==Behavior.PROCESSOR })return processorCapability.cast()
        if(!isRemoved && cap==ForgeCapabilities.ITEM_HANDLER && parts.filterNotNull().any { it.definition.behavior==Behavior.ENERGY_METER })return meterCapability.cast()
        if(!isRemoved && cap==ForgeCapabilities.ITEM_HANDLER && parts.filterNotNull().any { it.definition.behavior==Behavior.LOGGER })return loggerCapability.cast()
        if (!isRemoved && isMachine) {
            if(cap==ForgeCapabilities.ITEM_HANDLER && parts.filterNotNull().any { it.definition.behavior==Behavior.TURRET })return turretCapability.cast()
            if(cap==ForgeCapabilities.ITEM_HANDLER && parts.filterNotNull().any { it.definition.behavior==Behavior.AUTO_MINER })return autoMinerCapability.cast()
            if(cap==ForgeCapabilities.ITEM_HANDLER && parts.filterNotNull().any { it.definition.behavior==Behavior.FABRICATOR })return fabricatorCapability.cast()
            if(cap==ForgeCapabilities.FLUID_HANDLER && parts.filterNotNull().any { it.definition.behavior==Behavior.HEAT_EXCHANGER })return exchangerCapability(side).cast()
            if(cap==ForgeCapabilities.ITEM_HANDLER && parts.filterNotNull().any { it.definition.behavior in setOf(Behavior.HEAT_FURNACE,Behavior.INCUBATOR) })return heatFuelCapability.cast()
            if (cap == ForgeCapabilities.ITEM_HANDLER && parts.filterNotNull().any {
                it.definition.processing || it.definition.behavior in setOf(Behavior.FURNACE,Behavior.ROLLING_SHAFT)
            }) return (if(side==null)itemCapability else if(side==Direction.UP)inputCapability else outputCapability).cast()
            if (cap == ForgeCapabilities.ENERGY && parts.filterNotNull().any { it.definition.behavior == Behavior.FE_CONVERTER }) return energyCapability.cast()
            if (cap == ForgeCapabilities.FLUID_HANDLER && parts.filterNotNull().any { it.definition.fluidMachine }) return fluidCapability.cast()
            if(parts.filterNotNull().any { it.definition.behavior==Behavior.FE_CONVERTER } &&
                IntegrationSupport.buildCraft() && mods.eln.modern.compat.BcceBridge.supports(cap)) {
                if(!bcceCapability.isPresent)bcceCapability=LazyOptional.of { mods.eln.modern.compat.BcceBridge.adapter(this) }
                return bcceCapability.cast()
            }
        }
        return super.getCapability(cap, side)
    }
    override fun invalidateCaps() {
        super.invalidateCaps(); itemCapability.invalidate(); inputCapability.invalidate();outputCapability.invalidate()
        exchangerCaps.values.forEach { it.invalidate() };exchangerCaps.clear()
        processorCapability.invalidate();meterCapability.invalidate();loggerCapability.invalidate();heatFuelCapability.invalidate();fabricatorCapability.invalidate();autoMinerCapability.invalidate();turretCapability.invalidate()
        energyCapability.invalidate();fluidCapability.invalidate()
        ccProbeCapability.invalidate();ccProbeOwner=null
        bcceCapability.invalidate()
    }
    override fun reviveCaps() {
        super.reviveCaps()
        processorCapability=LazyOptional.of { SignalProcessors.handler(this) }
        meterCapability=LazyOptional.of { EnergyMeterControls.handler(this) }
        loggerCapability=LazyOptional.of { DataLoggerAutomation.handler(this) }
        turretCapability=LazyOptional.of { TurretAutomation.handler(this) }
        autoMinerCapability=LazyOptional.of { AutoMinerAutomation.handler(this) }
        heatFuelCapability=LazyOptional.of { heatFuelHandler() };fabricatorCapability=LazyOptional.of { FabricatorAutomation.handler(this) }
        itemCapability = LazyOptional.of { automation(true,true) };inputCapability=LazyOptional.of { automation(true,false) }
        outputCapability=LazyOptional.of { automation(false,true) };energyCapability = LazyOptional.of { energyStorage }
        fluidCapability=LazyOptional.of { tank }
        ccProbeCapability=LazyOptional.empty();ccProbeOwner=null
        bcceCapability=LazyOptional.empty()
    }
    override fun onLoad() { super.onLoad(); if (level?.isClientSide == false) { CircuitManager.attach(this);refreshLight() } }
    override fun setRemoved() { if (level?.isClientSide == false) CircuitManager.detach(this); super.setRemoved() }
    fun changedTopology() {
        updateFluidCapacity()
        setChanged()
        if (level?.isClientSide == false) { CircuitManager.attach(this); CircuitManager.invalidate(this); sync() }
    }
    private fun updateFluidCapacity() {
        tank.setCapacity(parts.filterNotNull().filter { it.definition.fluidMachine }.maxOfOrNull { it.definition.tankCapacity.takeIf { size -> size>0 } ?: 2000 } ?: 2000)
    }
    fun sync() {
        val world=level ?: return
        if(world is net.minecraft.server.level.ServerLevel && !world.chunkSource.isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(blockPos)))return
        refreshLight()
        world.sendBlockUpdated(worldPosition,blockState,blockState,3)
    }
    /** Lighting workers read immutable block state; ServerLevel block-entity reads are thread-bound. */
    fun refreshLight() {
        val world=level as? net.minecraft.server.level.ServerLevel ?: return
        if(isRemoved || world.chunkSource.getChunkNow(blockPos.x shr 4,blockPos.z shr 4)?.getBlockEntity(blockPos)!==this)return
        val state=world.getBlockState(blockPos);val property=net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL
        if(state.block !is NodeBlock || !state.hasProperty(property))return
        val partLight=parts.filterNotNull().filter { it.definition.behavior in setOf(Behavior.FESTIVE,Behavior.ALARM,Behavior.EMERGENCY_LAMP,Behavior.FLOODLIGHT) || it.definition.behavior==Behavior.LAMP && it.thermalState.getBoolean("lampSpotHere") }.maxOfOrNull { it.signal }?.coerceIn(0,15) ?: 0
        val cover=NodeCovers.state(this)
        val light=maxOf(partLight,cover?.getLightEmission(world,blockPos) ?: 0).coerceIn(0,15)
        val next=state.setValue(property,light).setValue(NodeBlock.COVERED,cover!=null).setValue(NodeBlock.COVER_OPACITY,cover?.getLightBlock(world,blockPos)?.coerceIn(0,15) ?: 0)
        val changed=next!=state
        if(changed)world.setBlock(blockPos,next,3)
        if(light>0 && (lastLightCheck==Long.MIN_VALUE || world.gameTime>=lastLightCheck+5)) {
            lastLightCheck=world.gameTime
            // A carrier may become the first emitter in an empty high section while chunk lighting is pending.
            world.chunkSource.lightEngine.updateSectionStatus(net.minecraft.core.SectionPos.of(blockPos),false)
            world.chunkSource.lightEngine.enableLightSources(net.minecraft.world.level.ChunkPos(blockPos),true)
            world.chunkSource.lightEngine.checkBlock(blockPos)
            mods.eln.modern.lighting.LightRecovery.observe(world,blockPos,light)
        } else if(changed)world.chunkSource.lightEngine.checkBlock(blockPos)
        if(light==0)mods.eln.modern.lighting.LightRecovery.forget(world,blockPos)
    }
    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        tag.putInt("elnDataVersion", 4)
        if(!coverStateData.isEmpty) { tag.put("nodeCoverState",coverStateData.copy());tag.put("nodeCoverItem",coverItemData.copy()) }
        val list = unknownParts.copy() as ListTag
        parts.filterNotNull().forEach { list.add(it.save()) }
        tag.put("parts", list); tag.put("inventory", inventory.serializeNBT()); tag.putInt("feStored", feStored)
        tag.put("gridRenderLinks",gridRenderLinks.copy())
        tag.putDouble("feEnergy",energyBuffer.energy);tag.put("fluid",if(unknownFluid.isEmpty)tank.writeToNBT(CompoundTag()) else unknownFluid.copy())
        if(parts.filterNotNull().any { it.definition.behavior==Behavior.HEAT_EXCHANGER } || exchanger.unknown || !exchanger.input.isEmpty || !exchanger.output.isEmpty)tag.put("exchanger",exchanger.save())
    }
    override fun load(tag: CompoundTag) {
        ccProbeCapability.invalidate();ccProbeCapability=LazyOptional.empty();ccProbeOwner=null
        super.load(tag); parts.fill(null); unknownParts = ListTag()
        coverStateData=tag.getCompound("nodeCoverState").copy();coverItemData=tag.getCompound("nodeCoverItem").copy()
        gridRenderLinks=tag.getList("gridRenderLinks",10).copy() as ListTag
        tag.getList("parts", 10).forEach { raw ->
            val entry = raw as CompoundTag
            val part = PartState.load(entry)
            if (part != null && parts[part.face.get3DDataValue()] == null) parts[part.face.get3DDataValue()] = part
            else unknownParts.add(entry.copy())
        }
        if (tag.contains("inventory")) inventory.deserializeNBT(tag.getCompound("inventory").copy().also { it.putInt("Size",6) })
        energyBuffer.energy=if(tag.contains("feEnergy"))tag.getDouble("feEnergy") else tag.getInt("feStored").toDouble()
        val savedFluid=tag.getCompound("fluid")
        updateFluidCapacity()
        tank.readFromNBT(savedFluid)
        unknownFluid=if(tank.isEmpty && savedFluid.getInt("Amount")>0)savedFluid.copy() else CompoundTag()
        exchanger.load(tag.getCompound("exchanger"))
        if(level?.isClientSide==false && !isRemoved) { CircuitManager.attach(this);CircuitManager.invalidate(this);refreshLight() }
    }
    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()
    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket = ClientboundBlockEntityDataPacket.create(this)
    override fun onDataPacket(connection: Connection, packet: ClientboundBlockEntityDataPacket) {
        packet.tag?.let { load(it) }
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }
    fun partAt(hit: Vec3,eye: Vec3?=null): PartState? {
        if(NodeCovers.present(this) || blockState.getValue(NodeBlock.COVERED))return null
        if(eye!=null) {
            val end=hit.add(hit.subtract(eye).normalize().scale(.01))
            val nearest=parts.filterNotNull().mapNotNull { part ->
                PartGeometry.shape(part).clip(eye,end,worldPosition)?.let { part to eye.distanceToSqr(it.location) }
            }.minByOrNull { it.second }
            if(nearest!=null)return nearest.first
        }
        val local=hit.subtract(Vec3.atLowerCornerOf(worldPosition))
        return parts.filterNotNull().minByOrNull { PartGeometry.distanceTo(it,local) }
    }
    fun drop(part: PartState): ItemStack {
        if(part.definition.behavior==Behavior.AUTO_MINER) {
            AutoMiners.releasePipes(this,part)
            for(pending in AutoMiners.pending(part))net.minecraft.world.level.block.Block.popResource(level!!,blockPos,pending)
            AutoMiners.pending(part,emptyList())
        }
        val stack = ItemStack(Content.partItems[part.definition.id]!!.get())
        stack.orCreateTag.put("component", part.save())
        if(part.definition.machine) {
            val storage=CompoundTag()
            if(part.definition.fluidMachine)storage.put("fluid",if(unknownFluid.isEmpty)tank.writeToNBT(CompoundTag())else unknownFluid.copy())
            if(part.definition.behavior==Behavior.FE_CONVERTER)storage.putDouble("feEnergy",energyBuffer.energy)
            if(part.definition.behavior==Behavior.HEAT_EXCHANGER)storage.put("exchanger",exchanger.save())
            if(!storage.isEmpty)stack.orCreateTag.put("machineStorage",storage)
        }
        return stack
    }
    /** Machine inventories still drop separately; carrier storage belongs to this one machine item. */
    fun restoreItemStorage(part: PartState,storage: CompoundTag) {
        if(!part.definition.machine)return
        updateFluidCapacity()
        if(part.definition.fluidMachine && storage.contains("fluid")) {
            val fluid=storage.getCompound("fluid").copy()
            fluid.putInt("Amount",fluid.getInt("Amount").coerceIn(0,tank.capacity))
            tank.readFromNBT(fluid)
            unknownFluid=if(tank.isEmpty && fluid.getInt("Amount")>0)fluid else CompoundTag()
        }
        if(part.definition.behavior==Behavior.FE_CONVERTER && storage.contains("feEnergy"))energyBuffer.energy=storage.getDouble("feEnergy")
        if(part.definition.behavior==Behavior.HEAT_EXCHANGER && storage.contains("exchanger"))exchanger.load(storage.getCompound("exchanger"))
        setChanged()
    }
    /** Consume fractional fuel energy without repeatedly rounding fluid usage up. */
    fun reserveFuel(part: PartState,requested: Double) {
        if(!requested.isFinite() || requested<=0)return
        if(part.fuelEnergy<requested && !tank.isEmpty) {
            val perMb=FuelFluids.joulesPerMb(tank.fluid.fluid)
            if(perMb>0) {
                val amount=kotlin.math.ceil((requested-part.fuelEnergy)/perMb).toInt().coerceAtMost(tank.fluidAmount)
                val removed=tank.drain(amount,IFluidHandler.FluidAction.EXECUTE)
                part.fuelEnergy+=removed.amount*perMb
            }
        }
    }
    fun consumeFuel(part: PartState,requested: Double): Double {
        if(!requested.isFinite() || requested<=0)return 0.0
        reserveFuel(part,requested)
        val used=minOf(requested,part.fuelEnergy);part.fuelEnergy-=used;setChanged();return used
    }
    fun transferEnergy() {
        val part=converter() ?: return
        val world=level ?: return
        if(world.isClientSide)return
        for(side in Direction.values()) {
            val target=world.getBlockEntity(blockPos.relative(side)) ?: continue
            if(part.value<.5 && IntegrationSupport.buildCraft() &&
                mods.eln.modern.compat.BcceBridge.push(this,target,side.opposite))continue
            val other=target.getCapability(ForgeCapabilities.ENERGY,side.opposite).orElse(null) ?: continue
            if(part.value<0.5 && other.canReceive()) {
                val offer=energyStorage.extractEnergy(ForgePortConfig.feTransferPerTick.get(),true)
                val sent=other.receiveEnergy(offer,false).coerceIn(0,offer)
                energyStorage.extractEnergy(sent,false)
            } else if(part.value>=0.5 && other.canExtract()) {
                val space=energyStorage.receiveEnergy(ForgePortConfig.feTransferPerTick.get(),true)
                val received=other.extractEnergy(space,false).coerceIn(0,space)
                energyStorage.receiveEnergy(received,false)
            }
        }
    }
}
