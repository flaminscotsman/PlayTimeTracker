package me.flamin.playtimetracker.listeners

import me.flamin.playtimetracker.PlayTimeTracker
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent

class MovementListener constructor(private val plugin: PlayTimeTracker): Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun playerMoveListener(event: PlayerMoveEvent) {
        if (event.from.world != event.to.world) {
            // We need to actually wait until the player has moved :(
            val uuid = event.player.uniqueId
            this.plugin.server.scheduler.runTask(this.plugin, {
                val player = this.plugin.server.getPlayer(uuid)
                if (player != null) {
                    this.plugin.logPlayerActivityChange(player, false)
                    this.plugin.onPlayerActivity(player, false)
                }
            })
        }
    }
}
