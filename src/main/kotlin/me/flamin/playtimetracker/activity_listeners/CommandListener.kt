package me.flamin.playtimetracker.activity_listeners

import com.google.common.collect.ImmutableList
import me.flamin.playtimetracker.PlayTimeTracker
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerCommandPreprocessEvent

class CommandListener constructor(tracker: PlayTimeTracker) : AbstractListener(tracker, CommandListener.events) {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onCommandPreProcess(event: PlayerCommandPreprocessEvent) {
        this.tracker.onPlayerActivity(event.player)
    }

    companion object {
        @JvmStatic
        @Suppress("unused")
        val signature: List<String> = ImmutableList.of<String>()

        private val events: List<Class<out Event>> = arrayListOf(PlayerCommandPreprocessEvent::class.java)
    }
}
