package mods.eln.modern

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.*
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.*
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Material
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.shapes.*
import net.minecraftforge.network.NetworkHooks

// The six-face shape depends on block-entity data, which arrives after the block state.
// Caching the initially empty shape also gives the renderer an empty culling box.
class NodeBlock(private val machine: Boolean,private val fullCube: Boolean=false) : BaseEntityBlock(
    (if(fullCube)Properties.of(Material.ICE_SOLID).strength(1.5f)else Properties.of(Material.METAL).strength(1.5f).noOcclusion())
        .dynamicShape().lightLevel { it.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL) }) {
    init { registerDefaultState(stateDefinition.any().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL,0).setValue(COVERED,false).setValue(COVER_OPACITY,0)) }
    override fun createBlockStateDefinition(builder: net.minecraft.world.level.block.state.StateDefinition.Builder<Block,BlockState>) { builder.add(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL,COVERED,COVER_OPACITY) }
    override fun getLightBlock(state: BlockState,level: BlockGetter,pos: BlockPos)=if(fullCube)15 else if(state.getValue(COVERED))state.getValue(COVER_OPACITY)else 0
    override fun getOcclusionShape(state: BlockState,level: BlockGetter,pos: BlockPos): VoxelShape=if(fullCube || state.getValue(COVERED))Shapes.block()else Shapes.empty()
    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = FaceNodeEntity(pos, state)
    override fun <T: BlockEntity> getTicker(level: Level,state: BlockState,type: net.minecraft.world.level.block.entity.BlockEntityType<T>): net.minecraft.world.level.block.entity.BlockEntityTicker<T>? =
        if(level.isClientSide && type==Content.nodeEntity.get())net.minecraft.world.level.block.entity.BlockEntityTicker { _,_,_,entity ->
            (entity as? FaceNodeEntity)?.let { ClientNodeHooks.tick?.invoke(it) }
        }else null
    override fun getRenderShape(state: BlockState) = RenderShape.ENTITYBLOCK_ANIMATED
    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        if(fullCube || state.getValue(COVERED))return Shapes.block()
        val node = level.getBlockEntity(pos) as? FaceNodeEntity ?: return Shapes.empty()
        return node.parts.filterNotNull().fold(Shapes.empty()) { shape,part ->
            val body=PartGeometry.shape(part)
            val local=if(part.definition.shaftPorts.orEmpty().isNotEmpty() || part.definition.powerPorts.orEmpty().isNotEmpty())Shapes.join(body,Shapes.block(),BooleanOp.AND)else body
            Shapes.or(shape,local)
        }
    }
    override fun getCloneItemStack(state: BlockState,hit: HitResult,level: BlockGetter,pos: BlockPos,player: Player): ItemStack {
        val node=level.getBlockEntity(pos) as? FaceNodeEntity ?: return ItemStack.EMPTY
        if(NodeCovers.present(node))return NodeCovers.stack(node)
        return node.partAt(hit.location,player.eyePosition)?.let { ItemStack(Content.partItems.getValue(it.definition.id).get()) } ?: ItemStack.EMPTY
    }
    override fun use(state: BlockState, level: Level, pos: BlockPos, player: Player, hand: InteractionHand, hit: BlockHitResult): InteractionResult {
        val entity = level.getBlockEntity(pos) as? FaceNodeEntity ?: return InteractionResult.PASS
        if(!level.mayInteract(player,pos))return InteractionResult.FAIL
        NodeCovers.interact(entity,player,hand,hit)?.let { return it }
        val part = entity.partAt(hit.location,player.eyePosition) ?: return InteractionResult.PASS
        if (player.getItemInHand(hand).item is PartItem) return InteractionResult.PASS
        if (!level.mayInteract(player,pos)) return InteractionResult.FAIL
        if((player.getItemInHand(hand).item as? MaterialItem)?.definition?.kind in PortableInstruments.kinds)return PortableInstruments.use(net.minecraft.world.item.context.UseOnContext(player,hand,hit))
        if (!level.isClientSide) {
            val held=player.getItemInHand(hand)
            if(Brushes.paint(entity,part,player,held))return InteractionResult.SUCCESS
            if(part.definition.behavior==Behavior.ENERGY_METER && PartInventory.takeCable(entity,part,player,held))return InteractionResult.SUCCESS
            if(part.definition.behavior==Behavior.ENERGY_METER && held.item==Content.multimeter.get()) { player.displayClientMessage(EnergyMeterControls.reading(part),true);return InteractionResult.SUCCESS }
            if(part.definition.behavior==Behavior.TURRET && TurretAutomation.interact(entity,part,player,held))return InteractionResult.SUCCESS
            if(part.definition.behavior==Behavior.FUSE_HOLDER && !player.isShiftKeyDown && ProtectionSettings.fuseInteraction(entity,part,player,held))return InteractionResult.SUCCESS
            if(part.definition.behavior in setOf(Behavior.BATTERY_CHARGER,Behavior.ENVIRONMENT_SENSOR,Behavior.WATCH,Behavior.AUTO_MINER) || part.definition.behavior in setOf(Behavior.LAMP,Behavior.FLOODLIGHT) && (held.item as? MaterialItem)?.definition?.bulb!=null) {
                if(PartInventory.takeItem(entity,part,player,held))return InteractionResult.SUCCESS
            }
            if(part.definition.fluidMachine && net.minecraftforge.fluids.FluidUtil.interactWithFluidHandler(player,hand,level,pos,hit.direction))return InteractionResult.SUCCESS
            if(part.definition.behavior==Behavior.PASSIVE_COOLER && (held.`is`(net.minecraft.world.item.Items.WATER_BUCKET) || held.`is`(net.minecraft.world.item.Items.ICE))) {
                val water=held.`is`(net.minecraft.world.item.Items.WATER_BUCKET)
                val previous=if(part.thermalState.contains("coolingFactor"))part.thermalState.getDouble("coolingFactor")else 1.0
                part.thermalState.putDouble("coolingFactor",previous*(if(water).5 else .2))
                if(!player.abilities.instabuild) { if(water)player.setItemInHand(hand,ItemStack(net.minecraft.world.item.Items.BUCKET)) else held.shrink(1) }
                entity.setChanged();entity.sync();return InteractionResult.SUCCESS
            }
            val dye=held.item as? net.minecraft.world.item.DyeItem
            if((part.definition.id=="digital_display" || part.definition.lamp?.paintable==true) && dye!=null) {
                part.displayColor=dye.dyeColor.fireworkColor
                if(!player.abilities.instabuild)held.shrink(1)
                entity.setChanged();entity.sync();return InteractionResult.SUCCESS
            }
            if(part.definition.fluidMachine && net.minecraftforge.fluids.FluidUtil.interactWithFluidHandler(player,hand,level,pos,hit.direction)) {
                entity.sync();return InteractionResult.SUCCESS
            }
            if(part.definition.behavior==Behavior.RESIN_COLLECTOR && held.item !in setOf(Content.wrench.get(),Content.multimeter.get(),Content.thermometer.get())) {
                ResinCollectorRuntime.harvest(entity,part,player);return InteractionResult.CONSUME
            }
            when (player.getItemInHand(hand).item) {
                Content.wrench.get() -> {
                    if (player.isShiftKeyDown) {
                        if(part.definition.multiblock) {
                            mods.eln.modern.grid.GridNetwork.removeAt(level as net.minecraft.server.level.ServerLevel,pos,!player.abilities.instabuild)
                            mods.eln.modern.grid.GridMultiblock.remove(entity,part)
                        }
                        if (!player.abilities.instabuild) popResource(level, pos, entity.drop(part))
                        entity.parts[part.face.get3DDataValue()] = null
                        if (entity.parts.all { it == null }) level.removeBlock(pos, false) else entity.changedTopology()
                    } else {
                        if(part.definition.behavior in setOf(Behavior.CABLE,Behavior.SIGNAL_BUS)) {
                            PaintedWiring.toggle(entity,part)
                            player.displayClientMessage(Component.translatable(if(PaintedWiring.cares(part))"message.eln.color_care_on" else "message.eln.color_care_off"),true)
                            return InteractionResult.SUCCESS
                        }
                        if(part.definition.behavior==Behavior.AUTO_MINER && AutoMiners.depth(part)>0)return InteractionResult.SUCCESS
                        if(part.definition.behavior==Behavior.FABRICATOR)return InteractionResult.SUCCESS
                        if(part.definition.behavior==Behavior.POWER_SOCKET)return InteractionResult.SUCCESS
                        if(part.definition.behavior==Behavior.WIND_TURBINE) {
                            part.gridState.putInt("windCableOrientation",(part.gridState.getInt("windCableOrientation")+1)%4)
                            entity.changedTopology();return InteractionResult.SUCCESS
                        }
                        if(part.definition.behavior==Behavior.RESIN_COLLECTOR)return InteractionResult.SUCCESS
                        if(part.definition.multiblock) {
                            if(!mods.eln.modern.grid.GridMultiblock.rotate(entity,part,player))player.displayClientMessage(Component.translatable("message.eln.grid.obstructed"),true)
                            return InteractionResult.SUCCESS
                        }
                        val previous=part.rotation;part.rotation=(previous+1)%4
                        if(entity.parts.filterNotNull().any { it!==part && PartGeometry.overlapsBody(part,it) })part.rotation=previous
                        else { if(part.definition.mechanical)part.shaftJoinPending=true;entity.changedTopology() }
                    }
                }
                Content.multimeter.get() -> if(part.definition.behavior==Behavior.FESTIVE)player.displayClientMessage(Component.translatable("tooltip.eln.festive"),true)else if(part.definition.behavior==Behavior.INVENTORY_SCANNER)player.displayClientMessage(Component.translatable("message.eln.inventory_scanner",Component.translatable(if(part.gridState.getInt("scannerMode")==1)"gui.eln.scanner_slots" else "gui.eln.scanner_simple"),"%.1f".format(java.util.Locale.ROOT,part.powerFactor*100)),true)else if(part.definition.behavior==Behavior.SIGNAL_BUS) {
                    val message=Component.empty();val values=part.gridState.getLongArray("busVoltages")
                    for(i in 0..15)message.append(Component.literal((if(i==0)"" else "  ")+"%.2f V".format(java.util.Locale.ROOT,if(i<values.size)Double.fromBits(values[i])else 0.0)).withStyle { it.withColor(PaintedWiring.tint(i,.7,.3)) })
                    player.displayClientMessage(message,true)
                }else player.displayClientMessage(Component.translatable("message.eln.meter",
                    "%.3f".format(java.util.Locale.ROOT, part.voltage-part.voltageB), "%.4f".format(java.util.Locale.ROOT,part.current),
                    "%.2f".format(java.util.Locale.ROOT,kotlin.math.abs((part.voltage-part.voltageB)*part.current))), true)
                Content.thermometer.get() -> player.displayClientMessage(if(part.definition.behavior==Behavior.FESTIVE)Component.translatable("message.eln.festive_temperature") else if(part.definition.behavior==Behavior.HEAT_TURBINE)
                    Component.translatable("message.eln.turbine_temperature","%.1f".format(java.util.Locale.ROOT,part.temperature),"%.1f".format(java.util.Locale.ROOT,part.thermalState.getDouble("coldTemperature")))
                    else Component.translatable("message.eln.temperature","%.1f".format(java.util.Locale.ROOT,part.temperature)), true)
                else -> {
                    if(SimpleSignals.supported(part) && part.definition.id !in setOf("signal_source","signal_trimmer")) {
                        if(!player.isShiftKeyDown && SimpleSignals.activate(entity,part))return InteractionResult.SUCCESS
                        return InteractionResult.PASS
                    }
                    if(part.definition.behavior in setOf(Behavior.FESTIVE,Behavior.COMPUTER_PROBE))return InteractionResult.PASS
                    if(part.definition.behavior==Behavior.INVENTORY_SCANNER && !player.isShiftKeyDown) {
                        InventoryScannerRuntime.toggle(entity,part)
                    } else if(part.definition.behavior==Behavior.FUEL_GENERATOR && !player.isShiftKeyDown) {
                        FuelGeneratorRuntime.setEnabled(entity,part,!part.enabled)
                    } else if(part.definition.behavior==Behavior.CRANK && !player.isShiftKeyDown) {
                        part.crankInput+=part.definition.power/20;entity.setChanged()
                    } else if(part.definition.wireless?.kind=="SOURCE" && !player.isShiftKeyDown) {
                        WirelessRuntime.activate(entity,part)
                    } else if (part.definition.behavior == Behavior.BUTTON && !player.isShiftKeyDown) {
                        part.timer = 10; part.enabled = true; entity.setChanged();entity.sync()
                    } else if ((part.definition.behavior in setOf(Behavior.SWITCH,Behavior.BREAKER) || part.definition.id=="signal_switch") && !part.originalBreaker && !player.isShiftKeyDown) {
                        part.enabled = !part.enabled; part.tripped = false; entity.changedTopology()
                    } else {
                        val face = part.face.get3DDataValue()
                        entity.sync()
                        NetworkHooks.openScreen(player as ServerPlayer,
                            SimpleMenuProvider({ id, inventory, _ -> DeviceMenu(id, inventory, entity, face) },
                                Component.translatable(part.definition.translationKey))) { buf -> buf.writeBlockPos(pos); buf.writeByte(face) }
                    }
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }
    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, moving: Boolean) {
        if (state.block != newState.block) {
            if(level is net.minecraft.server.level.ServerLevel)mods.eln.modern.thermal.RoomThermal.data(level).invalidate(pos)
            val entity = level.getBlockEntity(pos) as? FaceNodeEntity
            if (entity != null && !level.isClientSide) {
                NodeCovers.drop(entity)
                mods.eln.modern.grid.GridNetwork.removeAt(level as net.minecraft.server.level.ServerLevel,pos,!entity.gridFailed)
                entity.parts.filterNotNull().forEach { mods.eln.modern.grid.GridMultiblock.remove(entity,it) }
                entity.parts.filterNotNull().forEach { popResource(level, pos, entity.drop(it)) }
                for (slot in 0 until entity.inventory.slots) popResource(level, pos, entity.inventory.getStackInSlot(slot))
            }
        }
        super.onRemove(state, level, pos, newState, moving)
    }
    override fun neighborChanged(state: BlockState, level: Level, pos: BlockPos, block: Block, from: BlockPos, moving: Boolean) {
        if (level.isClientSide) return
        if(machine && (level.getBlockEntity(pos) as? FaceNodeEntity)?.parts?.filterNotNull()?.none { it.definition.powerAntenna!=null || it.definition.behavior in setOf(Behavior.FESTIVE,Behavior.INCUBATOR,Behavior.FABRICATOR,Behavior.TURRET) }!=false)return
        val entity = level.getBlockEntity(pos) as? FaceNodeEntity ?: return
        var changed = false
        entity.parts.filterNotNull().forEach { part ->
            if (level.hasChunkAt(pos.relative(part.face)) && (part.definition.behavior !in setOf(Behavior.FESTIVE,Behavior.INCUBATOR,Behavior.FABRICATOR,Behavior.TURRET,Behavior.COMPUTER_PROBE) && !PartPlacement.supported(level,pos,part.face) || !PartPlacement.canMount(level,pos,part))) {
                if(part.definition.multiblock)mods.eln.modern.grid.GridMultiblock.remove(entity,part)
                popResource(level,pos,entity.drop(part)); entity.parts[part.face.get3DDataValue()] = null; changed = true
            }
        }
        if (changed) {
            if (entity.parts.all { it == null }) level.removeBlock(pos,false) else entity.changedTopology()
        }
    }
    override fun isSignalSource(state: BlockState) = true
    override fun getSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int =
        (level.getBlockEntity(pos) as? FaceNodeEntity)?.parts?.filterNotNull()?.filter { it.definition.behavior == Behavior.REDSTONE_OUTPUT }?.maxOfOrNull { it.signal } ?: 0
    override fun getLightEmission(state: BlockState, level: BlockGetter, pos: BlockPos): Int =
        state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LEVEL)
    override fun onDestroyedByPlayer(state: BlockState,level: Level,pos: BlockPos,player: Player,willHarvest: Boolean,fluid: net.minecraft.world.level.material.FluidState): Boolean {
        if(state.getValue(COVERED)) {
            if(!level.isClientSide)(level.getBlockEntity(pos) as? FaceNodeEntity)?.let { NodeCovers.peel(it,player) }
            return false
        }
        return super.onDestroyedByPlayer(state,level,pos,player,willHarvest,fluid)
    }
    companion object {
        val COVERED=net.minecraft.world.level.block.state.properties.BooleanProperty.create("covered")
        val COVER_OPACITY=net.minecraft.world.level.block.state.properties.IntegerProperty.create("cover_opacity",0,15)
    }
}
