package io.github.minus1over12.quadwars;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Controls the teams in the game.
 *
 * @author War Pigeon
 */
public class TeamControl implements Listener {
    /**
     * The logger for the plugin.
     */
    private final Logger logger;
    /**
     * The current game state.
     */
    GameState gameState;
    
    /**
     * Creates a team control object.
     *
     * @param plugin the plugin creating the object
     */
    protected TeamControl(QuadWars plugin) {
        gameState = plugin.getGameState();
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        logger = plugin.getLogger();
        if (scoreboard.getTeams().stream()
                .anyMatch(team -> !team.getName().startsWith("quadwars_"))) {
            logger.warning("QuadWars is powered by Minecraft's built-in scoreboard system. " +
                    "Minecraft only allows a player to be on one team at a time. Your server has " +
                    "teams not managed by QuadWars. These will cause problems if anything else " +
                    "tries to assign a player to them.");
        }
        for (Quadrant quadrant : Quadrant.values()) {
            String teamName = "quadwars_" + quadrant;
            if (scoreboard.getTeam(teamName) == null) {
                logger.info("Creating Scoreboard team " + teamName);
                Team team = scoreboard.registerNewTeam(teamName);
                team.setCanSeeFriendlyInvisibles(true);
                team.setAllowFriendlyFire(false);
                //TODO: Allow setting name, prefix, and suffix from config
                team.prefix(Component.text(quadrant.toString()));
                team.displayName(Component.text(quadrant.toString()));
                team.suffix(Component.text(quadrant.toString()));
            }
        }
    }
    
    /**
     * Adds an entity to a team.
     *
     * @param entity   the entity to add
     * @param quadrant the team to add the entity to
     */
    void addEntityToTeam(Entity entity, Quadrant quadrant) {
        Objects.requireNonNull(Bukkit.getScoreboardManager().getMainScoreboard()
                .getTeam("quadwars_" + quadrant)).addEntity(entity);
        logger.info("Adding " + entity.getName() + " to team " + quadrant);
        if (gameState == GameState.PREP) {
            entity.teleportAsync(Bukkit.getWorld("world")
                    .getHighestBlockAt(quadrant.xSign * 1024, quadrant.zSign * 1024).getLocation()
                    .add(0, 1, 0));
        }
    }
    
    /**
     * Handles team events when the game state changes.
     *
     * @param event The event that triggered this method.
     */
    @EventHandler
    public static void onGameStateChange(GameStateChangeEvent event) {
        GameState state = event.getState();
        switch (state) {
            case PREGAME, BATTLE, POSTGAME -> {
            }
            case PREP -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Team team =
                            Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player);
                    if (team != null) {
                        player.teleportAsync(Bukkit.getWorld("world").getHighestBlockAt(
                                        Quadrant.valueOf(
                                                team.getName().replaceFirst("quadwars_", "")).xSign * 1024,
                                        Quadrant.valueOf(
                                                team.getName().replaceFirst("quadwars_", "")).zSign * 1024)
                                .getLocation().add(0, 1, 0));
                        player.setGameMode(GameMode.SURVIVAL);
                    } else {
                        player.teleportAsync(Bukkit.getWorld("world").getSpawnLocation());
                    }
                }
            }
        }
    }
    
}
