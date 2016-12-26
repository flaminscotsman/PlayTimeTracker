package me.flamin.playtimetracker.listeners

import me.flamin.playtimetracker.PlayTimeTracker
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent

class MovementListener implements Listener {
    private final PlayTimeTracker plugin;

    MovementListener(PlayTimeTracker plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    def playerMoveListener(PlayerMoveEvent event) {
        if (event.from.world != event.to.world) {
            // We need to actually wait until the player has moved :(
            final uuid = event.player.uniqueId
            this.plugin.server.scheduler.runTask(this.plugin, {
                def player = this.plugin.server.getPlayer(uuid)
                if (player != null) {
                    this.plugin.logPlayerActivityChange(player, false)
                    this.plugin.onPlayerActivity(player, false)
                }
            } as Runnable)
        }
    }
}
