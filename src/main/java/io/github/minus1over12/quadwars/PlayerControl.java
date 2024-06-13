package io.github.minus1over12.quadwars;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Controls the players in the game.
 *
 * @author War Pigeon
 */
public class PlayerControl implements Listener {
    /**
     * Handles player events when the game state changes.
     * @param event The event that triggered this method.
     */
    @EventHandler
    public static void onGameStateChange(GameStateChangeEvent event) {
        GameState state = event.getState();
        switch (state) {
            case PREGAME, PREP, POSTGAME -> {
            }
            case BATTLE -> {
                Bukkit.getOnlinePlayers().forEach(player -> player.playSound(
                        Sound.sound(Key.key("event.raid.horn"), Sound.Source.MASTER, 1, 1)));
            }
        }
    }
    
}
