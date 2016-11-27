package me.flamin.playtimetracker.activity_listeners

import com.google.common.collect.ImmutableList
import me.flamin.playtimetracker.PlayTimeTracker
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.AsyncPlayerChatEvent

class ChatListener extends AbstractListener {
    public static final List<String> signature = ImmutableList.of();
    protected ChatListener(PlayTimeTracker tracker) {
        super(tracker, [AsyncPlayerChatEvent])
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    void onPlayerChat(AsyncPlayerChatEvent event) {
        if (event.asynchronous) {
            final uuid = event.player.uniqueId;
            this.tracker.server.scheduler.runTask(
                    this.tracker,
                    {
                        final player = this.tracker.server.getPlayer(uuid);
                        if (player != null) {
                            this.tracker.onPlayerActivity(player)
                        }
                    } as Runnable
            )
        } else {
            this.tracker.onPlayerActivity(event.player)
        }
    }
}
