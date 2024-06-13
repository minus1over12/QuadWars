package io.github.minus1over12.quadwars;

import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;

/**
 * Controls the lobby world.
 *
 * @author War Pigeon
 */
public class LobbyWorldControl implements Listener {
    /**
     * The lobby world.
     */
    protected final World lobbyWorld;
    /**
     * The current game state.
     */
    private GameState gameState;
    
    /**
     * Creates a lobby world control object.
     *
     * @param plugin the plugin to get the game state from
     */
    protected LobbyWorldControl(QuadWars plugin) {
        gameState = plugin.getGameState();
        lobbyWorld = new WorldCreator(new NamespacedKey(plugin, "lobby")).generateStructures(false)
                .hardcore(false).keepSpawnLoaded(TriState.FALSE)
                .environment(World.Environment.NORMAL).type(WorldType.FLAT).createWorld();
        Objects.requireNonNull(lobbyWorld);
        lobbyWorld.setPVP(false);
        lobbyWorld.setDifficulty(Difficulty.PEACEFUL);
        lobbyWorld.setSpawnFlags(false, false);
        lobbyWorld.setSendViewDistance(2);
        lobbyWorld.setSimulationDistance(2);
        lobbyWorld.setViewDistance(2);
        WorldBorder worldBorder = lobbyWorld.getWorldBorder();
        worldBorder.setCenter(lobbyWorld.getSpawnLocation());
        worldBorder.setSize(16);
    }
    
    /**
     * Teleports players to the lobby world when they join if needed.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (gameState == GameState.PREGAME ||
                (!(player.isOp() || player.hasPermission("quadwars.gamemaster")) &&
                        Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player) ==
                                null)) {
            player.teleport(lobbyWorld.getSpawnLocation());
            player.setGameMode(GameMode.ADVENTURE);
        }
    }
    
    /**
     * Sets the game state.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onGameStateChange(GameStateChangeEvent event) {
        gameState = event.getState();
    }
    
    /**
     * Unloads the lobby world. This should be called when the plugin is disabled.
     */
    void unloadLobby() {
        Bukkit.unloadWorld(lobbyWorld, true);
    }
}
