package me.flamin.playtimetracker

import com.google.common.collect.Lists
import com.mongodb.MongoClient
import com.mongodb.client.MongoDatabase
import me.flamin.playtimetracker.activity_listeners.*
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin

import java.util.logging.Level

class PlayTimeTracker extends JavaPlugin {
    private List<AbstractListener> listeners = [];
    private Map<String, Class<? extends AbstractListener>> availableListeners = [
            'movement': MovementListener,
            'chat': ChatListener
    ]
//    private HikariDataSource pool;
    private MongoClient pool;

    @Override
    void onEnable() {
        this.saveDefaultConfig()
        try {
            this.readConfig()
        } catch (InstantiationException e) {
            this.logger.log(Level.SEVERE, "Invalid configuration given for a listener!", e)
            this.server.pluginManager.disablePlugin(this)
        }
    }

    @Override
    void onDisable() {
        super.onDisable()
    }

    public synchronized MongoDatabase getDatabase(String database) {
        return this.pool.getDatabase(database);
    }

    public onPlayerActivity(Player player) {
        this.logger.info(player.displayName)
    }

    public synchronized readConfig() {
        this.reloadConfig()

        if (this.pool != null) {
            this.pool.close()
        }
        this.pool = new MongoClient(this.getConfig().getString('database.connection'))

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
            List<Object> args = Lists.<Object>asList(this);
            args.addAll(clazz.signature.collect{ key ->
                final conf = listener_configs.getConfigurationSection(listener_name)
                if (!conf.contains(key)) {
                    throw new InstantiationException("Missing configuration entry for ${key} for listener ${listener_name}")
                }
                return conf.get(key)
            })
            final listener = clazz.newInstance(args.toArray());
            this.server.pluginManager.registerEvents(listener, this)
            this.listeners.add(listener)
        }
    }
}
