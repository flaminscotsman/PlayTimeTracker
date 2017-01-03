package me.flamin.playtimetracker.activity_listeners

import com.google.common.collect.ImmutableList
import me.flamin.playtimetracker.PlayTimeTracker
import me.flamin.playtimetracker.activity_listeners.AbstractListener
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerCommandPreprocessEvent

internal class CommandListener constructor(tracker: PlayTimeTracker) : AbstractListener(tracker, arrayListOf(PlayerCommandPreprocessEvent::class.java)) {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onCommandPreProcess(event: PlayerCommandPreprocessEvent) {
        this.tracker.onPlayerActivity(event.player)
    }

    companion object {
        @Suppress("unused")
        val signature: List<String> = ImmutableList.of<String>()
    }
}
