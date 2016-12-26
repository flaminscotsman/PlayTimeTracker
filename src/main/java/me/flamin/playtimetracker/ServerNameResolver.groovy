package me.flamin.playtimetracker

import com.google.common.io.ByteStreams
import lilypad.client.connect.api.Connect
import lilypad.client.connect.api.request.RequestException
import lilypad.client.connect.api.request.impl.GetWhoamiRequest
import lilypad.client.connect.api.result.FutureResultListener
import lilypad.client.connect.api.result.StatusCode
import lilypad.client.connect.api.result.impl.GetWhoamiResult
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.plugin.RegisteredServiceProvider
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.messaging.PluginMessageListener
import org.bukkit.plugin.messaging.PluginMessageListenerRegistration

import java.util.logging.Level


class ServerNameResolver implements Listener {
    private JavaPlugin plugin
    private List<String> resolvers
    private attempt = 0
    private String internal_server_name

    // Bungee specific variables -> can't rely purely on closure scope like with lilypad
    private bungeeResolvedCallback
    private bungeeEventsEnabled = false
    private bungeeTimeoutTaskID

    ServerNameResolver(JavaPlugin plugin) {
        this.plugin = plugin
        this.resolvers = this.plugin.config.getStringList('server_name_resolvers')
        if (this.resolvers == null || this.resolvers.empty) {
            this.resolvers = ['lilypad', 'bungee', 'config']
        }
        this.loadResolver()
    }

    synchronized String getServerName() {
        if (this.internal_server_name == null) {
            // Use the value from the config before a more definitive source has resolved.
            return this.plugin.config.getString('server_name', this.plugin.server.serverName)
        }
        return this.internal_server_name
    }

    synchronized void setServerName(String server_name) {
        this.internal_server_name = server_name
    }

    private loadResolver() {
        if (!this.plugin.server.primaryThread) {
            this.plugin.server.scheduler.runTask(this.plugin, {this.loadResolver()} as Runnable)
            return
        }

        def resolver = this.resolvers[this.attempt++]
        if (resolver.equalsIgnoreCase('lilypad')) {
            this.resolveUsingLilyPad()
        } else if (resolver.equalsIgnoreCase('bungee')) {
            this.resolveUsingBungeeCord()
        } else if (resolver.equalsIgnoreCase('config')) {
            this.resolveUsingConfig()
        } else {
            throw new IllegalArgumentException("Unknown resolver \"${resolver}\"!")
        }
    }

    private resolveUsingLilyPad() {
        RegisteredServiceProvider<Connect> rsp
        try {
            rsp = plugin.server.servicesManager.getRegistration(Connect.class)
        } catch (NoClassDefFoundError e) {
            return this.loadResolver()
        }

        if (rsp == null) {
            return loadResolver()
        }

        final client = rsp.getProvider()
        final taskId

        def waitUntilConnected = {
            if (!client.connected) {
                return
            }

            taskId.cancel()

            try {
                def request = client.request(new GetWhoamiRequest())

                request.registerListener({result ->
                    if (result.statusCode != StatusCode.SUCCESS) {
                        plugin.logger.log(
                                Level.INFO,
                                "Failed to resolve servername using lilypad."
                        )
                        loadResolver()
                    } else {
                        this.serverName = result.identification
                    }
                } as FutureResultListener<GetWhoamiResult>)

            } catch (RequestException e) {
                plugin.logger.log(
                        Level.INFO,
                        "Encountered an error when attempting to resolve servername using lilypad.",
                        e
                )
                loadResolver()
            }
        } as Runnable

        taskId = this.plugin.server.scheduler.runTaskTimerAsynchronously(this.plugin, waitUntilConnected, 0L, 20L)
    }

    private resolveUsingBungeeCord() {
        def incomingRegistration

        this.bungeeResolvedCallback = { String channel, Player player, byte[] message ->
            if (this.bungeeResolvedCallback == null) {
                // We've been timed-out
                return
            }

            if (channel != "BungeeCord") {
                return
            }

            def incoming = ByteStreams.newDataInput(message)
            def subchannel = incoming.readUTF()
            if (subchannel == "GetServer") {
                this.serverName = incoming.readUTF()

                this.bungeeTimeoutTaskID.cancel()

                if (this.bungeeEventsEnabled) {
                    HandlerList.unregisterAll(this)
                }

                assert incomingRegistration instanceof PluginMessageListenerRegistration
                this.plugin.server.messenger.unregisterIncomingPluginChannel(
                        incomingRegistration.plugin,
                        incomingRegistration.channel,
                        incomingRegistration.listener
                )
            }
        } as PluginMessageListener

        incomingRegistration = this.plugin.server.messenger.registerIncomingPluginChannel(this.plugin, "BungeeCord", this.bungeeResolvedCallback)

        if (!this.plugin.server.onlinePlayers.empty) {
            def player = this.plugin.server.onlinePlayers.first()

            def out = ByteStreams.newDataOutput()
            out.writeUTF("GetServer")
            player.sendPluginMessage(this.plugin, "BungeeCord", out.toByteArray())
        } else {
            // We need to wait for a player to login
            this.bungeeEventsEnabled = true
            this.plugin.server.pluginManager.registerEvents(this, this.plugin)
        }

        this.bungeeTimeoutTaskID = this.plugin.server.scheduler.runTaskLater(this.plugin, {
            this.bungeeResolvedCallback = null

            if (this.bungeeEventsEnabled) {
                HandlerList.unregisterAll(this)
            }

            assert incomingRegistration instanceof PluginMessageListenerRegistration
            this.plugin.server.messenger.unregisterIncomingPluginChannel(
                    incomingRegistration.plugin,
                    incomingRegistration.channel,
                    incomingRegistration.listener
            )

            this.loadResolver()
        } as Runnable, this.plugin.config.getLong('bungeeResolveTimeout', 5L) * 20L)
        // Give it (default) 5s to resolve before trying next resolver
    }

    def resolveUsingConfig() {
        this.serverName = this.plugin.config.getString('server_name', this.plugin.server.serverName)
    }

    @EventHandler
    onPlayerLoginEvent(PlayerLoginEvent event) {
        def out = ByteStreams.newDataOutput()
        out.writeUTF("GetServer")

        event.player.sendPluginMessage(this.plugin, "BungeeCord", out.toByteArray())
    }
}
