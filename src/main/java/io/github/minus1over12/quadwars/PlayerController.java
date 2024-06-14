package io.github.minus1over12.quadwars;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Controls the players in the game.
 *
 * @author War Pigeon
 */
public class PlayerController implements Listener {
    
    private static GameState state;
    
    /**
     * Handles player events when the game state changes.
     *
     * @param event The event that triggered this method.
     */
    @EventHandler
    public static void onGameStateChange(GameStateChangeEvent event) {
        state = event.getState();
        switch (state) {
            case PREGAME, PREP -> {
            }
            case BATTLE -> {
                Bukkit.getServer().playSound(
                        Sound.sound(Key.key("event.raid.horn"), Sound.Source.MASTER,
                                Float.MAX_VALUE, 1), 0, 256, 0);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }
            case POSTGAME -> {
                Bukkit.getServer().stopSound(SoundStop.source(Sound.Source.MUSIC));
                Bukkit.getServer()
                        .playSound(Sound.sound(Key.key("music.credits"), Sound.Source.MUSIC, 1, 1));
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getGameMode().equals(GameMode.SURVIVAL)) {
                        player.setGameMode(GameMode.ADVENTURE);
                    }
                }
            }
        }
        for (Player player : Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.hasPermission("quadwars.gamemaster") &&
                        Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player) ==
                                null).toList()) {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }
    
    @EventHandler
    public void onPlayerPostRespawn(PlayerPostRespawnEvent event) {
        if (state == GameState.BATTLE || state == GameState.POSTGAME) {
            event.getPlayer().setGameMode(GameMode.SPECTATOR);
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (state == GameState.BATTLE || state == GameState.POSTGAME) {
            event.getPlayer().setGameMode(GameMode.SPECTATOR);
        }
    }
    
}
