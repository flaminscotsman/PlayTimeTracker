package me.flamin.playtimetracker


import com.mongodb.MongoClient
import com.mongodb.MongoClientOptions
import com.mongodb.MongoClientURI
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.UpdateOptions
import me.flamin.playtimetracker.activity_listeners.*
import me.flamin.playtimetracker.listeners.LoginListener
import org.bson.Document
import org.bson.UuidRepresentation
import org.bson.codecs.UuidCodecProvider
import org.bson.codecs.configuration.CodecRegistries
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
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

class PlayTimeTracker extends JavaPlugin implements CommandExecutor {
    private List<AbstractListener> listeners = []
    private Map<String, Class<? extends AbstractListener>> availableListeners = [
            'block': BlockListener,
            'chat': ChatListener,
            'command': CommandListener,
            'movement': MovementListener,
    ]
    private MongoClient mongo_client
    private Lock lock = new ReentrantLock()
    private Map<UUID, DateTime> last_active_time = new HashMap<>()
    private int timeout_duration
    private ServerNameResolver serverNameResolver

    private timeoutHandlerRef
    private Runnable timeoutHandler = {
        def expired_players = new ArrayList<Player>(last_active_time.size())
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
                        expired_players.add(player)
                        return iterator.remove()
                    }
                }
            }
        } finally {
            lock.unlock()
        }

        expired_players.each {player ->
            this.logPlayerActivityChange(player, true)
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

    @Override
    boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.name.equalsIgnoreCase('playtimetracker')) {
            return false
        }
        if (args.length > 1 || !args[0].equalsIgnoreCase('reload')) {
            sender.sendMessage(ChatColor.GRAY.toString() + String.format("[PlaytimeTracker] Usage: /%s reload", alias))
            return true
        }
        this.readConfig()
        sender.sendMessage(ChatColor.GRAY.toString() + "[PlaytimeTracker] Reloaded config.")
        return true
    }

    synchronized MongoDatabase getDatabase(String database) {
        return this.mongo_client.getDatabase(database)
    }

    boolean onPlayerActivity(Player player) {
        return this.onPlayerActivity(player, true)
    }

    boolean onPlayerActivity(Player player, boolean log_change) {
        def previous
        lock.lock()
        try {
            previous = this.last_active_time.put(player.uniqueId, DateTime.now())
        } finally {
            lock.unlock()
        }

        if (previous == null && log_change) {
            this.logPlayerActivityChange(player, false)
        }
        return previous == null
    }

    void logPlayerActivityChange(Player player, boolean isAfk) {
        final database = this.config.getString('database.database', 'PlaytimeTracker')
        final collection_name = this.config.getString('database.collection', 'Activity')

        def task = {
            try {
                def conn = this.getDatabase(database)
                MongoCollection collection = conn.getCollection(collection_name)

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
                                                'server'    : this.serverNameResolver.serverName,
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

        this.serverNameResolver = new ServerNameResolver(this);

        if (this.mongo_client != null) {
            this.mongo_client.close()
        }
        def codecRegistry = CodecRegistries.fromRegistries(
                MongoClient.getDefaultCodecRegistry(),
                CodecRegistries.fromProviders(new UuidCodecProvider(UuidRepresentation.STANDARD))
        )

        def uri = new MongoClientURI(this.getConfig().getString('database.connection'), MongoClientOptions.builder().codecRegistry(codecRegistry))
        this.logger.info("Connection to mongodb using ${uri.credentials.userName} / ${uri.credentials.password}")

        this.mongo_client = new MongoClient(uri)

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
