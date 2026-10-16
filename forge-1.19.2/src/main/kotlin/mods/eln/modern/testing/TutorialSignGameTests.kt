package mods.eln.modern.testing

import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.storage.LevelResource
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import java.nio.file.Files

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object TutorialSignGameTests {
    @JvmStatic @GameTest(template="empty",batch="original_tutorial_map",timeoutTicks=80)
    fun originalHiddenBeaconReadsOnlyItsWorldMapTextHandlesWindowsAndUnixLinesAndPreservesItsNameAcrossReload(h: GameTestHelper) {
        val at=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+8,285,it.z+8) };GameTestYards.clear(h,285,2,4)
        val path=h.level.server.getWorldPath(LevelResource.ROOT).resolve("EA/tutorialSign.txt")
        val previous=if(Files.exists(path))Files.readAllBytes(path)else null
        var done=false
        fun cleanup() { if(done)return;done=true;if(previous!=null)Files.write(path,previous)else Files.deleteIfExists(path);TutorialSigns.clear() }
        fun at(tick: Long,action: ()->Unit)=h.runAtTickTime(tick) { try { action() }catch(e: Throwable) { cleanup();throw e } }
        try {
            Files.createDirectories(path.parent);Files.writeString(path,"#power#\r\nConnect a source.\r\nUse a fuse.\r\n#radio#\nUse the same channel.\n#")
            TutorialSigns.clear();h.level.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());h.level.setBlockAndUpdate(at,Content.node.get().defaultBlockState())
            val node=h.level.getBlockEntity(at) as FaceNodeEntity;var p=PartState(PartDefinition.byId.getValue("tutorial_sign"),Direction.DOWN);node.parts[0]=p;node.changedTopology()
            at(15) {
                if(!TutorialSigns.configure(node,p,"power") || !p.gridState.getBoolean("tutorialFound") || p.gridState.getString("tutorialText")!="Connect a source.\nUse a fuse.")h.fail("Original Windows map beacon did not resolve its actual authored text: ${p.gridState}")
                val saved=node.saveWithoutMetadata();node.load(saved);p=node.parts[0]!!;node.changedTopology()
            }
            at(35) {
                if(p.gridState.getString("tutorialBeacon")!="power" || p.gridState.getString("tutorialText")!="Connect a source.\nUse a fuse." || p.connectedMask!=0)h.fail("Beacon reload lost text/key or created electrical terminals")
                TutorialSigns.configure(node,p,"radio")
                if(p.gridState.getString("tutorialText")!="Use the same channel.")h.fail("LF map text was parsed differently from Windows text")
                if(TutorialSigns.configure(node,p,"bad\nname"))h.fail("Invalid control characters accepted as a beacon key")
                TutorialSigns.configure(node,p,"../../unrelated-file")
                if(p.gridState.getBoolean("tutorialFound") || p.gridState.getString("tutorialText").isNotEmpty())h.fail("A beacon key was treated as a file path")
                cleanup();h.succeed()
            }
        }catch(e: Throwable) { cleanup();throw e }
    }
}
