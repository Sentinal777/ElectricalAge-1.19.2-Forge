package mods.eln.modern.testing

import com.mojang.authlib.GameProfile
import mods.eln.modern.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.TieredItem
import net.minecraft.world.item.Tiers
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import java.util.UUID

@GameTestHolder("eln") @PrefixGameTestTemplate(false)
object CopperToolGameTests {
    @JvmStatic @GameTest(batch="copper_tools",template="grid_empty",timeoutTicks=40)
    fun copperToolsKeepIronTierAndPerformActualHarvestTillingAndPathActions(h: GameTestHelper) {
        GameTestYards.clear(h,180,1,5);val base=h.absolutePos(BlockPos.ZERO);val pos=BlockPos(base.x+5,180,base.z+5)
        val p=FakePlayer(h.level,GameProfile(UUID.randomUUID(),"ElnCopperQA"));p.setGameMode(GameType.SURVIVAL);p.setPos(pos.x+.5,180.0,pos.z-2.5)
        fun tool(name: String)=ItemStack(Content.materialItems.getValue("copper_$name").get()).also { p.setItemInHand(InteractionHand.MAIN_HAND,it) }
        for(name in listOf("sword","pickaxe","axe","shovel","hoe")) {
            val stack=tool(name)
            if((stack.item as? TieredItem)?.tier!=Tiers.IRON || stack.maxDamage!=250 || stack.maxStackSize!=1)h.fail("Original copper tool lost its iron material and durability: $name")
        }
        val pick=tool("pickaxe");h.level.setBlockAndUpdate(pos,Blocks.DIAMOND_ORE.defaultBlockState())
        h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(pos).inflate(4.0)).forEach { it.discard() }
        if(!pick.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState()) || pick.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()) || !p.gameMode.destroyBlock(pos) || pick.damageValue!=1 || h.level.getEntitiesOfClass(ItemEntity::class.java,AABB(pos).inflate(4.0)).filter { it.item.`is`(Items.DIAMOND) }.sumOf { it.item.count }!=1)h.fail("Copper pickaxe lost actual iron-tier harvesting or finite durability")
        fun use(name: String,expected: net.minecraft.world.level.block.Block) {
            val stack=tool(name);h.level.setBlockAndUpdate(pos,Blocks.DIRT.defaultBlockState())
            val result=stack.useOn(UseOnContext(p,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(pos).add(0.0,.5,0.0),Direction.UP,pos,false)))
            if(!result.consumesAction() || h.level.getBlockState(pos).block!=expected || stack.damageValue!=1)h.fail("Copper $name did not perform its actual terrain action with durability")
        }
        use("hoe",Blocks.FARMLAND);use("shovel",Blocks.DIRT_PATH)
        h.succeed()
    }
}
