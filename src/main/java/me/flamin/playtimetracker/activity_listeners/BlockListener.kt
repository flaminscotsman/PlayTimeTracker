package me.flamin.playtimetracker.activity_listeners

import com.google.common.collect.ImmutableList
import me.flamin.playtimetracker.PlayTimeTracker
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockMultiPlaceEvent
import org.bukkit.event.block.BlockPlaceEvent

internal class BlockListener constructor(tracker: PlayTimeTracker) : AbstractListener(tracker, arrayListOf(
        BlockBreakEvent::class.java, BlockMultiPlaceEvent::class.java, BlockPlaceEvent::class.java
)) {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onBlockBreak(event: BlockBreakEvent) {
        this.tracker.onPlayerActivity(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onBlockMultiPlace(event: BlockMultiPlaceEvent) {
        this.tracker.onPlayerActivity(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onBlockPlace(event: BlockPlaceEvent) {
        this.tracker.onPlayerActivity(event.player)
    }

    companion object {
        @Suppress("unused")
        val signature: List<String> = ImmutableList.of<String>()
    }
}
