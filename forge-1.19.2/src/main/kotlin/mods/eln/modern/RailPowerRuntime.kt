package mods.eln.modern

import mods.eln.sim.ElectricalConnection
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.mna.misc.MnaConst
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraftforge.event.server.ServerStoppedEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.util.UUID
import java.util.WeakHashMap
import kotlin.math.abs

/** One live electrical contact per cart; uncollected paid work survives contact changes exactly once. */
@Mod.EventBusSubscriber(modid="eln")
object RailPower {
    class Contact(val cart: ElectricMinecartEntity,var owner: FaceNodeEntity,var face: Int,var seen: Long) {
        var paid=0.0
        var voltage=0.0
    }
    private val worlds=WeakHashMap<ServerLevel,MutableMap<UUID,Contact>>()
    fun pending(cart: ElectricMinecartEntity)=worlds[cart.level]?.get(cart.uuid)?.takeIf { it.cart===cart }?.paid ?: 0.0
    fun contacts(node: FaceNodeEntity,p: PartState)=worlds[node.level]?.values?.filter { it.owner===node && it.face==p.face.get3DDataValue() && !it.cart.isRemoved }.orEmpty()
    fun collect(cart: ElectricMinecartEntity): Double {
        val contact=worlds[cart.level]?.get(cart.uuid) ?: return 0.0
        if(contact.cart!==cart)return 0.0
        val paid=contact.paid;contact.paid=0.0;return paid
    }
    fun disconnect(cart: ElectricMinecartEntity) {
        val contacts=worlds[cart.level] ?: return
        val contact=contacts[cart.uuid]?.takeIf { it.cart===cart } ?: return
        contacts.remove(cart.uuid)
        cart.energyJ+=contact.paid;contact.paid=0.0;CircuitManager.invalidate(contact.owner)
    }
    fun update(cart: ElectricMinecartEntity) {
        val level=cart.level as? ServerLevel ?: return
        val contacts=worlds.getOrPut(level) { linkedMapOf() }
        val start=cart.blockPosition()
        var owner: FaceNodeEntity?=null;var face=0
        for(y in 0..3) {
            val at=start.above(y)
            if(!level.hasChunkAt(at))continue
            val node=level.getBlockEntity(at) as? FaceNodeEntity ?: continue
            val p=node.parts.filterNotNull().firstOrNull { it.definition.behavior==Behavior.OVERHEAD_LINES } ?: continue
            owner=node;face=p.face.get3DDataValue();break
        }
        val old=contacts[cart.uuid]
        if(owner==null) { if(old!=null)disconnect(cart);return }
        if(old==null || old.cart!==cart || old.owner!==owner || old.face!=face) {
            if(old!=null)disconnect(old.cart)
            contacts[cart.uuid]=Contact(cart,owner,face,level.gameTime);CircuitManager.invalidate(owner)
        }else old.seen=level.gameTime
    }
    @JvmStatic @SubscribeEvent fun stopped(event: ServerStoppedEvent) { worlds.clear() }
    private fun report(player: net.minecraft.world.entity.player.Player,stack: net.minecraft.world.item.ItemStack) {
        val cart=player.vehicle as? ElectricMinecartEntity ?: return
        val all=(stack.item as? MaterialItem)?.definition?.id=="allmeter"
        if(!player.level.isClientSide && (stack.item==Content.multimeter.get() || all))player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.eln.cart_energy",String.format(java.util.Locale.ROOT,"%.1f",cart.energyJ)),true)
    }
    @JvmStatic @SubscribeEvent fun meter(event: net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem) { report(event.entity,event.itemStack) }
    @JvmStatic @SubscribeEvent fun meterBlock(event: net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock) { report(event.entity,event.itemStack) }
}

/** Original overhead load and private cart resistors. Only real resistor work charges a cart. */
class RailPowerRuntime(private val e: ElementRuntime) {
    private val p=e.part
    private val load=ElectricalLoad().apply { serialResistance=.0125;state=p.voltage }
    init {
        e.circuit.root.addState(load);e.circuit.root.addComponent(Resistor(load,null).pullDown())
        for(i in 0..3)e.pins[i]=load
    }
    private data class Tap(val contact: RailPower.Contact,val load: ElectricalLoad,val resistor: Resistor,var charging: Boolean=false)
    private val taps=RailPower.contacts(e.node,p).map { contact ->
        val privateLoad=ElectricalLoad().apply { serialResistance=1e-9;state=contact.voltage }
        val resistor=Resistor(privateLoad,null).setResistance(MnaConst.highImpedance)
        e.circuit.root.addState(privateLoad);e.circuit.root.addComponent(resistor)
        e.circuit.root.addComponent(ElectricalConnection(load,privateLoad))
        Tap(contact,privateLoad,resistor)
    }
    fun beforeStep(dt: Double) {
        for(tap in taps) {
            val c=tap.contact;val cart=c.cart
            val left=cart.targetJ-cart.energyJ-c.paid
            val alive=!cart.isRemoved && e.circuit.level.isPositionEntityTicking(cart.blockPosition()) && e.circuit.level.gameTime-c.seen<=2 && c.owner===e.node
            tap.charging=alive && tap.load.voltage>=700.0 && left>0.0
            tap.resistor.resistance=if(!tap.charging)MnaConst.highImpedance else {
                val original=cart.targetJ/(abs(cart.targetJ-cart.energyJ)*2)*200
                maxOf(original,tap.load.voltage*tap.load.voltage*dt/left)
            }
        }
    }
    fun afterStep(dt: Double) {
        var paid=0.0;var dissipated=0.0
        for(tap in taps) {
            val work=tap.resistor.power.coerceAtLeast(0.0)*dt
            val credited=if(tap.charging)minOf(work,(tap.contact.cart.targetJ-tap.contact.cart.energyJ-tap.contact.paid).coerceAtLeast(0.0))else 0.0
            tap.contact.paid+=credited;tap.contact.voltage=tap.load.voltage;paid+=work;dissipated+=work-credited
        }
        p.voltage=load.voltage;p.current=load.current;p.energy+=paid;p.active=paid>1e-8
        p.gridState.putDouble("railDissipatedJ",p.gridState.getDouble("railDissipatedJ")+dissipated)
        p.gridState.putInt("railCarts",taps.count { !it.contact.cart.isRemoved && e.circuit.level.gameTime-it.contact.seen<=2 })
    }
}
