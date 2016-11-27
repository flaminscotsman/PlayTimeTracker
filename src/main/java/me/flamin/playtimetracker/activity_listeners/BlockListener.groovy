package me.flamin.playtimetracker.activity_listeners

import com.google.common.collect.ImmutableList
import me.flamin.playtimetracker.PlayTimeTracker
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockMultiPlaceEvent
import org.bukkit.event.block.BlockPlaceEvent

class BlockListener extends AbstractListener {
    public static final List<String> signature = ImmutableList.of();
    protected BlockListener(PlayTimeTracker tracker) {
        super(tracker, [BlockBreakEvent, BlockMultiPlaceEvent, BlockPlaceEvent])
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    void onBlockBreak(BlockBreakEvent event) {
        this.tracker.onPlayerActivity(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    void onBlockMultiPlace(BlockMultiPlaceEvent event) {
        this.tracker.onPlayerActivity(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    void onBlockPlace(BlockPlaceEvent event) {
        this.tracker.onPlayerActivity(event.player)
    }
}
