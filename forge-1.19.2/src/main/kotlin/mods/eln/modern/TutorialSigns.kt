package mods.eln.modern

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Files
import java.util.WeakHashMap

/** Original world-authored #beacon#text# pairs; a beacon name is never treated as a file path. */
object TutorialSigns {
    private val texts=WeakHashMap<MinecraftServer,Map<String,String>>()
    fun parse(text: String): Map<String,String> {
        val chunks=text.replace("\r\n","\n").replace("#\n","#").replace("\n#","#").split('#').drop(1)
        return chunks.chunked(2).filter { it.size==2 }.associate { it[0] to it[1].take(16000) }
    }
    private fun source(level: ServerLevel)=texts.getOrPut(level.server) {
        val path=level.server.getWorldPath(LevelResource.ROOT).resolve("EA/tutorialSign.txt")
        runCatching { if(Files.isRegularFile(path) && Files.size(path)<=1048576)parse(Files.readString(path))else emptyMap() }.getOrDefault(emptyMap())
    }
    fun refresh(node: FaceNodeEntity,p: PartState) {
        val level=node.level as? ServerLevel ?: return
        val key=p.gridState.getString("tutorialBeacon")
        val text=source(level)[key]
        p.gridState.putString("tutorialText",text.orEmpty());p.gridState.putBoolean("tutorialFound",text!=null)
    }
    fun configure(node: FaceNodeEntity,p: PartState,key: String): Boolean {
        if(p.definition.behavior!=Behavior.TUTORIAL_SIGN || node.isRemoved || node.parts[p.face.get3DDataValue()]!==p || key.length>64 || key.any { it.code<32 })return false
        p.gridState.putString("tutorialBeacon",key);refresh(node,p);node.setChanged();node.sync();return true
    }
    fun clear()=texts.clear()
}
