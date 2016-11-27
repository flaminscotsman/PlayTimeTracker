package me.flamin.playtimetracker


import com.mongodb.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.UpdateOptions
import me.flamin.playtimetracker.activity_listeners.*
import me.flamin.playtimetracker.listeners.LoginListener
import org.bson.Document
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin
import org.joda.time.DateTime
import org.joda.time.Seconds

import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Level

import static com.mongodb.client.model.Filters.*
import static com.mongodb.client.model.Updates.*

class PlayTimeTracker extends JavaPlugin {
    private List<AbstractListener> listeners = []
    private Map<String, Class<? extends AbstractListener>> availableListeners = [
            'movement': MovementListener,
            'chat': ChatListener
    ]
    private MongoClient mongo_client
    private Lock lock = new ReentrantLock()
    private Map<UUID, DateTime> last_active_time = new HashMap<>()
    private int timeout_duration

    private def timeoutHandlerRef
    private Runnable timeoutHandler = {
        lock.lock()
        try {
            last_active_time.iterator().with {iterator ->
                def now = DateTime.now()
                iterator.each { Map.Entry<UUID, DateTime> entry ->
                    def player = server.getPlayer(entry.key)
                    def last_active = entry.value

                    if (player == null) {
                        // Player has left the server and so ignore
                        logger.info("Expiring entry as player has logged off!")
                        return iterator.remove()
                    }

                    if (Seconds.secondsBetween(last_active, now).seconds > timeout_duration) {
                        logger.info("Expiring entry as ${player.name} has not done anything for ${Seconds.secondsBetween(last_active, now).seconds}s")
                        this.logPlayerActivityChange(player, true)
                        return iterator.remove()
                    }
                }
            }
        } finally {
            lock.unlock()
        }
    } as Runnable

    @Override
    void onEnable() {
        this.saveDefaultConfig()
        try {
            this.readConfig()
        } catch (InstantiationException e) {
            this.logger.log(Level.SEVERE, "Invalid configuration given for a listener!", e)
            this.server.pluginManager.disablePlugin(this)
        }
        this.server.pluginManager.registerEvents(new LoginListener(this), this)
    }

    @Override
    void onDisable() {
        super.onDisable()
    }

    synchronized MongoDatabase getDatabase(String database) {
        return this.mongo_client.getDatabase(database)
    }

    boolean onPlayerActivity(Player player) {
        return this.onPlayerActivity(player, true)
    }

    boolean onPlayerActivity(Player player, boolean log_change) {
        lock.lock()
        try {
            def previous = this.last_active_time.put(player.uniqueId, DateTime.now())
            if (previous == null && log_change) {
                this.logPlayerActivityChange(player, false)
            }
            return previous == null
        } finally {
            lock.unlock()
        }
    }

    void logPlayerActivityChange(Player player, boolean isAfk) {
        def task = {
            try {
                def conn = this.getDatabase('Test')
                MongoCollection collection = conn.getCollection('Activity')

                Date now = new Date()

                def result = collection.updateOne(
                        and(
                                eq('player_id', player.uniqueId),
                                eq('active', true),
                                lt('count', 50)
                        ),
                        combine(
                                push(
                                        'activity_tracker',
                                        new Document([
                                                'server'    : this.server.name,
                                                'world'     : player.location.world.name,
                                                'is_afk'    : isAfk,
                                                'start_time': now
                                        ]),
                                ),
                                setOnInsert('start_time', now),
                                inc('count', 1)
                        ),
                        new UpdateOptions().upsert(true)
                )

                if (result.modifiedCount == 0) {
                    // We created a new document
                    collection.updateOne(
                            and(
                                    eq('player_id', player.uniqueId),
                                    eq('active', true),
                                    ne('_id', result.upsertedId.asObjectId())
                            ),
                            combine(
                                    set('end_time', now),
                                    set('active', false)
                            ),
                            new UpdateOptions().upsert(false)
                    )
                }
            } catch (Exception e) {
                this.logger.log(
                        Level.WARNING, "Fatal error occured when saving session for ${player.name}", e
                )
            }
        } as Runnable

        this.server.scheduler.runTaskAsynchronously(this, task)
    }

    private synchronized readConfig() {
        this.reloadConfig()

        if (this.mongo_client != null) {
            this.mongo_client.close()
        }
        this.mongo_client = new MongoClient(this.getConfig().getString('database.connection'))

        this.timeout_duration = this.config.getLong('timeout', 30)
        if (this.timeoutHandlerRef != null) {
            this.timeoutHandlerRef.cancel()
        }
        this.timeoutHandlerRef = this.server.scheduler.runTaskTimerAsynchronously(this, this.timeoutHandler, 0L, 20L)

        // Clean up any old activity_listeners
        listeners.each {listener ->
            listener.events.each {event ->
                ((HandlerList) event.getHandlerList()).unregister(listener)
            }
        }
        listeners.clear()

        final listener_configs = this.config.getConfigurationSection('enabled_listeners')
        listener_configs.getKeys(false).each { listener_name ->
            if (!listener_name in this.availableListeners) {
                this.logger.warning("Configuration contains entry for unknown listener \"${listener_name}\"")
                return
            }
            final clazz = this.availableListeners[listener_name]
            List<Object> args = new ArrayList<>([this])
            clazz.signature.forEach { key ->
                final conf = listener_configs.getConfigurationSection(listener_name)
                if (!conf.contains(key)) {
                    throw new InstantiationException("Missing configuration entry for ${key} for listener ${listener_name}")
                }
                args.add(conf.get(key))
            }
            final listener = clazz.newInstance(args.toArray())
            this.server.pluginManager.registerEvents(listener, this)
            this.listeners.add(listener)
        }
    }
}
