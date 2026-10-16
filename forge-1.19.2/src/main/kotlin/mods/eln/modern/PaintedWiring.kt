package mods.eln.modern

import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack

/** Original dye metadata order and NodeBase's two-sided color-care mask. */
object PaintedWiring {
    fun wire(p: PartState)=p.definition.behavior in setOf(Behavior.CABLE,Behavior.SIGNAL_BUS,Behavior.POWER_SOCKET)
    fun color(p: PartState)=p.gridState.getInt("paintColor").coerceIn(0,15)
    fun cares(p: PartState)=wire(p) && if(p.gridState.contains("colorCare"))p.gridState.getBoolean("colorCare") else p.definition.behavior!=Behavior.SIGNAL_BUS
    fun compatible(a: PartState,b: PartState)=!cares(a) || !cares(b) || color(a)==color(b)
    private val palette=arrayOf(
        doubleArrayOf(.2,.2,.2),doubleArrayOf(1.0,.05,.05),doubleArrayOf(.2,.5,.1),doubleArrayOf(.3,.2,.1),
        doubleArrayOf(.2,.2,1.0),doubleArrayOf(.7,.05,1.0),doubleArrayOf(.2,.7,.9),doubleArrayOf(.7,.7,.7),
        doubleArrayOf(.4,.4,.4),doubleArrayOf(1.0,.5,.5),doubleArrayOf(.05,1.0,.05),doubleArrayOf(.9,.8,.1),
        doubleArrayOf(.4,.5,1.0),doubleArrayOf(1.0,.2,1.0),doubleArrayOf(1.0,.6,.3),doubleArrayOf(1.0,1.0,1.0))
    fun tint(color: Int,gain: Double=1.0,bias: Double=0.0): Int {
        val rgb=palette[color.coerceIn(0,15)].map { ((it*gain+bias)*255).toInt().coerceIn(0,255) }
        return (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
    }
    fun tint(p: PartState)=if(wire(p))tint(color(p))else 0x333333
    fun connectionTint(p: PartState,edge: Int)=if(wire(p))tint(p)else tint(p.gridState.getInt("connectionColor$edge"))
    fun toggle(node: FaceNodeEntity,p: PartState) {
        p.gridState.putBoolean("colorCare",!cares(p));node.changedTopology()
    }
}

object Brushes {
    fun life(stack: ItemStack)=if(stack.tag?.contains("life")==true)stack.tag!!.getInt("life").coerceIn(0,32)else 32
    fun color(stack: ItemStack)=(stack.item as? MaterialItem)?.definition?.takeIf { it.kind=="BRUSH" }?.variant
    /** Returns true for a handled brush, including a dry brush or an unchanged color. */
    fun paint(node: FaceNodeEntity,p: PartState,player: Player,stack: ItemStack): Boolean {
        val color=color(stack) ?: return false
        val wire=PaintedWiring.wire(p)
        if(!wire && p.definition.lamp?.paintable!=true && p.definition.id!="digital_display" && p.definition.behavior!=Behavior.LOGGER)return false
        val display=DyeColor.byId(15-color).fireworkColor
        if(if(wire)PaintedWiring.color(p)==color else p.displayColor==display)return true
        if(!player.abilities.instabuild) {
            val life=life(stack)
            if(life==0) { player.displayClientMessage(Component.translatable("message.eln.brush_dry"),true);return true }
            stack.orCreateTag.putInt("life",life-1)
        }
        if(wire) { p.gridState.putInt("paintColor",color);node.changedTopology() }
        else { p.displayColor=display;node.setChanged();node.sync() }
        return true
    }
}
