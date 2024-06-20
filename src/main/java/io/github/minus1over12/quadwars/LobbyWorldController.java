package io.github.minus1over12.quadwars;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
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
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * Controls the lobby world.
 *
 * @author War Pigeon
 */
public class LobbyWorldController implements Listener {
    /**
     * The size of the lobby world border.
     */
    private static final int LOBBY_WORLD_BORDER_SIZE = 16;
    /**
     * The name of the Floodgate plugin.
     */
    private static final String FLOODGATE_NAME = "floodgate";
    /**
     * The command suggestion to join a team.
     */
    private static final String JOINTEAM_COMMAND_SUGGESTION = "/jointeam ";
    /**
     * The lobby world.
     */
    private final World lobbyWorld;
    /**
     * The current game state.
     */
    private GameState gameState;
    
    /**
     * The plugin used for scheduling tasks.
     */
    private final Plugin plugin;
    
    /**
     * Creates a lobby world control object.
     *
     * @param plugin the plugin to get the game state from
     */
    LobbyWorldController(QuadWars plugin) {
        gameState = plugin.getGameState();
        lobbyWorld = Objects.requireNonNull(
                new WorldCreator(new NamespacedKey(plugin, "lobby")).generateStructures(false)
                        .hardcore(false).keepSpawnLoaded(TriState.FALSE)
                        .environment(World.Environment.NORMAL).type(WorldType.FLAT).createWorld(),
                "Could not load lobby");
        lobbyWorld.setPVP(false);
        lobbyWorld.setDifficulty(Difficulty.PEACEFUL);
        lobbyWorld.setSpawnFlags(false, false);
        lobbyWorld.setSendViewDistance(2);
        lobbyWorld.setSimulationDistance(2);
        lobbyWorld.setViewDistance(2);
        lobbyWorld.setHardcore(false);
        WorldBorder worldBorder = lobbyWorld.getWorldBorder();
        worldBorder.setCenter(lobbyWorld.getSpawnLocation());
        worldBorder.setSize(LOBBY_WORLD_BORDER_SIZE);
        this.plugin = plugin;
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
                (!player.hasPermission(QuadWars.GAMEMASTER_PERMISSION) &&
                Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player) == null)) {
            //Not using async because we want to get the player without a team out of the game
            // world right away.
            player.teleport(lobbyWorld.getSpawnLocation());
            player.setGameMode(GameMode.ADVENTURE);
            switch (gameState) {
                case PREGAME -> {
                    player.sendMessage(Component.text(
                                    "The game has not started yet, but you can pick a team with " +
                                            "/jointeam.")
                            .clickEvent(ClickEvent.suggestCommand(JOINTEAM_COMMAND_SUGGESTION)));
                    if (Bukkit.getPluginManager().isPluginEnabled(FLOODGATE_NAME)) {
                        //Cumulus takes 2 ticks to become functional.
                        player.getScheduler().runDelayed(plugin,
                                ignored -> FloodgateIntegration.sendTeamForm(player), null, 2);
                    }
                }
                case PREP -> {
                    player.sendMessage(Component.text(
                                    "The game is in the prep phase, but you can still join a" +
                                            " team with /jointeam.")
                            .clickEvent(ClickEvent.suggestCommand(JOINTEAM_COMMAND_SUGGESTION)));
                    if (Bukkit.getPluginManager().isPluginEnabled(FLOODGATE_NAME)) {
                        //Cumulus takes 2 ticks to become functional.
                        player.getScheduler().runDelayed(plugin,
                                ignored -> FloodgateIntegration.sendTeamForm(player), null, 2);
                    }
                }
                case BATTLE, POST_GAME -> player.sendMessage(
                        Component.text("The battle has started, new players may not join."));
            }
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
        if (gameState == GameState.PREGAME) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.teleportAsync(lobbyWorld.getSpawnLocation())
                        .thenRun(() -> player.setGameMode(GameMode.ADVENTURE));
                if (Bukkit.getPluginManager().isPluginEnabled(FLOODGATE_NAME)) {
                    FloodgateIntegration.sendTeamForm(player);
                }
            }
        }
    }
    
    /**
     * Gets the NamespacedKey of the lobby world.
     *
     * @return the NamespacedKey of the lobby world
     */
    NamespacedKey getLobbyWorldKey() {
        return lobbyWorld.getKey();
    }
    
}
