package me.flamin.playtimetracker.activity_listeners

import me.flamin.playtimetracker.PlayTimeTracker
import org.bukkit.event.Event
import org.bukkit.event.Listener

abstract class AbstractListener implements Listener {
    protected final PlayTimeTracker tracker;
    public final List<Class<? extends Event>> events;

    protected AbstractListener(PlayTimeTracker tracker, events) {
        this.tracker = tracker;
        this.events = events;
    }
}
