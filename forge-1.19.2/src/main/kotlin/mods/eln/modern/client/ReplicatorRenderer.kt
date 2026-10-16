package mods.eln.modern.client

import mods.eln.modern.ReplicatorEntity
import net.minecraft.client.model.SilverfishModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.MobRenderer
import net.minecraft.resources.ResourceLocation

/** ClientProxy actually used ModelSilverfish, rather than the unused ReplicatorModel spider. */
class ReplicatorRenderer(context: EntityRendererProvider.Context): MobRenderer<ReplicatorEntity,SilverfishModel<ReplicatorEntity>>(context,SilverfishModel(context.bakeLayer(ModelLayers.SILVERFISH)),.3f) {
    override fun getTextureLocation(entity: ReplicatorEntity)=ResourceLocation("eln","textures/entity/replicator.png")
    override fun getFlipDegrees(entity: ReplicatorEntity)=180f
}
