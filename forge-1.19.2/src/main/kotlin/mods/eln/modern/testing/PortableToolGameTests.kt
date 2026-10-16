package mods.eln.modern.testing

import com.mojang.authlib.GameProfile
import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.AABB
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.event.level.BlockEvent
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import java.util.UUID
import java.util.function.Consumer

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object PortableToolGameTests {
    private fun player(h: GameTestHelper,id: String,joules: Double): FakePlayer {
        GameTestYards.clear(h,200,1,20)
        val base=h.absolutePos(BlockPos.ZERO)
        h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(base.x.toDouble(),199.0,base.z.toDouble(),base.x+17.0,221.0,base.z+17.0)).forEach { it.discard() }
        return FakePlayer(h.level,GameProfile(UUID.randomUUID(),"ElnToolQA")).also {
            it.setGameMode(GameType.SURVIVAL);it.setPos(base.x+5.5,200.0,base.z+3.5)
            it.setItemInHand(InteractionHand.MAIN_HAND,PortableEnergyGameTests.item(id,joules))
        }
    }
    private fun center(h: GameTestHelper)=h.absolutePos(BlockPos.ZERO).let { BlockPos(it.x+5,201,it.z+5) }
    private fun quarry(h: GameTestHelper,center: BlockPos): List<BlockPos> = buildList {
        for(x in -1..1)for(y in -1..0)for(z in -1..1)add(center.offset(x,y,z))
    }.onEach { h.level.setBlockAndUpdate(it,Blocks.COBBLESTONE.defaultBlockState()) }
    private fun dropped(h: GameTestHelper,pos: BlockPos,item: net.minecraft.world.item.Item)=h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(pos).inflate(6.0).expandTowards(0.0,24.0,0.0)).filter { it.item.`is`(item) }.sumOf { it.item.count }
    @JvmStatic @GameTest(batch="portable_tools",template="grid_empty",timeoutTicks=40)
    fun originalDrillActuallyHarvestsEighteenBlocksAndSpendsTheLastFiniteBlockCharge(h: GameTestHelper) {
        val p=player(h,"portable_electrical_mining_drill",3600.0);val stack=p.mainHandItem;val center=center(h);val blocks=quarry(h,center)
        if(ElectricalItems.spec(stack)!!.capacity!=120000.0 || ElectricalItems.spec(stack)!!.chargePower!=10000.0 || stack.getDestroySpeed(Blocks.STONE.defaultBlockState())!=22f)h.fail("Original portable drill ratings or powered mining strength were lost")
        if(!p.gameMode.destroyBlock(center) || blocks.any { !h.level.getBlockState(it).isAir } || ElectricalItems.energy(stack)!=0.0 || dropped(h,center,Items.COBBLESTONE)!=18)h.fail("Actual drill did not harvest all 18 blocks once for exactly 3600 joules: energy=${ElectricalItems.energy(stack)}, drops=${dropped(h,center,Items.COBBLESTONE)}")
        if(stack.getDestroySpeed(Blocks.STONE.defaultBlockState())!=1f)h.fail("Empty drill kept powered mining speed")
        h.succeed()
    }
    @JvmStatic @GameTest(batch="portable_tools",template="grid_empty",timeoutTicks=40)
    fun actualDrillToggleAndItemNbtPreserveSingleBlockModeAndSwingExcludesCharging(h: GameTestHelper) {
        val p=player(h,"portable_electrical_mining_drill",600.0);var stack=p.mainHandItem;val center=center(h);val blocks=quarry(h,center)
        stack.item.use(h.level,p,InteractionHand.MAIN_HAND);stack=ItemStack.of(stack.save(CompoundTag()));p.setItemInHand(InteractionHand.MAIN_HAND,stack)
        if(!stack.orCreateTag.getBoolean("conservative") || !p.gameMode.destroyBlock(center) || blocks.count { h.level.getBlockState(it).isAir }!=1 || ElectricalItems.energy(stack)!=400.0)h.fail("Actual saved conservative drill mode excavated extra blocks or spent the wrong energy")
        stack.item.onEntitySwing(stack,p)
        if(!PortableTools.excluded(stack,h.level.server.tickCount) || PortableTools.excluded(stack,h.level.server.tickCount+40))h.fail("Original two-second descriptor charging exclusion was lost")
        h.succeed()
    }
    @JvmStatic @GameTest(batch="portable_tools",template="grid_empty",timeoutTicks=40)
    fun areaDrillHonorsForgeBreakCancellationAndUnbreakableBlocksWithoutSpendingTheirEnergy(h: GameTestHelper) {
        val p=player(h,"portable_electrical_mining_drill",4000.0);val center=center(h);val blocks=quarry(h,center)
        val protected=center.east();val bedrock=center.offset(-1,-1,-1);h.level.setBlockAndUpdate(bedrock,Blocks.BEDROCK.defaultBlockState())
        val listener=Consumer<BlockEvent.BreakEvent> { if(it.player===p && it.pos==protected)it.isCanceled=true }
        MinecraftForge.EVENT_BUS.addListener(listener)
        try { p.gameMode.destroyBlock(center) }finally { MinecraftForge.EVENT_BUS.unregister(listener) }
        if(h.level.getBlockState(protected).block!=Blocks.COBBLESTONE || h.level.getBlockState(bedrock).block!=Blocks.BEDROCK || blocks.count { h.level.getBlockState(it).isAir }!=16 || ElectricalItems.energy(p.mainHandItem)!=800.0 || dropped(h,center,Items.COBBLESTONE)!=16)h.fail("Area drill bypassed Forge protection/unbreakable blocks or charged for rejected breaks")
        h.succeed()
    }
    @JvmStatic @GameTest(batch="portable_tools",template="grid_empty",timeoutTicks=60)
    fun originalAxeFellsGraduallyTraversesOnlyShortLeafBridgesAndConservesEveryAcceptedLog(h: GameTestHelper) {
        val p=player(h,"portable_electrical_axe",8000.0);val stack=p.mainHandItem;val root=center(h).below()
        val logs=(0..17).map { root.above(it) }+root.offset(2,5,0)
        for(pos in logs)h.level.setBlockAndUpdate(pos,Blocks.OAK_LOG.defaultBlockState())
        val leaves=(1..4).map { root.offset(it,4,0) }
        for(pos in leaves)h.level.setBlockAndUpdate(pos,Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT,true))
        val far=root.offset(5,4,0);h.level.setBlockAndUpdate(far,Blocks.OAK_LOG.defaultBlockState())
        if(ElectricalItems.spec(stack)!!.capacity!=40000.0 || stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState())!=22f || !PortableTools.capitation(stack))h.fail("Original axe ratings, speed or default tree mode changed")
        p.gameMode.destroyBlock(root)
        if(logs.count { h.level.getBlockState(it).isAir }!=1 || ElectricalItems.energy(stack)!=7800.0)h.fail("Tree felling ran all at once or failed to charge the primary log")
        PortableTools.tick(h.level.server)
        if(logs.count { h.level.getBlockState(it).isAir } !in 2..11)h.fail("Original ten-candidate tree budget was exceeded")
        h.runAtTickTime(20) {
            if(logs.any { !h.level.getBlockState(it).isAir } || leaves.any { h.level.getBlockState(it).block!=Blocks.OAK_LEAVES } || h.level.getBlockState(far).block!=Blocks.OAK_LOG || ElectricalItems.energy(stack)!=4200.0 || dropped(h,root,Items.OAK_LOG)!=19)h.fail("Tree traversal lost original leaf limit or finite log harvesting: energy=${ElectricalItems.energy(stack)}, drops=${dropped(h,root,Items.OAK_LOG)}")
            stack.item.use(h.level,p,InteractionHand.MAIN_HAND)
            val restored=ItemStack.of(stack.save(CompoundTag()));p.setItemInHand(InteractionHand.MAIN_HAND,restored)
            if(PortableTools.capitation(restored))h.fail("Axe ordinary-chopping toggle did not survive item NBT")
            h.succeed()
        }
    }
}
