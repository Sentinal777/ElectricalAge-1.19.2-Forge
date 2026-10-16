package mods.eln.modern

import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.*
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import javax.annotation.Nullable

class PartItem(val definition: PartDefinition, properties: Properties) : Item(properties) {
    override fun initializeClient(consumer: java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions>) {
        // Forge invokes this from Item's constructor, before Kotlin assigns definition.
        // Generated sprite models ignore the custom renderer; 3D models call it after setup.
        consumer.accept(object : net.minecraftforge.client.extensions.common.IClientItemExtensions {
            override fun getCustomRenderer()=mods.eln.modern.client.NodeItemRenderer.instance
        })
    }
    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val player = context.player ?: return InteractionResult.PASS
        val gridNode=(level.getBlockEntity(context.clickedPos) as? FaceNodeEntity) ?: (level.getBlockEntity(context.clickedPos) as? mods.eln.modern.grid.GridGhostEntity)?.root()
        if(gridNode!=null && mods.eln.modern.grid.GridNetwork.useCable(gridNode,player,context.itemInHand,context.clickedFace))return InteractionResult.sidedSuccess(level.isClientSide)
        if(gridNode!=null) {
            val target=gridNode.partAt(context.clickLocation,player.eyePosition)
            if(target!=null && PartInventory.takeCable(gridNode,target,player,context.itemInHand))return InteractionResult.sidedSuccess(level.isClientSide)
        }
        val clicked = level.getBlockEntity(context.clickedPos) as? FaceNodeEntity
        val adjacent=context.clickedPos.relative(context.clickedFace)
        val defaultFace=if(definition.machine && definition.behavior!=Behavior.FLOODLIGHT && definition.powerAntenna==null)Direction.DOWN else context.clickedFace.opposite
        val candidates=mutableListOf<Pair<net.minecraft.core.BlockPos,Direction>>()
        if(!definition.machine && clicked!=null && !clicked.isMachine) {
            candidates.add(context.clickedPos to defaultFace)
            // Extending a face-mounted run from an existing part keeps its mounting plane.
            clicked.partAt(context.clickLocation,player.eyePosition)?.face?.let { mount ->
                if(mount.axis!=context.clickedFace.axis)candidates.add(adjacent to mount)
            }
        }
        candidates.add(adjacent to defaultFace)
        val selected=candidates.firstNotNullOfOrNull { (candidate,face) ->
            val part=PartState(definition,face)
            part.rotation=FaceBasis.placementRotation(face,player.direction,definition.machine)
            if(definition.behavior in setOf(Behavior.POWER_SOCKET,Behavior.FABRICATOR))part.rotation=0
            if(definition.powerAntenna!=null)part.rotation=PowerAntennas.initialRotation(face)
            if(definition.behavior==Behavior.WATCH)part.rotation=(part.rotation+1)%4
            if(definition.behavior==Behavior.TIMER)part.rotation=(part.rotation+3)%4
            if(definition.behavior==Behavior.ALARM)part.rotation=(part.rotation+2)%4
            definition.contact?.let { part.rotation=(part.rotation+if(it.relay)1 else 2)%4 }
            if(definition.passive!=null && !definition.machine)part.rotation=(part.rotation+1)%4
            if(definition.behavior==Behavior.NIXIE)part.rotation=(part.rotation+2)%4
            if(definition.wireless?.kind in setOf("RX","TX","REPEATER"))part.rotation=(part.rotation+2)%4
            if(definition.behavior==Behavior.FUSE_HOLDER || definition.originalBreaker)part.rotation=(part.rotation+2)%4
            if(definition.behavior==Behavior.RESIN_COLLECTOR)part.rotation=FaceBasis.edges(face,0).indexOf(Direction.DOWN).coerceAtLeast(0)
            if(definition.solar?.rotating==true && definition.multiblock)part.rotation=0
            if(definition.behavior==Behavior.WIND_TURBINE)part.rotation=3
            val state=level.getBlockState(candidate)
            val node=level.getBlockEntity(candidate) as? FaceNodeEntity
            val available=state.isAir || state.material.isReplaceable || !definition.machine && state.block==Content.node.get()
            if(available && level.mayInteract(player,candidate) && player.mayUseItemAt(candidate,context.clickedFace,context.itemInHand) &&
                (definition.machine && definition.powerAntenna==null || PartPlacement.supported(level,candidate,face)) && PartPlacement.fits(part,node) && PartPlacement.canMount(level,candidate,part) &&
                mods.eln.modern.grid.GridMultiblock.canPlace(level,candidate,part,player))candidate to part else null
        } ?: return InteractionResult.FAIL
        val (pos,part)=selected
        val face=part.face
        val state=level.getBlockState(pos)
        if (!level.isClientSide) {
            val desired = if(definition.behavior==Behavior.COMPUTER_PROBE)Content.computerProbe.get() else if (definition.machine) Content.machine.get() else Content.node.get()
            if (state.block != desired && !level.setBlock(pos, desired.defaultBlockState(), 3)) return InteractionResult.FAIL
            val entity = level.getBlockEntity(pos) as? FaceNodeEntity ?: return InteractionResult.FAIL
            context.itemInHand.tag?.getCompound("component")?.let { saved ->
                PartState.load(saved)?.takeIf { it.definition.id == definition.id }?.let { restored ->
                    part.charge = restored.charge; part.life = restored.life; part.temperature = restored.temperature
                    part.value = restored.value; part.resistance = restored.resistance
                    part.legacyPassive=restored.legacyPassive
                    part.legacyWireless=restored.legacyWireless
                    part.legacyBreaker=restored.legacyBreaker
                    part.legacySimpleSignal=restored.legacySimpleSignal
                    part.enabled = restored.enabled; part.channel = restored.channel; part.expression = restored.expression
                    part.energy = restored.energy
                    part.voltage=restored.voltage;part.voltageB=restored.voltageB
                    part.inductorCurrent=restored.inductorCurrent;part.progress=restored.progress;part.recipeId=restored.recipeId
                    part.shaftEnergy=restored.shaftEnergy;part.shaftSpeed=restored.shaftSpeed;part.shaftGroup=restored.shaftGroup
                    if(definition.grid==null)part.gridState=restored.gridState.copy()
                    part.gridAngle=restored.gridAngle;part.history=restored.history.toMutableList()
                    part.tripped=restored.tripped
                    part.logicState = restored.logicState.copy(); part.equationState = restored.equationState.copy()
                    part.analogState = restored.analogState.copy()
                    part.displayColor=restored.displayColor
                    part.inventoryData=restored.inventoryData.copy()
                    part.thermalState=restored.thermalState.copy();part.thermalPower=restored.thermalPower
                    part.fuelEnergy=restored.fuelEnergy
                    part.solarAngle=restored.solarAngle
                }
            }
            if(definition.behavior==Behavior.AUTO_MINER)part.gridState.putString("owner",player.uuid.toString())
            entity.parts[face.get3DDataValue()] = part
            if(definition.mechanical) {
                part.shaftJoinPending=true
                part.shaftGroup="placement:${part.gridId}"
                if(definition.behavior==Behavior.CLUTCH)for(side in listOf(1,3))part.gridState.putString("clutchGroup$side","placement:${part.gridId}:$side")
            }
            context.itemInHand.tag?.takeIf { it.getCompound("component").getString("id")==definition.id }?.let {
                entity.restoreItemStorage(part,it.getCompound("machineStorage"))
            }
            mods.eln.modern.grid.GridMultiblock.install(entity,part)
            entity.changedTopology()
            if (!player.abilities.instabuild) context.itemInHand.shrink(1)
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }
    override fun fillItemCategory(tab: CreativeModeTab,items: net.minecraft.core.NonNullList<ItemStack>) {
        if(definition.behavior==Behavior.TUTORIAL_SIGN)return
        if(definition.behavior==Behavior.MODBUS && !mods.eln.config.ForgePortConfig.modbusEnabled.get())return
        if(definition.behavior!=Behavior.FESTIVE || mods.eln.config.ForgePortConfig.enableFestiveItems.get())super.fillItemCategory(tab,items)
    }
    override fun appendHoverText(stack: ItemStack, @Nullable level: Level?, lines: MutableList<Component>, flag: TooltipFlag) {
        if(definition.behavior==Behavior.PROCESSOR) { lines.add(Component.translatable("tooltip.eln.signal_processor"));return }
        if(definition.behavior==Behavior.ENERGY_METER) { lines.add(Component.translatable("tooltip.eln.energy_meter"));return }
        if(definition.behavior==Behavior.TURRET) { lines.add(Component.translatable("tooltip.eln.turret"));return }
        if(definition.behavior==Behavior.TELEPORTER) { lines.add(Component.translatable("tooltip.eln.transporter"));return }
        if(definition.behavior==Behavior.AUTO_MINER) { lines.add(Component.translatable("tooltip.eln.auto_miner"));return }
        if(definition.behavior==Behavior.FABRICATOR) { lines.add(Component.translatable("tooltip.eln.fabricator"));return }
        if(definition.behavior==Behavior.INCUBATOR) { lines.add(Component.translatable("tooltip.eln.incubator"));return }
        if(definition.behavior==Behavior.FESTIVE) { lines.add(Component.translatable("tooltip.eln.festive"));return }
        val component=stack.tag?.getCompound("component")?.takeIf { it.getString("id")==definition.id }
        val previewBreaker=component?.let { it.getBoolean("legacyBreaker") || it.getInt("componentDataVersion")<7 }==true
        val previewPassive=component?.let { it.getBoolean("legacyPassive") || it.getInt("componentDataVersion")<5 }==true
        if(definition.behavior in setOf(Behavior.CABLE,Behavior.SIGNAL_BUS,Behavior.POWER_SOCKET)) { lines.add(Component.translatable(if(definition.behavior==Behavior.POWER_SOCKET)"tooltip.eln.power_socket" else "tooltip.eln.painted_wire"));return }
        if(definition.powerAntenna!=null) { lines.add(Component.translatable("tooltip.eln.rating",definition.nominalVoltage.toInt(),definition.power/definition.nominalVoltage));lines.add(Component.translatable("gui.eln.power",definition.power.toInt()));if(definition.powerAntenna.transmitter)lines.add(Component.translatable("gui.eln.wireless_range",definition.powerAntenna.range));lines.add(Component.translatable(if(definition.powerAntenna.transmitter)"tooltip.eln.power_antenna_tx" else "tooltip.eln.power_antenna_rx"));return }
        if(definition.behavior==Behavior.INVENTORY_SCANNER) { lines.add(Component.translatable("tooltip.eln.inventory_scanner"));return }
        if(definition.behavior==Behavior.FUSE_HOLDER) { lines.add(Component.translatable("tooltip.eln.fuse_holder"));return }
        if(definition.originalBreaker && !previewBreaker) { lines.add(Component.translatable("tooltip.eln.original_breaker"));return }
        if(definition.behavior==Behavior.HUB) { lines.add(Component.translatable("tooltip.eln.hub"));return }
        if(definition.behavior==Behavior.WATCH) { lines.add(Component.translatable("tooltip.eln.clock"));lines.add(Component.translatable("tooltip.eln.clock_battery"));return }
        if(definition.behavior==Behavior.TIMER) { lines.add(Component.translatable("tooltip.eln.timer"));return }
        if(definition.behavior==Behavior.ALARM) { lines.add(Component.translatable("tooltip.eln.alarm"));return }
        if(definition.behavior==Behavior.RESIN_COLLECTOR) { lines.add(Component.translatable("tooltip.eln.resin_collector"));return }
        if(definition.behavior==Behavior.CRANK) { lines.add(Component.translatable("tooltip.eln.crank"));return }
        if(definition.behavior==Behavior.SENSOR) {
            lines.add(Component.translatable("tooltip.eln.probe"));lines.add(Component.translatable("tooltip.eln.face"));return
        }
        definition.grid?.let { grid ->
            lines.add(Component.translatable("tooltip.eln.grid",Component.translatable(PartDefinition.byId.getValue(grid.cable).translationKey),grid.range.toInt()));return
        }
        definition.thermal?.takeIf { definition.behavior in setOf(Behavior.THERMAL_CABLE,Behavior.HEAT_FURNACE,Behavior.FUEL_HEAT_FURNACE,Behavior.ACTIVE_COOLER,Behavior.PASSIVE_COOLER) }?.let {
            lines.add(Component.translatable("tooltip.eln.thermal",it.rs*2,it.capacity))
            lines.add(Component.translatable(if(definition.machine)"tooltip.eln.machine" else "tooltip.eln.face"));return
        }
        definition.passive?.takeIf { !previewPassive }?.let { spec ->
            lines.add(Component.translatable(when(spec.kind) { "RESISTOR" -> "tooltip.eln.passive_resistor";"CAPACITOR" -> "tooltip.eln.passive_capacitor";else -> if(spec.controlled)"tooltip.eln.passive_variable_inductor" else "tooltip.eln.passive_inductor" }))
            if(spec.controlled)lines.add(Component.translatable("tooltip.eln.passive_control"))
            if(spec.large)lines.add(Component.translatable("tooltip.eln.large_rheostat"))
            lines.add(Component.translatable(if(definition.machine)"tooltip.eln.machine" else "tooltip.eln.face"));return
        }
        if(definition.behavior==Behavior.NIXIE) { lines.add(Component.translatable("tooltip.eln.nixie"));return }
        lines.add(Component.translatable("tooltip.eln.rating", definition.nominalVoltage, definition.maxCurrent))
        lines.add(Component.translatable(if (definition.machine) "tooltip.eln.machine" else "tooltip.eln.face"))
    }
}
