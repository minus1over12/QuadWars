package io.github.minus1over12.quadwars;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.Objects;
import java.util.UUID;

/**
 * Controls the lobby world.
 *
 * @author War Pigeon
 */
public class LobbyWorldController implements Listener {
    /**
     * The lobby world.
     */
    private final World lobbyWorld;
    /**
     * The current game state.
     */
    private GameState gameState;
    
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
        WorldBorder worldBorder = lobbyWorld.getWorldBorder();
        worldBorder.setCenter(lobbyWorld.getSpawnLocation());
        worldBorder.setSize(16);
    }
    
    /**
     * Sends a team join form to the player.
     *
     * @param player the player to send the form to
     */
    private static void sendTeamForm(Entity player) {
        //todo this is untested and unfinished.
        FloodgateApi instance = FloodgateApi.getInstance();
        UUID uniqueId = player.getUniqueId();
        if (instance.isFloodgatePlayer(uniqueId)) {
            instance.sendForm(uniqueId,
                    SimpleForm.builder().title("Select a Team").button("NE").button("SE")
                            .button("SW").button("NW").validResultHandler(response -> {
                                Bukkit.dispatchCommand(player,
                                        "jointeam " + response.clickedButton().text());
                            }).build());
        }
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
                            "The game has not started yet, but you can " + "pick a team."));
                    sendTeamForm(player);
                }
                case PREP -> {
                    player.sendMessage(Component.text(
                            "The game is in the prep phase, but you " + "can still join a team."));
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
