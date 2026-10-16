package mods.eln.modern

import mods.eln.misc.WindProcess
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.saveddata.SavedData

class WindData : SavedData() {
    val process=WindProcess()
    var lastTick=Long.MIN_VALUE
    override fun save(tag: CompoundTag): CompoundTag { process.writeToNBT(tag,"");return tag }
    companion object {
        fun load(tag: CompoundTag)=WindData().also { it.process.readFromNBT(tag,"") }
    }
}

/** One saved original atmosphere shared by all dimensions, matching Eln.wind. */
object WorldWind {
    fun data(level: ServerLevel): WindData = level.server.overworld().dataStorage.computeIfAbsent(WindData::load,::WindData,"eln_wind")
    fun tick(level: ServerLevel) {
        val data=data(level)
        if(data.lastTick==level.gameTime)return
        data.lastTick=level.gameTime;data.process.process(.05);data.setDirty()
    }
    fun speed(level: ServerLevel,y: Int): Double {
        val rain=level.getRainLevel(0f).toDouble();val thunder=level.getThunderLevel(0f).toDouble()
        return (data(level).process.getWind(y)*(1+rain*.2+thunder*.2)+rain+thunder*2).coerceAtLeast(0.0)
    }
}
