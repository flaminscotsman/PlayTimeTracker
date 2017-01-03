package me.flamin.playtimetracker.listeners

import me.flamin.playtimetracker.PlayTimeTracker
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class LoginListener constructor(private val plugin: PlayTimeTracker): Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    fun playerJoinListener(event: PlayerJoinEvent) {
        this.plugin.onPlayerActivity(event.player)
    }
}
