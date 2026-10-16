package mods.eln.modern.client

import mods.eln.modern.*
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RenderGuiOverlayEvent
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid="eln",value=[Dist.CLIENT])
object TutorialSignOverlay {
    fun nearest(mc: Minecraft): Pair<FaceNodeEntity,PartState>? {
        val player=mc.player ?: return null;val level=mc.level ?: return null;val at=player.blockPosition()
        return BlockPos.betweenClosed(at.offset(-1,-1,-1),at.offset(1,1,1)).mapNotNull { pos ->
            if(!level.hasChunkAt(pos))return@mapNotNull null
            val node=level.getBlockEntity(pos) as? FaceNodeEntity ?: return@mapNotNull null
            node.parts.filterNotNull().firstOrNull { it.definition.behavior==Behavior.TUTORIAL_SIGN }?.let { node to it }
        }.minByOrNull { (node,_) -> player.distanceToSqr(node.blockPos.x+.5,node.blockPos.y+.5,node.blockPos.z+.5) }
    }
    @JvmStatic @SubscribeEvent fun render(event: RenderGuiOverlayEvent.Post) {
        if(event.overlay.id()!=VanillaGuiOverlay.HOTBAR.id())return
        val mc=Minecraft.getInstance();val (_,p)=nearest(mc) ?: return
        val text=if(p.gridState.getBoolean("tutorialFound"))p.gridState.getString("tutorialText")else Component.translatable("gui.eln.tutorial_missing").string
        val pose=event.poseStack;pose.pushPose();pose.scale(.5f,.5f,1f)
        for((i,line) in text.replace("\r\n","\n").split('\n').withIndex())mc.font.drawShadow(pose,line,10f,(10+i*10).toFloat(),0xFFFFFF)
        pose.popPose()
    }
}
