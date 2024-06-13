package io.github.minus1over12.quadwars;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Controls the world settings based on the game state.
 *
 * @author War Pigeon
 */
public class WorldControl implements Listener {
    /**
     * Configures all worlds based on the game state.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public static void onGameStateChange(GameStateChangeEvent event) {
        GameState state = event.getState();
        Bukkit.getWorlds().forEach(world -> {
            switch (state) {
                case PREGAME, POSTGAME, PREP -> {
                    world.setPVP(false);
                    world.setHardcore(false);
                }
                case BATTLE -> {
                    world.setPVP(true);
                    world.setHardcore(true);
                }
            }
        });
    }
}
