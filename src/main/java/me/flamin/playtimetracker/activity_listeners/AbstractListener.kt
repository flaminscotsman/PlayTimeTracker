package me.flamin.playtimetracker.activity_listeners

import me.flamin.playtimetracker.PlayTimeTracker
import org.bukkit.event.Event
import org.bukkit.event.Listener

abstract class AbstractListener protected constructor(protected val tracker: PlayTimeTracker, val events: List<Class<out Event>>) : Listener {
    companion object {
        val signature: List<String> = listOf()
    }
}
