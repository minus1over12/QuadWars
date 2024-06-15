package io.github.minus1over12.quadwars;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Controls the players in the game.
 *
 * @author War Pigeon
 */
public class PlayerController implements Listener {
    /**
     * The current state of the game.
     */
    private static GameState state;
    /**
     * If players should be killed if they quit in the battle phase.
     */
    private final boolean killOnQuit;
    /**
     * If the plugin is running in hardcore mode.
     */
    private final boolean hardcore;
    
    /**
     * Creates a player controller.
     *
     * @param plugin the plugin creating this controller.
     */
    public PlayerController(Plugin plugin) {
        killOnQuit = plugin.getConfig().getBoolean("killOnQuit");
        hardcore = plugin.getConfig().getBoolean(QuadWars.HARDCORE_CONFIG_PATH);
    }
    
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
            case POST_GAME -> {
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
                .filter(player -> player.hasPermission(QuadWars.GAMEMASTER_PERMISSION) &&
                        Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player) ==
                                null).toList()) {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }
    
    /**
     * Sets the player to spectator mode after they respawn during battle.
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onPlayerPostRespawn(PlayerPostRespawnEvent event) {
        setSpectatorIfNeeded(event.getPlayer());
    }
    
    /**
     * Sets the player to spectator mode if they die during battle.
     * @param event the event that triggered this method
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        setSpectatorIfNeeded(event.getPlayer());
    }
    
    /**
     * Sets the player's game mode to spectator if they die during battle.
     *
     * @param player the player to set the game mode for
     */
    private void setSpectatorIfNeeded(HumanEntity player) {
        if (hardcore && (state == GameState.BATTLE || state == GameState.POST_GAME)) {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }
    
    /**
     * Kills the player if they quit during the battle phase and are not allowed to.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        killIfNeeded(event.getPlayer());
    }
    
    /**
     * Kills the player if they join/leave during the battle phase and are not allowed to.
     *
     * @param player the player to check
     */
    private void killIfNeeded(Damageable player) {
        if (hardcore && killOnQuit && state == GameState.BATTLE &&
                !player.hasPermission(QuadWars.GAMEMASTER_PERMISSION)) {
            player.setHealth(0);
        }
    }
    
    /**
     * Kills the player if they join during the battle phase and are not allowed to.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        killIfNeeded(event.getPlayer());
    }
    
}
