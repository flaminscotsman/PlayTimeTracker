package me.flamin.playtimetracker.activity_listeners

import com.google.common.collect.ImmutableList
import me.flamin.playtimetracker.PlayTimeTracker
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerCommandPreprocessEvent

class CommandListener extends AbstractListener {
    public static final List<String> signature = ImmutableList.of();
    protected CommandListener(PlayTimeTracker tracker) {
        super(tracker, [PlayerCommandPreprocessEvent])
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    void onCommandPreProcess(PlayerCommandPreprocessEvent event) {
        this.tracker.onPlayerActivity(event.player)
    }
}
