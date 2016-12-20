package me.flamin.playtimetracker.listeners

import com.avaje.ebean.Update
import com.mongodb.client.MongoCollection
import me.flamin.playtimetracker.PlayTimeTracker
import groovy.sql.Sql
import org.bson.Document
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

import java.sql.SQLException
import java.util.logging.Level

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;
import com.mongodb.client.model.UpdateOptions;

class LoginListener implements Listener {
    private final PlayTimeTracker plugin;

    public LoginListener(PlayTimeTracker plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    def playerJoinListener(PlayerJoinEvent event) {
        this.plugin.onPlayerActivity(event.player)
    }
}
