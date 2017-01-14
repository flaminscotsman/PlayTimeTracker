package me.flamin.playtimetracker

import com.google.common.primitives.Primitives
import com.mongodb.MongoClient
import com.mongodb.MongoClientOptions
import com.mongodb.MongoClientURI
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates.*
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
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.joda.time.DateTime
import org.joda.time.Seconds
import java.lang.reflect.Modifier
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Level

class PlayTimeTracker : JavaPlugin(), CommandExecutor {
    private val listeners: MutableList<AbstractListener> = mutableListOf()
    private val availableListeners: Map<String, Class<out AbstractListener>> = mapOf(
            "block" to BlockListener::class.java,
            "chat" to ChatListener::class.java,
            "command" to CommandListener::class.java,
            "movement" to MovementListener::class.java
    )
    private val lock: Lock = ReentrantLock()
    private val last_active_time: MutableMap<UUID, DateTime> = HashMap()
    private var mongo_client: MongoClient? = null
    private var timeout_duration: Long? = null
    private var serverNameResolver: ServerNameResolver? = null

    private var timeoutHandlerRef: BukkitTask? = null
    private val timeoutHandler: Runnable = Runnable {
        val expired_players = ArrayList<Player>(last_active_time.size)
        lock.lock()
        try {
            val now = DateTime.now()
            val iterator = last_active_time.iterator()
            iterator.forEach { entry ->
                val player = server.getPlayer(entry.key)
                val last_active = entry.value

                if (player == null) {
                    // Player has left the server and so ignore
                    logger.info("Expiring entry as player has logged off!")
                    iterator.remove()
                }

                if (Seconds.secondsBetween(last_active, now).seconds > timeout_duration!!) {
                    logger.info("Expiring entry as ${player.name} has not done anything for ${Seconds.secondsBetween(last_active, now).seconds}s")
                    expired_players.add(player)
                    iterator.remove()
                }
            }
        } finally {
            lock.unlock()
        }

        expired_players.forEach { player ->
            this.logPlayerActivityChange(player, true)
        }
    }

    override fun onEnable() {
        this.saveDefaultConfig()
        try {
            this.readConfig()
        } catch (e: InstantiationException) {
            this.logger.log(Level.SEVERE, "Invalid configuration given for a listener!", e)
            this.server.pluginManager.disablePlugin(this)
        }
        this.server.pluginManager.registerEvents(LoginListener(this), this)
        this.server.pluginManager.registerEvents(MovementListener(this), this)
    }

    override fun onCommand(sender: CommandSender, command: Command, alias: String, args: Array<String>): Boolean {
        if (!command.name.equals("playtimetracker", ignoreCase = true)) {
            return false
        }
        if (args.size > 1 || !args[0].equals("reload", ignoreCase = true)) {
            sender.sendMessage(ChatColor.GRAY.toString() + String.format("[PlaytimeTracker] Usage: /%s reload", alias))
            return true
        }
        this.readConfig()
        sender.sendMessage(ChatColor.GRAY.toString() + "[PlaytimeTracker] Reloaded config.")
        return true
    }

    @Synchronized
    fun getDatabase(database: String): MongoDatabase {
        return this.mongo_client!!.getDatabase(database)
    }

    fun onPlayerActivity(player: Player): Boolean {
        return this.onPlayerActivity(player, true)
    }

    fun onPlayerActivity(player: Player, log_change: Boolean): Boolean {
        val previous: DateTime?
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

    fun logPlayerActivityChange(player: Player, isAfk: Boolean) {
        val database = this.config.getString("database.database", "PlaytimeTracker")
        val collection_name = this.config.getString("database.collection", "Activity")

        val task = Runnable {
            try {
                val conn = this.getDatabase(database)
                val collection = conn.getCollection(collection_name)

                val now = Date()

                val result = collection.updateOne(
                        and(
                                eq("player_id", player.uniqueId),
                                eq("active", true),
                                lt("count", 50)
                        ),
                        combine(
                                push(
                                        "activity_tracker",
                                        Document(mapOf(
                                                "server" to this.serverNameResolver!!.getServerName(),
                                                "world" to player.location.world.name,
                                                "is_afk" to isAfk,
                                                "start_time" to now
                                        ))
                                ),
                                setOnInsert("start_time", now),
                                inc("count", 1)
                        ),
                        UpdateOptions().upsert(true)
                )

                if (result.modifiedCount == 0L) {
                    // We created a new document
                    collection.updateOne(
                            and(
                                    eq("player_id", player.uniqueId),
                                    eq("active", true),
                                    ne("_id", result.upsertedId.asObjectId())
                            ),
                            combine(
                                    set("end_time", now),
                                    set("active", false)
                            ),
                            UpdateOptions().upsert(false)
                    )
                }
            } catch (e: Exception) {
                this.logger.log(
                        Level.WARNING, "Fatal error occured when saving session for ${player.name}", e
                )
            }
        }

        this.server.scheduler.runTaskAsynchronously(this, task)
    }

    @Synchronized
    fun readConfig() {
        this.reloadConfig()

        this.serverNameResolver = ServerNameResolver(this)

        this.mongo_client?.close()
        val codecRegistry = CodecRegistries.fromRegistries(
                MongoClient.getDefaultCodecRegistry(),
                CodecRegistries.fromProviders(UuidCodecProvider(UuidRepresentation.STANDARD))
        )
        val uri = MongoClientURI(this.config.getString("database.connection"), MongoClientOptions.builder().codecRegistry(codecRegistry))

        this.mongo_client = MongoClient(uri)

        this.timeout_duration = this.config.getLong("timeout", 30)
        this.timeoutHandlerRef?.cancel()
        this.timeoutHandlerRef = this.server.scheduler.runTaskTimerAsynchronously(this, this.timeoutHandler, 0L, 20L)

        // Clean up any old activity_listeners
        listeners.forEach { listener ->
            listener.events.forEach { event ->
                event.newInstance().handlers.unregister(listener)
            }
        }
        listeners.clear()

        val listener_configs = this.config.getConfigurationSection("enabled_listeners")
        listener_configs.getKeys(false).forEach { listener_name ->
            if (listener_name !in this.availableListeners) {
                this.logger.warning("Configuration contains entry for unknown listener \"${listener_name}\"")
                return
            }
            val clazz = this.availableListeners[listener_name]!!
            val args: MutableList<Any> = mutableListOf(this)
            val field = clazz.declaredMethods.first { it.name == "getSignature" && Modifier.isStatic(it.modifiers) }
            (field(null) as List<String>).forEach { key ->
                val conf = listener_configs.getConfigurationSection(listener_name)
                if (!conf.contains(key)) {
                    throw InstantiationException("Missing configuration entry for ${key} for listener ${listener_name}")
                }
                args.add(conf.get(key))
            }
            val listener = clazz.declaredConstructors.first {
                val types = it.parameterTypes
                if (types.size != args.size) {
                    return@first false
                }
                types.mapIndexed { idx, paramClass ->
                    if (paramClass.isPrimitive) {
                        val boxed_class = Primitives.wrap(paramClass)
                        // If the wrapper class is the same as that of the argument, continue; otherwise check both are
                        //   numerical, as java will then unbox & widen/shrink to fit!
                        boxed_class.isInstance(args.get(idx)) || (
                                Number::class.java.isAssignableFrom(boxed_class)
                                && Number::class.java.isAssignableFrom(args.get(idx).javaClass)
                        )
                    } else {
                        paramClass.isInstance(args.get(idx))
                    }}.reduce { a, b -> a && b }
            }.newInstance(*args.toTypedArray())
            this.server.pluginManager.registerEvents(listener as Listener, this)
            this.listeners.add(listener as AbstractListener)
        }
    }
}
