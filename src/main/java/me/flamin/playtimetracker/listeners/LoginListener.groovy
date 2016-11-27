package me.flamin.playtimetracker.listeners

import com.mongodb.client.MongoCollection
import me.flamin.playtimetracker.PlayTimeTracker
import groovy.sql.Sql
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

import java.sql.SQLException
import java.util.logging.Level

class LoginListener implements Listener {
    private static final query = """
    INSERT INTO `session_components` (
      `session_id`, `server`, `world`, `is_afk`
    )
    SELECT
      session_id, ?, ?, ?
    FROM `session`
    JOIN `players`
    ON `session`.`player` = `players`.`uuid`
    WHERE `players`.`uuid` = ?
    AND `session`.`active` = 1
    """
    private final PlayTimeTracker plugin;

    public LoginListener(PlayTimeTracker plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    def playerJoinListener(PlayerJoinEvent event) {
        def task = {
            try {
                def conn = this.plugin.getDatabase('Activity');
                MongoCollection collection = db.Activity;

                collection
            } catch (SQLException e) {
                this.plugin.logger.log(
                        Level.WARNING, "Fatal error occured when saving session for ${event.player.name}", e
                )
            }
        } as Runnable

        this.plugin.server.scheduler.runTaskAsynchronously(this.plugin, task)
    }
}
