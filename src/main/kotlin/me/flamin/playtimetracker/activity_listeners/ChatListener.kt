package me.flamin.playtimetracker.activity_listeners

import com.google.common.collect.ImmutableList
import me.flamin.playtimetracker.PlayTimeTracker
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.AsyncPlayerChatEvent

class ChatListener constructor(tracker: PlayTimeTracker) : AbstractListener(tracker, ChatListener.events) {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        if (event.isAsynchronous) {
            val uuid = event.player.uniqueId
            this.tracker.server.scheduler.runTask(
                    this.tracker,
                    {
                        val player = tracker.server.getPlayer(uuid)
                        if (player != null) {
                            tracker.onPlayerActivity(player)
                        }
                    }
            )
        } else {
            this.tracker.onPlayerActivity(event.player)
        }
    }

    companion object {
        @JvmStatic
        @Suppress("unused")
        val signature: List<String> = ImmutableList.of<String>()

        private val events = arrayListOf(AsyncPlayerChatEvent::class.java)
    }
}
