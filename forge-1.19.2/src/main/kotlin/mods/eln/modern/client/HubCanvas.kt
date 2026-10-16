package mods.eln.modern.client

import com.mojang.blaze3d.vertex.PoseStack
import mods.eln.modern.HubSettings
import mods.eln.modern.PartState
import net.minecraft.client.gui.GuiComponent
import net.minecraft.network.chat.Component

/** Original HubGui's four corner links and two separate crossing links, fitted around native inventory slots. */
object HubCanvas {
    private data class Rect(val x0: Int,val y0: Int,val x1: Int,val y1: Int) {
        fun contains(x: Double,y: Double)=x>=x0 && x<x1 && y>=y0 && y<y1
        fun draw(pose: PoseStack,color: Int)=GuiComponent.fill(pose,x0,y0,x1,y1,color)
    }
    private val links=listOf(
        listOf(Rect(95,81,100,103),Rect(95,81,117,86)),
        listOf(Rect(58,61,80,66),Rect(75,44,80,66)),
        listOf(Rect(58,81,80,86),Rect(75,81,80,103)),
        listOf(Rect(95,44,100,66),Rect(95,61,117,66)),
        listOf(Rect(58,71,117,76)),listOf(Rect(85,44,90,103)))
    private val leads=listOf(Rect(117,71,152,76),Rect(24,71,58,76),Rect(85,37,90,44),Rect(85,103,90,112))
    fun indexAt(x: Double,y: Double)=links.indexOfFirst { list -> list.any { it.contains(x,y) } }.takeIf { it>=0 }
    fun label(p: PartState,index: Int): Component {
        val (a,b)=HubSettings.pairs[index]
        return Component.translatable(if(HubSettings.connected(p,index))"gui.eln.hub_link_on" else "gui.eln.hub_link_off",a+1,b+1)
    }
    fun draw(pose: PoseStack,p: PartState) {
        leads.forEach { it.draw(pose,0xFF404040.toInt()) }
        for(enabled in listOf(false,true))for(i in links.indices)if(HubSettings.connected(p,i)==enabled)
            links[i].forEach { it.draw(pose,if(enabled)0xFF202020.toInt() else 0xFF909090.toInt()) }
        // The two centre lines cross without becoming a junction, just as in the source hub.
        GuiComponent.fill(pose,85,69,90,78,0xFFC6C6C6.toInt())
        GuiComponent.fill(pose,85,71,90,76,if(HubSettings.connected(p,4))0xFF202020.toInt() else 0xFF909090.toInt())
    }
}
