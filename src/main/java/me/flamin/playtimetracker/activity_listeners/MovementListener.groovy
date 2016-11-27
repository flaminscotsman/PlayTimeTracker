package me.flamin.playtimetracker.activity_listeners

import com.google.common.collect.ImmutableList
import me.flamin.playtimetracker.PlayTimeTracker
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.joda.time.Duration
import org.joda.time.Instant
import org.joda.time.Interval

class MovementListener extends AbstractListener {
    public static final List<String> signature = ImmutableList.of('minimumDistance', 'duration');

    private Map<UUID, ExpiringLocation> lastLocation = [:];
    private final minimumDistanceSquared;
    Duration duration

    protected MovementListener(PlayTimeTracker tracker, Object minimumDistance, Integer duration) {
        super(tracker, [PlayerMoveEvent, PlayerQuitEvent])
        this.minimumDistanceSquared = minimumDistance * minimumDistance
        this.duration = new Duration(duration * 1000L)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    void onPlayerMove(PlayerMoveEvent event) {
        if (event.player.uniqueId in this.lastLocation &&
                !this.lastLocation[event.player.uniqueId].expiry_date.containsNow()) {
             this.lastLocation.remove(event.player.uniqueId)
        }
        if (event.player.uniqueId in this.lastLocation) {
            if (this.lastLocation[event.player.uniqueId].location.distanceSquared(event.to) > this.minimumDistanceSquared) {
                this.tracker.onPlayerActivity(event.player)
                this.lastLocation.remove(event.player.uniqueId)
            }
        } else {
            this.lastLocation[event.player.uniqueId] = new ExpiringLocation(
                    location: event.from,
                    expiry_date: this.duration.toIntervalFrom(Instant.now())
            )
        }
    }

    class ExpiringLocation {
        Location location
        Interval expiry_date
    }
}
