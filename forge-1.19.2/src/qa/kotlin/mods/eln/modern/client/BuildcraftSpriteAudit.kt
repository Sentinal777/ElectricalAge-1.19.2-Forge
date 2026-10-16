package mods.eln.modern.client

import mods.eln.Eln
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.world.inventory.InventoryMenu

/** Read-only verification of the optional published BCCE atlas, including cached holder references. */
internal object BuildcraftSpriteAudit {
    fun verify(mc: Minecraft) {
        if(!net.minecraftforge.fml.ModList.get().isLoaded("buildcraftlib"))return
        val registry=Class.forName("buildcraft.lib.client.sprite.SpriteHolderRegistry")
        val field=registry.getDeclaredField("HOLDER_MAP").apply { isAccessible=true }
        val holders=field.get(null) as Map<*,*>
        val atlas=mc.modelManager.getAtlas(InventoryMenu.BLOCK_ATLAS)
        val missing=atlas.getSprite(MissingTextureAtlasSprite.getLocation())
        val failures=mutableListOf<String>()
        for((id,holder) in holders) {
            val cached=holder!!.javaClass.getMethod("getSprite").invoke(holder) as? TextureAtlasSprite
            val actual=atlas.getSprite(id as net.minecraft.resources.ResourceLocation)
            if(cached==null || cached===missing || actual===missing || cached.u0==missing.u0 && cached.v0==missing.v0 || actual.u0==missing.u0 && actual.v0==missing.v0)failures.add(id.toString())
        }
        require(holders.isNotEmpty() && failures.isEmpty()) { "BCCE actual atlas/holder sprite failures: $failures (${holders.size} holders)" }
        Eln.LOGGER.info("ELN_CLIENT_QA BCCE published artifact: all {} actual atlas sprites and cached sprite-holder references resolve to real textures",holders.size)
    }
}
