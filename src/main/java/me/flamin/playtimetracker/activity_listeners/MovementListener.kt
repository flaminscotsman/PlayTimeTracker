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
import java.util.*

internal class MovementListener constructor(tracker: PlayTimeTracker, minimumDistance: Int, duration: Int) : AbstractListener(tracker, arrayListOf(PlayerMoveEvent::class.java)) {
    private val lastLocation: MutableMap<UUID, ExpiringLocation> = HashMap()
    private val minimumDistanceSquared: Int
    private val duration: Duration

    init {
        this.minimumDistanceSquared = minimumDistance * minimumDistance
        this.duration = Duration(duration)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (event.player.uniqueId in this.lastLocation &&
                !this.lastLocation[event.player.uniqueId]!!.expiry_date.containsNow()) {
             this.lastLocation.remove(event.player.uniqueId)
        }
        if (event.player.uniqueId in this.lastLocation) {
            if (this.lastLocation[event.player.uniqueId]!!.location.world != event.to.world ||
                    this.lastLocation[event.player.uniqueId]!!.location.distanceSquared(event.to) > this.minimumDistanceSquared) {
                this.tracker.onPlayerActivity(event.player)
                this.lastLocation.remove(event.player.uniqueId)
            }
        } else {
            this.lastLocation[event.player.uniqueId] = ExpiringLocation(
                    event.from,
                    duration.toIntervalFrom(Instant.now())
            )
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        this.lastLocation.remove(event.player.uniqueId)
    }

    private data class ExpiringLocation constructor(val location: Location, val expiry_date: Interval)

    companion object {
        @Suppress("unused")
        val signature: List<String> = ImmutableList.of("minimumDistance", "duration")
    }
}
