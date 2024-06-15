package io.github.minus1over12.quadwars;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Collection;

/**
 * Controls the world settings based on the game state.
 *
 * @author War Pigeon
 */
public class WorldController implements Listener {
    /**
     * NamespacedKeys to ignore.
     */
    private final Collection<NamespacedKey> ignoredWorldKeys;
    /**
     * If the plugin is running in hardcore mode.
     */
    private final boolean hardcore;
    
    /**
     * Creates a world control object.
     *
     * @param ignoredWorldKeys the keys to ignore
     * @param plugin the plugin creating the object
     */
    WorldController(Collection<NamespacedKey> ignoredWorldKeys, Plugin plugin) {
        this.ignoredWorldKeys = ignoredWorldKeys;
        hardcore = plugin.getConfig().getBoolean(QuadWars.HARDCORE_CONFIG_PATH);
    }
    
    /**
     * Configures all worlds based on the game state.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onGameStateChange(GameStateChangeEvent event) {
        GameState state = event.getState();
        for (World world : Bukkit.getWorlds().stream()
                .filter(world -> !ignoredWorldKeys.contains(world.getKey())).toList()) {
            switch (state) {
                case PREGAME, POST_GAME, PREP -> {
                    world.setPVP(false);
                    world.setHardcore(false);
                }
                case BATTLE -> {
                    world.setPVP(true);
                    world.setHardcore(hardcore);
                }
            }
        }
    }
}
