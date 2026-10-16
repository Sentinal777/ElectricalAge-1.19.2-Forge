package mods.eln.metrics

/** The native runtime keeps a thread-safe snapshot instead of depending on the old server singleton. */
object MetricsSubsystem {
    data class MnaSnapshot(val subsystems: Int, val inversions: Int, val singular: Int, val averageNanos: Double, val maximumNanos: Long)
    @Volatile var latest = MnaSnapshot(0, 0, 0, 0.0, 0)
        private set
    @JvmStatic fun isSimulatorMetricsActive() = true
    @JvmStatic fun publishMnaMetrics(subsystems: Int, inversions: Int, singular: Int, average: Double, maximum: Long) {
        latest = MnaSnapshot(subsystems, inversions, singular, average, maximum)
    }
}
