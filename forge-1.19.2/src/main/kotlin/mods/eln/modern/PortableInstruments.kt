package mods.eln.modern

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import java.util.Locale

object PortableInstruments {
    val kinds=setOf("ALLMETER","WIRELESS_ANALYSER","CONFIG_COPY")
    data class Channel(val name: String,val strength: Double,val value: Double,val transmitters: Int)
    fun analyse(level: ServerLevel,at: BlockPos): List<Channel> {
        val reception=CircuitManager.wirelessAt(level,at)
        return reception.transmitters.groupBy { it.channel }.map { (name,tx) -> Channel(name,tx.minOf { reception.strengths[it.gridId] ?: 0.0 },tx.maxOf { WirelessNetwork.normalized(it) },tx.size) }
    }
    fun meter(p: PartState): Component {
        if(p.definition.behavior==Behavior.ENERGY_METER)return EnergyMeterControls.reading(p)
        fun n(value: Double,precision: Int)=String.format(Locale.ROOT,"%.${precision}f",value)
        return Component.translatable("message.eln.meter",n(p.voltage-p.voltageB,3),n(p.current,4),n(kotlin.math.abs((p.voltage-p.voltageB)*p.current),2)).append("   ")
            .append(Component.translatable("message.eln.temperature",n(p.temperature,1)))
    }
    fun interact(node: FaceNodeEntity,p: PartState,player: Player,stack: ItemStack): Boolean {
        val kind=(stack.item as? MaterialItem)?.definition?.kind ?: return false
        if(kind !in kinds)return false
        if(player.level.isClientSide)return true
        if(kind=="ALLMETER")player.displayClientMessage(meter(p),true)
        if(kind=="CONFIG_COPY") {
            val clipboard=stack.orCreateTag.getCompound("ElnConfiguration")
            val copied=player.isShiftKeyDown
            val ok=if(copied)ConfigCopyTool.copy(node,p,clipboard)else ConfigCopyTool.apply(node,p,clipboard,player)
            if(copied && ok)stack.orCreateTag.put("ElnConfiguration",clipboard)
            node.level!!.playSound(null,player.blockPosition(),OriginalSounds.event(if(!ok)"beep_error" else if(copied)"beep_accept" else "beep_accept_2"),SoundSource.PLAYERS,1f,1f)
            player.displayClientMessage(Component.translatable(if(!ok)"message.eln.config_unavailable" else if(copied)"message.eln.config_copied" else "message.eln.config_applied"),true)
        }
        return kind!="WIRELESS_ANALYSER"
    }
    fun use(context: UseOnContext): InteractionResult {
        val player=context.player ?: return InteractionResult.PASS
        if(!context.level.mayInteract(player,context.clickedPos))return InteractionResult.FAIL
        val kind=(context.itemInHand.item as? MaterialItem)?.definition?.kind ?: return InteractionResult.PASS
        if(kind=="WIRELESS_ANALYSER") {
            val world=context.level as? ServerLevel ?: return InteractionResult.SUCCESS
            val channels=analyse(world,context.clickedPos.relative(context.clickedFace))
            player.displayClientMessage(Component.literal("-------------------"),false)
            for(c in channels)player.displayClientMessage(Component.translatable("message.eln.wireless_analysis",c.name,String.format(Locale.ROOT,"%.1f",c.strength),String.format(Locale.ROOT,"%.0f",c.value*100)),false)
            if(channels.isEmpty())player.displayClientMessage(Component.translatable("message.eln.wireless_none"),false)
            return InteractionResult.SUCCESS
        }
        val entity=context.level.getBlockEntity(context.clickedPos)
        val node=(entity as? FaceNodeEntity) ?: (entity as? mods.eln.modern.grid.GridGhostEntity)?.root() ?: return InteractionResult.PASS
        val part=node.partAt(context.clickLocation,player.eyePosition) ?: return InteractionResult.PASS
        return if(interact(node,part,player,context.itemInHand))InteractionResult.sidedSuccess(context.level.isClientSide)else InteractionResult.PASS
    }
}
