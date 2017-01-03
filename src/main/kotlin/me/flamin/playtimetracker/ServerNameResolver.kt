package me.flamin.playtimetracker

import com.google.common.io.ByteStreams
import lilypad.client.connect.api.Connect
import lilypad.client.connect.api.request.RequestException
import lilypad.client.connect.api.request.impl.GetWhoamiRequest
import lilypad.client.connect.api.result.StatusCode
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.plugin.RegisteredServiceProvider
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.messaging.PluginMessageListener
import org.bukkit.plugin.messaging.PluginMessageListenerRegistration
import org.bukkit.scheduler.BukkitTask
import java.util.logging.Level


class ServerNameResolver constructor(val plugin: JavaPlugin): Listener {
    private var attempt: Int = 0
    private var resolvers: List<String>
    private var internal_server_name: String? = null

    // Bungee specific variables -> can't rely purely on closure scope like with lilypad
    private var bungeeResolvedCallback: PluginMessageListener? = null
    private var bungeeEventsEnabled = false
    private var bungeeTimeoutTaskID: BukkitTask? = null

    init {
        this.resolvers = this.plugin.config.getStringList("server_name_resolvers") ?: listOf("lilypad", "bungee", "config")
        if (this.resolvers.isEmpty()) {
            this.resolvers = listOf("lilypad", "bungee", "config")
        }
        this.loadResolver()
    }

    @Synchronized
    fun getServerName(): String {
        return this.internal_server_name ?: this.plugin.config.getString("server_name", this.plugin.server.serverName)
    }

    @Synchronized
    private fun setServerName(server_name: String) {
        this.internal_server_name = server_name
    }

    private fun loadResolver() {
        if (!this.plugin.server.isPrimaryThread) {
            this.plugin.server.scheduler.runTask(this.plugin, {this.loadResolver()})
            return
        }

        val resolver = this.resolvers[this.attempt++]
        if (resolver.equals("lilypad", ignoreCase = true)) {
            this.resolveUsingLilyPad()
        } else if (resolver.equals("bungee", ignoreCase = true)) {
            this.resolveUsingBungeeCord()
        } else if (resolver.equals("config", ignoreCase = true)) {
            this.resolveUsingConfig()
        } else {
            throw IllegalArgumentException("Unknown resolver \"${resolver}\"!")
        }
    }

    private fun resolveUsingLilyPad() {
        val rsp: RegisteredServiceProvider<Connect>?
        try {
            rsp = plugin.server.servicesManager.getRegistration(Connect::class.java)
        } catch (e: NoClassDefFoundError) {
            return this.loadResolver()
        }

        if (rsp == null) {
            return loadResolver()
        }

        val client = rsp.provider
        var taskId: BukkitTask? = null

        val waitUntilConnected = Runnable {
            if (!client.isConnected) {
                return@Runnable
            }

            taskId!!.cancel()

            try {
                val request = client.request(GetWhoamiRequest())

                request.registerListener({ result ->
                    if (result.statusCode != StatusCode.SUCCESS) {
                        plugin.logger.log(
                                Level.INFO,
                                "Failed to resolve servername using lilypad."
                        )
                        loadResolver()
                    } else {
                        this@ServerNameResolver.setServerName(result.identification)
                    }
                })

            } catch (e: RequestException) {
                plugin.logger.log(
                        Level.INFO,
                        "Encountered an error when attempting to resolve servername using lilypad.",
                        e
                )
                loadResolver()
            }
        }

        taskId = this.plugin.server.scheduler.runTaskTimerAsynchronously(this.plugin, waitUntilConnected, 0L, 20L)
    }

    private fun resolveUsingBungeeCord() {
        var incomingRegistration: PluginMessageListenerRegistration? = null

        this.bungeeResolvedCallback = PluginMessageListener { channel: String, player: Player, message: ByteArray ->
            if (this.bungeeResolvedCallback == null) {
                // We've been timed-out
                return@PluginMessageListener
            }

            if (channel != "BungeeCord") {
                return@PluginMessageListener
            }

            val incoming = ByteStreams.newDataInput(message)
            val subchannel = incoming.readUTF()
            if (subchannel == "GetServer") {
                this.setServerName(incoming.readUTF())

                this.bungeeTimeoutTaskID!!.cancel()

                if (this.bungeeEventsEnabled) {
                    HandlerList.unregisterAll(this@ServerNameResolver)
                }

                assert (incomingRegistration is PluginMessageListenerRegistration)
                this.plugin.server.messenger.unregisterIncomingPluginChannel(
                        incomingRegistration!!.plugin,
                        incomingRegistration!!.channel,
                        incomingRegistration!!.listener
                )
            }
        }

        incomingRegistration = this.plugin.server.messenger.registerIncomingPluginChannel(this.plugin, "BungeeCord", this.bungeeResolvedCallback)

        if (!this.plugin.server.onlinePlayers.isEmpty()) {
            val player = this.plugin.server.onlinePlayers.first()

            val out = ByteStreams.newDataOutput()
            out.writeUTF("GetServer")
            player.sendPluginMessage(this.plugin, "BungeeCord", out.toByteArray())
        } else {
            // We need to wait for a player to login
            this.bungeeEventsEnabled = true
            this.plugin.server.pluginManager.registerEvents(this, this.plugin)
        }

        // If timeout is negative, don't cancel, just wait till resolved
        if (this.plugin.config.getLong("bungeeResolveTimeout", 60L) >= 0L) {
            this.bungeeTimeoutTaskID = this.plugin.server.scheduler.runTaskLater(this.plugin, {
                this.bungeeResolvedCallback = null

                if (this.bungeeEventsEnabled) {
                    HandlerList.unregisterAll(this)
                }

                assert(incomingRegistration is PluginMessageListenerRegistration)
                this.plugin.server.messenger.unregisterIncomingPluginChannel(
                        incomingRegistration!!.plugin,
                        incomingRegistration!!.channel,
                        incomingRegistration!!.listener
                )

                this.loadResolver()
            } as Runnable, this.plugin.config.getLong("bungeeResolveTimeout", 60L) * 20L)
            // Give it (default) 60s to resolve before trying next resolver
        }
    }

    private fun resolveUsingConfig() {
        this.setServerName(this.plugin.config.getString("server_name", this.plugin.server.serverName))
    }

    @EventHandler
    private fun onPlayerLoginEvent(event: PlayerLoginEvent) {
        val out = ByteStreams.newDataOutput()
        out.writeUTF("GetServer")

        event.player.sendPluginMessage(this.plugin, "BungeeCord", out.toByteArray())
    }
}
