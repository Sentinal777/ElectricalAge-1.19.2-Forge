package mods.eln.modern

/** Installed by client setup; the dedicated server never loads Minecraft client classes. */
object ClientNodeHooks {
    var tick: ((FaceNodeEntity)->Unit)?=null
    var creative: ()->Boolean = { false }
    var coverModifier: ()->Boolean = { false }
}
